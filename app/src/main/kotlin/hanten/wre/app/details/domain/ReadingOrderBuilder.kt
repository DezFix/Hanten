package hanten.wre.app.details.domain

import hanten.wre.app.core.db.entity.MangaRelationEntity
import hanten.wre.app.core.model.MangaRelationType
import hanten.wre.app.parsers.model.Manga

/**
 * Builds a linear reading order for one series from the stored relation graph.
 *
 * Only PREQUEL/SEQUEL edges define the main line; spin-offs, alternatives and other
 * connections are appended after it. The graph is walked from the currently opened work,
 * so the result is stable for every part of the same series.
 */
object ReadingOrderBuilder {

	private data class Edge(
		val toId: Long,
		val type: MangaRelationType,
		val createdAt: Long,
	)

	private data class Snapshot(
		val title: String?,
		val source: String?,
		val url: String?,
	)

	suspend fun build(
		rootId: Long,
		current: Manga?,
		relations: List<MangaRelationEntity>,
		resolveManga: suspend (Long) -> Manga?,
	): List<ReadingOrderItem> {
		if (relations.isEmpty()) {
			return emptyList()
		}

		val edges = HashMap<Long, MutableList<Edge>>(relations.size * 2)
		val snapshots = HashMap<Long, Snapshot>(relations.size)
		val unlinkedIds = HashSet<Long>(relations.size)
		for (entity in relations) {
			if (entity.isUnlinked) {
				unlinkedIds += entity.toId
			}
			edges.getOrPut(entity.fromId) { ArrayList(2) }
				.add(Edge(entity.toId, entity.type, entity.createdAt))
			if (!entity.isUnlinked && entity.toId != 0L) {
				edges.getOrPut(entity.toId) { ArrayList(2) }
					.add(Edge(entity.fromId, entity.type.reverse, entity.createdAt))
			}
			snapshots[entity.toId] = Snapshot(
				title = entity.targetTitle,
				source = entity.targetSource,
				url = entity.targetUrl,
			)
		}

		val component = collectComponent(rootId, edges)
		if (component.size <= 1) {
			return emptyList()
		}

		val visited = HashSet<Long>(component.size)
		val order = ArrayList<Long>(component.size)
		val types = HashMap<Long, MangaRelationType?>(component.size)

		fun collectBack(id: Long, type: MangaRelationType?) {
			if (!visited.add(id)) return
			val prequels = edges[id].orEmpty()
				.filter { it.type == MangaRelationType.PREQUEL }
				.sortedWith(compareBy({ it.createdAt }, { it.toId }))
			for (edge in prequels) {
				collectBack(edge.toId, MangaRelationType.PREQUEL)
			}
			order += id
			types[id] = type
		}

		fun collectForward(id: Long) {
			val sequels = edges[id].orEmpty()
				.filter { it.type == MangaRelationType.SEQUEL }
				.sortedWith(compareBy({ it.createdAt }, { it.toId }))
			for (edge in sequels) {
				if (!visited.add(edge.toId)) continue
				order += edge.toId
				types[edge.toId] = MangaRelationType.SEQUEL
				collectForward(edge.toId)
			}
		}

		collectBack(rootId, null)
		collectForward(rootId)

		// Side stories of the current work go after the main line.
		edges[rootId].orEmpty()
			.filter { it.type != MangaRelationType.PREQUEL && it.type != MangaRelationType.SEQUEL }
			.sortedWith(
				compareBy(
					{ sidePriority(it.type) },
					{ it.createdAt },
					{ it.toId },
				),
			)
			.forEach { edge ->
				if (edge.toId in visited) return@forEach
				collectBack(edge.toId, edge.type)
				collectForward(edge.toId)
			}

		// Remaining nodes of the same connected component (e.g. a side story of a sequel).
		component.asSequence()
			.filter { it !in visited }
			.sortedWith(
				compareBy(
					{ sidePriority(typeFor(it, edges)) },
					{ snapshots[it]?.title.orEmpty() },
					{ it },
				),
			)
			.forEach { id ->
				if (id in visited) return@forEach
				val type = typeFor(id, edges)
				collectBack(id, type)
				collectForward(id)
			}

		val result = ArrayList<ReadingOrderItem>(order.size)
		for ((index, id) in order.withIndex()) {
			val isCurrent = id == rootId
			val manga = when {
				isCurrent -> current
				id in unlinkedIds -> null
				id != 0L -> resolveManga(id)
				else -> null
			}
			val snapshot = snapshots[id]
			val title = manga?.title ?: snapshot?.title.orEmpty()
			val type = if (isCurrent) null else types[id]
			result += ReadingOrderItem(
				order = index + 1,
				targetId = id,
				manga = manga,
				title = title,
				sourceName = manga?.source?.name ?: snapshot?.source,
				publicUrl = manga?.publicUrl ?: snapshot?.url,
				type = type,
				isCurrent = isCurrent,
				isSideStory = type != null &&
					type != MangaRelationType.PREQUEL &&
					type != MangaRelationType.SEQUEL,
			)
		}
		return result
	}

	private fun collectComponent(
		rootId: Long,
		edges: Map<Long, List<Edge>>,
	): Set<Long> {
		val result = HashSet<Long>()
		val queue = ArrayDeque<Long>()
		queue += rootId
		while (queue.isNotEmpty()) {
			val id = queue.removeFirst()
			if (!result.add(id)) continue
			edges[id].orEmpty().forEach { queue += it.toId }
		}
		return result
	}

	private fun typeFor(id: Long, edges: Map<Long, List<Edge>>): MangaRelationType {
		return edges.values.asSequence()
			.flatten()
			.firstOrNull { it.toId == id }
			?.type
			?: MangaRelationType.OTHER
	}

	private fun sidePriority(type: MangaRelationType): Int = when (type) {
		MangaRelationType.PREQUEL,
		MangaRelationType.SEQUEL,
		-> 0

		MangaRelationType.SPIN_OFF -> 10
		MangaRelationType.ALTERNATIVE -> 20
		MangaRelationType.SAME_UNIVERSE -> 30
		MangaRelationType.ADAPTATION -> 40
		MangaRelationType.OTHER -> 50
	}
}
