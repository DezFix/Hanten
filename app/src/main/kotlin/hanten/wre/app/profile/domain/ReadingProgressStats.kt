package hanten.wre.app.profile.domain

/**
 * Hanten 3.0 profile levels: everything is counted from the local database (history + reading stats),
 * so titles read before the update count too and nothing is stored separately.
 */
data class ReadingProgressStats(
	/** Chapters read in all titles: percent × chapters of each history entry. */
	val chapters: Int,
	/** Titles read to the end (≥ 99 %). */
	val finishedTitles: Int,
	val readingMinutes: Long,
	/** Reading sessions started between 0:00 and 4:59. */
	val nightSessions: Int,
	/** Longest reading time in one day, minutes. */
	val bestDayMinutes: Long,
	/** Longest run of days in a row with reading. */
	val bestStreakDays: Int,
	val libraryTitles: Int,
	val sourcesRead: Int,
) {

	/** 10 XP per chapter, 50 per finished title, 100 per hour of reading. */
	val xp: Long
		get() = chapters * XP_CHAPTER + finishedTitles * XP_FINISHED + readingMinutes * XP_HOUR / 60

	companion object {

		const val XP_CHAPTER = 10L
		const val XP_FINISHED = 50L
		const val XP_HOUR = 100L

		val EMPTY = ReadingProgressStats(0, 0, 0, 0, 0, 0, 0, 0)
	}
}
