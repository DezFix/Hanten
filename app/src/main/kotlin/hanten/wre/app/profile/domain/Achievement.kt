package hanten.wre.app.profile.domain

import androidx.annotation.StringRes
import hanten.wre.app.R

/** Profile achievements: counted from [ReadingProgressStats], nothing to store or back up. */
enum class Achievement(
	val icon: String,
	@StringRes val titleResId: Int,
	@StringRes val descriptionResId: Int,
	val target: Long,
	private val value: (ReadingProgressStats) -> Long,
) {

	FIRST_CHAPTER("📖", R.string.ach_first_chapter, R.string.ach_first_chapter_desc, 1, { it.chapters.toLong() }),
	CHAPTERS_100("📚", R.string.ach_chapters_100, R.string.ach_chapters_100_desc, 100, { it.chapters.toLong() }),
	CHAPTERS_1000("🏯", R.string.ach_chapters_1000, R.string.ach_chapters_1000_desc, 1000, { it.chapters.toLong() }),
	CHAPTERS_5000("🐉", R.string.ach_chapters_5000, R.string.ach_chapters_5000_desc, 5000, { it.chapters.toLong() }),
	FINISHED_1("🏁", R.string.ach_finished_1, R.string.ach_finished_1_desc, 1, { it.finishedTitles.toLong() }),
	FINISHED_10("🎖️", R.string.ach_finished_10, R.string.ach_finished_10_desc, 10, { it.finishedTitles.toLong() }),
	FINISHED_50("🏆", R.string.ach_finished_50, R.string.ach_finished_50_desc, 50, { it.finishedTitles.toLong() }),
	HOURS_10("⏳", R.string.ach_hours_10, R.string.ach_hours_10_desc, 10, { it.readingMinutes / 60 }),
	HOURS_100("🕰️", R.string.ach_hours_100, R.string.ach_hours_100_desc, 100, { it.readingMinutes / 60 }),
	NIGHT_OWL("🦉", R.string.ach_night_owl, R.string.ach_night_owl_desc, 1, { it.nightSessions.toLong() }),
	MARATHON("🏃", R.string.ach_marathon, R.string.ach_marathon_desc, 180, { it.bestDayMinutes }),
	STREAK_7("🔥", R.string.ach_streak_7, R.string.ach_streak_7_desc, 7, { it.bestStreakDays.toLong() }),
	COLLECTOR("🗃️", R.string.ach_collector, R.string.ach_collector_desc, 100, { it.libraryTitles.toLong() }),
	EXPLORER("🧭", R.string.ach_explorer, R.string.ach_explorer_desc, 5, { it.sourcesRead.toLong() }),
	;

	fun current(stats: ReadingProgressStats): Long = value(stats).coerceAtMost(target)

	fun isUnlocked(stats: ReadingProgressStats): Boolean = value(stats) >= target
}
