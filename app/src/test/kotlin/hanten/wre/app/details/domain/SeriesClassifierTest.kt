package hanten.wre.app.details.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaParserSource

class SeriesClassifierTest {

	@Test
	fun detectsPrequelAndSequelByPartNumber() {
		val current = manga(2L, "Полярная ночь 2")
		val info = SeriesClassifier.classify(
			current,
			listOf(
				candidate(manga(1L, "Полярная ночь")),
				candidate(manga(3L, "Полярная ночь 3")),
			),
		)

		assertEquals(2, info.currentPart)
		assertEquals(listOf(1L), info.prequels.map { it.manga.id })
		assertEquals(listOf(3L), info.continuations.map { it.manga.id })
	}

	@Test
	fun detectsPartWordAndRomanNumeral() {
		assertEquals(2, SeriesTitle.part("Полярная ночь. Часть 2"))
		assertEquals(3, SeriesTitle.part("Полярная ночь III"))
		assertNull(SeriesTitle.part("Полярная ночь"))
	}

	@Test
	fun authorWorksAreSeparateFromContinuations() {
		val current = manga(10L, "Улей", authors = setOf("Иван Петров"))
		val info = SeriesClassifier.classify(
			current,
			listOf(
				candidate(manga(11L, "Собачник", authors = setOf("Иван Петров"))),
				candidate(manga(12L, "Мёртвая королева", authors = setOf("Иван Петров"))),
				candidate(manga(13L, "Чужое произведение", authors = setOf("Другой Автор")), fromSource = true),
			),
		)

		assertEquals(listOf(12L, 11L), info.sameAuthor.map { it.manga.id })
		assertEquals(listOf(13L), info.related.map { it.manga.id })
		assertTrue(info.prequels.isEmpty())
		assertTrue(info.continuations.isEmpty())
	}

	@Test
	fun samePartIsNotDuplicated() {
		val info = SeriesClassifier.classify(
			manga(20L, "Полярная ночь 2"),
			listOf(candidate(manga(21L, "Полярная ночь 2"))),
		)

		assertTrue(info.isEmpty)
	}

	@Test
	fun unrelatedTitlesAreIgnored() {
		val info = SeriesClassifier.classify(
			manga(30L, "Полярная ночь 2"),
			listOf(candidate(manga(31L, "Совсем другое название"))),
		)

		assertTrue(info.isEmpty)
	}

	private fun candidate(manga: Manga, fromSource: Boolean = false) =
		SeriesClassifier.Candidate(manga, fromSource)

	private fun manga(
		id: Long,
		title: String,
		authors: Set<String> = emptySet(),
	) = Manga(
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
		authors = authors,
		source = MangaParserSource.READMANGA_RU,
	)
}
