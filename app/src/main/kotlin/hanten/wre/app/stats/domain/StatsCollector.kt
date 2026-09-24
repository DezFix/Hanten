package hanten.wre.app.stats.domain

import androidx.collection.LongSparseArray
import androidx.collection.set
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.RetainedLifecycleCoroutineScope
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.parsers.util.runCatchingCancellable
import hanten.wre.app.reader.ui.ReaderState
import hanten.wre.app.stats.data.StatsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject

@ViewModelScoped
class StatsCollector @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	lifecycle: ViewModelLifecycle,
) {

	private val viewModelScope = RetainedLifecycleCoroutineScope(lifecycle)
	private val stats = LongSparseArray<Entry>(1)

	/**
	 * Upserts carry absolute totals, so a single consumer is enough: independent coroutines could
	 * land out of order and write an older, smaller total over a newer one.
	 */
	private val pendingWrites = Channel<StatsEntity>(Channel.CONFLATED)

	init {
		viewModelScope.launch(Dispatchers.IO) {
			for (entity in pendingWrites) {
				runCatchingCancellable {
					db.getStatsDao().upsert(entity)
				}.onFailure { e ->
					e.printStackTraceDebug("StatsCollector::commit")
				}
			}
		}
	}

	@Synchronized
	fun onStateChanged(mangaId: Long, state: ReaderState) {
		if (!settings.isStatsEnabled) {
			return
		}
		val now = System.currentTimeMillis()
		val entry = stats[mangaId]
		if (entry == null) {
			stats[mangaId] = Entry(
				state = state,
				stats = StatsEntity(
					mangaId = mangaId,
					startedAt = now,
					duration = 0,
					pages = 0,
				),
				lastUpdate = now,
			)
			return
		}
		// Count only active reading: idle gaps (phone put aside with screen on)
		// must not inflate the per-page average forever
		val gap = (now - entry.lastUpdate).coerceAtLeast(0)
		val counted = gap.coerceAtMost(MAX_PAGE_DURATION_MS)
		val pagesDelta = if (entry.state.page != state.page || entry.state.chapterId != state.chapterId) 1 else 0
		val newEntry = entry.copy(
			state = state,
			stats = entry.stats.copy(
				duration = entry.stats.duration + counted,
				pages = entry.stats.pages + pagesDelta,
			),
			lastUpdate = now,
		)
		stats[mangaId] = newEntry
		commit(newEntry.stats)
	}

	@Synchronized
	fun onPause(mangaId: Long) {
		val entry = stats[mangaId] ?: return
		stats.remove(mangaId)
		// Flush what was accumulated since the last state change, otherwise the time spent on the
		// final page (and any session that never changed page) never reaches the database
		if (entry.stats.duration > 0L || entry.stats.pages > 0) {
			pendingWrites.trySend(entry.stats)
		}
	}

	private fun commit(entity: StatsEntity) {
		pendingWrites.trySend(entity)
	}

	private data class Entry(
		val state: ReaderState,
		val stats: StatsEntity,
		val lastUpdate: Long,
	)

	private companion object {

		// Longer than this on a single page counts as idle, not reading
		const val MAX_PAGE_DURATION_MS = 120_000L
	}
}
