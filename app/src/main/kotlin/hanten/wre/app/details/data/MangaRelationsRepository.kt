package hanten.wre.app.details.data

import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.db.entity.MangaRelationEntity
import hanten.wre.app.core.model.MangaRelationOrigin
import hanten.wre.app.core.model.MangaRelationType
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.parsers.model.Manga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RelatedEntry(
	val targetId: Long,
	val manga: Manga?,
	val title: String,
	val sourceName: String?,
	val publicUrl: String?,
	val type: MangaRelationType,
	val origin: MangaRelationOrigin,
)

@Singleton
class MangaRelationsRepository @Inject constructor(
	db: MangaDatabase,
	private val mangaDataRepository: MangaDataRepository,
) {

	private val dao = db.getMangaRelationsDao()

	/** All stored relations. Used to build the reading order for the whole series. */
	fun observeAll(): Flow<List<MangaRelationEntity>> = dao.observeAll()

	fun observeFor(mangaId: Long): Flow<List<RelatedEntry>> = dao.observeFor(mangaId).map { relations ->
		relations.mapNotNull { entity -> entity.toEntry(mangaId) }
	}

	suspend fun resolveManga(mangaId: Long): Manga? =
		mangaDataRepository.findMangaById(mangaId, withChapters = false)

	suspend fun add(
		fromId: Long,
		toId: Long,
		type: MangaRelationType,
		title: String? = null,
		sourceName: String? = null,
		publicUrl: String? = null,
	) {
		if (fromId == toId) return
		dao.insert(
			MangaRelationEntity(
				fromId = fromId,
				toId = toId,
				type = type,
				origin = MangaRelationOrigin.MANUAL,
				createdAt = System.currentTimeMillis(),
				targetTitle = title,
				targetSource = sourceName,
				targetUrl = publicUrl,
			),
		)
	}

	suspend fun remove(fromId: Long, toId: Long, type: MangaRelationType) = dao.delete(fromId, toId, type)

	suspend fun removeBetween(a: Long, b: Long) {
		dao.findFor(a).forEach { entity ->
			val matches = (entity.fromId == a && entity.toId == b) || (entity.fromId == b && entity.toId == a)
			if (matches) {
				dao.delete(entity.fromId, entity.toId, entity.type)
			}
		}
	}

	suspend fun dump(): List<MangaRelationEntity> = dao.findAll()

	suspend fun restore(entries: List<MangaRelationEntity>, isMerge: Boolean) {
		if (!isMerge) {
			dao.clear()
		}
		entries.forEach { dao.insert(it) }
	}

	private suspend fun MangaRelationEntity.toEntry(mangaId: Long): RelatedEntry? {
		val otherId = if (fromId == mangaId) toId else fromId
		val manga = if (!isUnlinked && otherId != 0L) resolveManga(otherId) else null
		val title = manga?.title ?: targetTitle.orEmpty()
		if (manga == null && title.isBlank()) {
			return null
		}
		return RelatedEntry(
			targetId = otherId,
			manga = manga,
			title = title,
			sourceName = manga?.source?.name ?: targetSource,
			publicUrl = manga?.publicUrl ?: targetUrl,
			type = if (fromId == mangaId) type else type.reverse,
			origin = origin,
		)
	}

	companion object {

		/**
		 * Stable synthetic id for a title that is not in the library yet. Negative ids never
		 * collide with the auto-increment ids used by the manga table in practice.
		 */
		fun unlinkedId(title: String): Long {
			val hash = title.trim().lowercase().hashCode().toLong()
			return if (hash < 0L) hash else -hash - 1L
		}
	}
}
