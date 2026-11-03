package hanten.wre.app.novel.domain

import hanten.wre.app.core.prefs.ChapterCompletionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelSmartResumeTest {

	@Test
	fun percentageThresholdReachedOpensNextChapter() {
		val resume = resume(mode = ChapterCompletionMode.PERCENTAGE, percentage = 90)
		assertTrue(resume.isChapterCompleted(0.95f))
		assertEquals(3, resume.resolveStartIndex(savedIndex = 2, chapterCount = 5, percent = 0.95f))
	}

	@Test
	fun percentageAtThresholdOpensNextChapter() {
		val resume = resume(mode = ChapterCompletionMode.PERCENTAGE, percentage = 90)
		assertEquals(1, resume.resolveStartIndex(savedIndex = 0, chapterCount = 2, percent = 0.9f))
	}

	@Test
	fun percentageBelowThresholdStaysOnSavedChapter() {
		val resume = resume(mode = ChapterCompletionMode.PERCENTAGE, percentage = 90)
		assertFalse(resume.isChapterCompleted(0.5f))
		assertEquals(2, resume.resolveStartIndex(savedIndex = 2, chapterCount = 5, percent = 0.5f))
	}

	@Test
	fun lastChapterIsNeverSkipped() {
		val resume = resume(mode = ChapterCompletionMode.PERCENTAGE, percentage = 90)
		assertEquals(4, resume.resolveStartIndex(savedIndex = 4, chapterCount = 5, percent = 1f))
	}

	@Test
	fun singleChapterIsNeverSkipped() {
		val resume = resume(mode = ChapterCompletionMode.PERCENTAGE, percentage = 1)
		assertEquals(0, resume.resolveStartIndex(savedIndex = 0, chapterCount = 1, percent = 1f))
	}

	@Test
	fun disabledResumeStaysOnSavedChapter() {
		val resume = resume(mode = ChapterCompletionMode.PERCENTAGE, percentage = 90, enabled = false)
		assertFalse(resume.isChapterCompleted(1f))
		assertEquals(1, resume.resolveStartIndex(savedIndex = 1, chapterCount = 4, percent = 1f))
	}

	@Test
	fun pagesRemainingApproximatesPagesFromPercent() {
		val resume = resume(mode = ChapterCompletionMode.PAGES_REMAINING, pagesRemaining = 1)
		// 0.5% left <= 1 pseudo page left
		assertTrue(resume.isChapterCompleted(0.995f))
		assertEquals(1, resume.resolveStartIndex(savedIndex = 0, chapterCount = 3, percent = 0.995f))
		// 2% left > 1 pseudo page left
		assertFalse(resume.isChapterCompleted(0.98f))
		assertEquals(0, resume.resolveStartIndex(savedIndex = 0, chapterCount = 3, percent = 0.98f))
	}

	@Test
	fun pagesRemainingZeroRequiresFullChapter() {
		val resume = resume(mode = ChapterCompletionMode.PAGES_REMAINING, pagesRemaining = 0)
		assertTrue(resume.isChapterCompleted(1f))
		assertFalse(resume.isChapterCompleted(0.99f))
	}

	private fun resume(
		mode: ChapterCompletionMode,
		percentage: Int = 90,
		pagesRemaining: Int = 1,
		enabled: Boolean = true,
	) = NovelSmartResume(
		enabled = enabled,
		mode = mode,
		percentage = percentage,
		pagesRemaining = pagesRemaining,
	)
}
