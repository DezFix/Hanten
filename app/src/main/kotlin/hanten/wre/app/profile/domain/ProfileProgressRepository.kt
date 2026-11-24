package hanten.wre.app.profile.domain

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hanten.wre.app.core.db.MangaDatabase
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only aggregates for profile levels and achievements (plain SQL over existing tables, no schema change).
 */
@Singleton
class ProfileProgressRepository @Inject constructor(
	private val database: MangaDatabase,
) {

	suspend fun load(): ReadingProgressStats = withContext(Dispatchers.IO) {
		ReadingProgressStats(
			chapters = long(
				"SELECT IFNULL(SUM(CAST(ROUND(percent * chapters) AS INTEGER)), 0) FROM history " +
					"WHERE deleted_at = 0 AND chapters > 0 AND percent > 0",
			).toInt(),
			finishedTitles = long(
				"SELECT COUNT(*) FROM history WHERE deleted_at = 0 AND chapters > 0 AND percent >= 0.99",
			).toInt(),
			readingMinutes = TimeUnit.MILLISECONDS.toMinutes(long("SELECT IFNULL(SUM(duration), 0) FROM stats")),
			nightSessions = long(
				"SELECT COUNT(*) FROM stats WHERE " +
					"CAST(strftime('%H', started_at / 1000, 'unixepoch', 'localtime') AS INTEGER) < 5",
			).toInt(),
			bestDayMinutes = TimeUnit.MILLISECONDS.toMinutes(
				long(
					"SELECT IFNULL(MAX(total), 0) FROM (SELECT SUM(duration) AS total FROM stats " +
						"GROUP BY date(started_at / 1000, 'unixepoch', 'localtime'))",
				),
			),
			bestStreakDays = bestStreak(
				strings("SELECT DISTINCT date(started_at / 1000, 'unixepoch', 'localtime') AS d FROM stats ORDER BY d"),
			),
			libraryTitles = long("SELECT COUNT(DISTINCT manga_id) FROM favourites WHERE deleted_at = 0").toInt(),
			sourcesRead = long(
				"SELECT COUNT(DISTINCT manga.source) FROM history JOIN manga ON manga.manga_id = history.manga_id " +
					"WHERE history.deleted_at = 0",
			).toInt(),
		)
	}

	private fun long(sql: String): Long = runCatching {
		database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql)).use { cursor ->
			if (cursor.moveToFirst()) cursor.getLong(0) else 0L
		}
	}.getOrDefault(0L)

	private fun strings(sql: String): List<String> = runCatching {
		database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql)).use { cursor ->
			buildList(cursor.count) {
				while (cursor.moveToNext()) {
					cursor.getString(0)?.let(::add)
				}
			}
		}
	}.getOrDefault(emptyList())

	private fun bestStreak(days: List<String>): Int {
		var best = 0
		var current = 0
		var previous: LocalDate? = null
		for (raw in days) {
			val day = runCatching { LocalDate.parse(raw) }.getOrNull() ?: continue
			current = if (previous != null && previous.plusDays(1) == day) current + 1 else 1
			best = maxOf(best, current)
			previous = day
		}
		return best
	}
}
