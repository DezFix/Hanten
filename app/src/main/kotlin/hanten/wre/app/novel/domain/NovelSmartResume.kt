package hanten.wre.app.novel.domain

import hanten.wre.app.core.prefs.ChapterCompletionMode

/**
 * Pure start-chapter decision for novel smart resume.
 *
 * Novels have no pages, so [ChapterCompletionMode.PAGES_REMAINING] is only approximated from the
 * saved scroll fraction: the chapter is treated as 100 pseudo pages, so one pseudo page equals 1%
 * of the chapter. It is complete when the remaining fraction is at most `pagesRemaining / 100f`.
 */
internal class NovelSmartResume(
	private val enabled: Boolean,
	private val mode: ChapterCompletionMode,
	private val percentage: Int,
	private val pagesRemaining: Int,
) {
	fun isChapterCompleted(percent: Float): Boolean {
		if (!enabled) return false
		val read = percent.coerceIn(0f, 1f)
		return when (mode) {
			ChapterCompletionMode.PERCENTAGE -> read * 100f + FLOAT_EPSILON >= percentage
			ChapterCompletionMode.PAGES_REMAINING -> 1f - read <= pagesRemaining / 100f + FLOAT_EPSILON
		}
	}

	/**
	 * Chapter index to open: at most one chapter is skipped, and the last chapter is always kept.
	 * An unknown saved index falls back to the first chapter.
	 */
	fun resolveStartIndex(savedIndex: Int, chapterCount: Int, percent: Float): Int {
		if (chapterCount <= 0) return 0
		val saved = savedIndex.coerceIn(0, chapterCount - 1)
		val hasNext = saved < chapterCount - 1
		return if (hasNext && isChapterCompleted(percent)) saved + 1 else saved
	}

	private companion object {
		// Absorbs float rounding: 0.9f * 100f is 89.999997...
		private const val FLOAT_EPSILON = 1e-4f
	}
}
