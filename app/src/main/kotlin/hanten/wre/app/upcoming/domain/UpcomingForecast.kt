package hanten.wre.app.upcoming.domain

import kotlin.math.floor

/**
 * «Скоро выйдет»: sites do not say when the next chapter comes out, so it is estimated from the dates of the
 * previous ones (the same idea Mihon uses). Approximate on purpose: a team can pause or post in batches.
 */
object UpcomingForecast {

	const val DAY_MS = 86_400_000L

	/** Intervals taken into account (the last ones, so a schedule change is picked up quickly) */
	private const val MAX_INTERVALS = 10

	/** Fewer intervals than this is a guess, not a schedule */
	private const val MIN_INTERVALS = 3

	/** Titles updating less often than this are too irregular to show */
	private const val MAX_INTERVAL_DAYS = 60

	class Chapter(val uploadDate: Long, val number: Float, val branch: String?)

	class Result(
		/** Expected date of the next chapter */
		val nextDate: Long,
		/** Typical gap between chapters, days */
		val intervalDays: Int,
		/** Number of the next chapter if chapters are numbered */
		val nextNumber: Int?,
		/** The expected date has passed by more than one gap */
		val isLate: Boolean,
	)

	fun forecast(chapters: Collection<Chapter>, now: Long): Result? {
		if (chapters.isEmpty()) return null
		// several translations: follow the one with most chapters, others would mix two schedules
		val branch = chapters.groupBy { it.branch }.maxBy { it.value.size }.value
		// chapters posted the same day count once
		val days = branch.map { it.uploadDate / DAY_MS }.distinct().sorted().takeLast(MAX_INTERVALS + 1)
		if (days.size < MIN_INTERVALS + 1) return null
		val gaps = days.zipWithNext { a, b -> (b - a).toInt() }.sorted()
		val interval = gaps[gaps.size / 2].coerceAtLeast(1)
		if (interval > MAX_INTERVAL_DAYS) return null
		val last = branch.maxOf { it.uploadDate }
		val next = last + interval * DAY_MS
		// silent for much longer than usual: on hiatus or dropped, a date would only mislead
		val giveUpAfter = maxOf(interval * 4L, 21L) * DAY_MS
		if (now > last + giveUpAfter) return null
		val maxNumber = branch.maxOf { it.number }
		return Result(
			nextDate = next,
			intervalDays = interval,
			nextNumber = if (maxNumber > 0f) floor(maxNumber).toInt() + 1 else null,
			isLate = now > next + maxOf(interval, 2) * DAY_MS,
		)
	}
}
