package hanten.wre.app.upcoming

import hanten.wre.app.upcoming.domain.UpcomingForecast
import hanten.wre.app.upcoming.domain.UpcomingForecast.Chapter
import hanten.wre.app.upcoming.domain.UpcomingForecast.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingForecastTest {

	private val start = 20_000L * DAY_MS

	private fun weekly(count: Int, branch: String? = null) = (0 until count).map {
		Chapter(start + it * 7 * DAY_MS, (it + 1).toFloat(), branch)
	}

	@Test
	fun weeklyTitleGetsNextWeek() {
		val chapters = weekly(6)
		val last = chapters.last().uploadDate
		val r = requireNotNull(UpcomingForecast.forecast(chapters, now = last + DAY_MS))
		assertEquals(7, r.intervalDays)
		assertEquals(last + 7 * DAY_MS, r.nextDate)
		assertEquals(7, r.nextNumber)
		assertFalse(r.isLate)
	}

	@Test
	fun tooFewChaptersIsNoForecast() {
		val chapters = weekly(3)
		assertNull(UpcomingForecast.forecast(chapters, now = chapters.last().uploadDate))
	}

	@Test
	fun lateWhenMissedByAWholeGap() {
		val chapters = weekly(6)
		val last = chapters.last().uploadDate
		val r = requireNotNull(UpcomingForecast.forecast(chapters, now = last + 16 * DAY_MS))
		assertTrue(r.isLate)
	}

	@Test
	fun longSilenceDropsTheTitle() {
		val chapters = weekly(6)
		assertNull(UpcomingForecast.forecast(chapters, now = chapters.last().uploadDate + 40 * DAY_MS))
	}

	@Test
	fun batchesOnOneDayCountOnce() {
		// 3 chapters every 10 days
		val chapters = (0 until 5).flatMap { week ->
			(0 until 3).map { Chapter(start + week * 10 * DAY_MS + it * 1000L, (week * 3 + it + 1).toFloat(), null) }
		}
		val r = requireNotNull(UpcomingForecast.forecast(chapters, now = chapters.last().uploadDate))
		assertEquals(10, r.intervalDays)
	}

	@Test
	fun followsTheBiggestTranslation() {
		val main = weekly(6, "A")
		val other = listOf(Chapter(start + 1 * DAY_MS, 1f, "B"), Chapter(start + 2 * DAY_MS, 2f, "B"))
		val r = requireNotNull(UpcomingForecast.forecast(main + other, now = main.last().uploadDate))
		assertEquals(7, r.intervalDays)
	}
}
