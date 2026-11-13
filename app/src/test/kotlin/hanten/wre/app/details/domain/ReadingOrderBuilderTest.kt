package hanten.wre.app.details.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import hanten.wre.app.core.db.entity.MangaRelationEntity
import hanten.wre.app.core.model.MangaRelationOrigin
import hanten.wre.app.core.model.MangaRelationType
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaParserSource

class ReadingOrderBuilderTest {

	@Test
	fun buildsPrequelCurrentSequelAndSideStory() = runTest {
		val entities = listOf(
			relation(from = 1L, to = 2L, type = MangaRelationType.PREQUEL),
			relation(from = 1L, to = 3L, type = MangaRelationType.SEQUEL),
			relation(from = 1L, to = 4L, type = MangaRelationType.SPIN_OFF),
		)
		val order = ReadingOrderBuilder.build(1L, manga(1L, "Current"), entities) { id ->
			manga(id, "Manga $id")
		}

		assertEquals(listOf(2L, 1L, 3L, 4L), order.map { it.targetId })
		assertEquals(listOf(1, 2, 3, 4), order.map { it.order })
		assertEquals(manga(1L, "Current"), order[1].manga)
		assertEquals(true, order[1].isCurrent)
		assertEquals(MangaRelationType.SPIN_OFF, order.last().type)
	}

	@Test
	fun reverseEdgeIsNormalized() = runTest {
		val entities = listOf(
			relation(from = 2L, to = 1L, type = MangaRelationType.SEQUEL),
		)
		val order = ReadingOrderBuilder.build(1L, manga(1L, "Current"), entities) { id ->
			manga(id, "Manga $id")
		}

		assertEquals(listOf(2L, 1L), order.map { it.targetId })
		assertEquals(MangaRelationType.PREQUEL, order.first().type)
	}

	@Test
	fun resolvesLinkedWorkWithNegativeId() = runTest {
		val entities = listOf(
			relation(from = 1L, to = -7L, type = MangaRelationType.SEQUEL),
		)
		val order = ReadingOrderBuilder.build(1L, manga(1L, "Current"), entities) { id ->
			if (id == -7L) manga(-7L, "Linked negative") else null
		}

		assertEquals(listOf(1L, -7L), order.map { it.targetId })
		assertEquals("Linked negative", order.last().title)
		assertEquals(manga(-7L, "Linked negative"), order.last().manga)
	}

	@Test
	fun keepsUnlinkedTitleWithSnapshot() = runTest {
		val unlinkedId = -42L
		val entities = listOf(
			relation(
				from = 1L,
				to = unlinkedId,
				type = MangaRelationType.SEQUEL,
				title = "Unlinked sequel",
			),
		)
		val order = ReadingOrderBuilder.build(1L, manga(1L, "Current"), entities) { null }

		assertEquals(listOf(1L, unlinkedId), order.map { it.targetId })
		assertEquals("Unlinked sequel", order.last().title)
		assertNull(order.last().manga)
	}

	@Test
	fun ignoresUnrelatedSeries() = runTest {
		val entities = listOf(
			relation(from = 1L, to = 2L, type = MangaRelationType.SEQUEL),
			relation(from = 10L, to = 11L, type = MangaRelationType.SEQUEL),
		)
		val order = ReadingOrderBuilder.build(1L, manga(1L, "Current"), entities) { id ->
			manga(id, "Manga $id")
		}

		assertEquals(listOf(1L, 2L), order.map { it.targetId })
	}

	private fun relation(
		from: Long,
		to: Long,
		type: MangaRelationType,
		title: String? = null,
	) = MangaRelationEntity(
		fromId = from,
		toId = to,
		type = type,
		origin = MangaRelationOrigin.MANUAL,
		createdAt = 0L,
		targetTitle = title,
	)

	private fun manga(id: Long, title: String) = Manga(
		id = id,
		title = title,
		altTitles = emptySet(),
		url = "/$id",
		publicUrl = "https://example.org/$id",
		rating = -1f,
		contentRating = null,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		source = MangaParserSource.READMANGA_RU,
	)
}
