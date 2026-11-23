package hanten.wre.app.today.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.history.domain.model.MangaWithHistory
import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.tracker.domain.TrackingRepository
import hanten.wre.app.tracker.work.TrackWorker
import javax.inject.Inject

/** "Today" home: what to continue, what got new chapters, what was read recently. */
@HiltViewModel
class TodayViewModel @Inject constructor(
	historyRepository: HistoryRepository,
	trackingRepository: TrackingRepository,
	settings: AppSettings,
	private val trackScheduler: TrackWorker.Scheduler,
) : BaseViewModel() {

	val isTrackerEnabled = settings.isTrackerEnabled

	val isCheckingUpdates = trackScheduler.observeIsRunning()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, false)

	fun checkNewChapters() {
		trackScheduler.startNow()
	}

	/** Null while loading; the first item is the "Continue" card. */
	val history = historyRepository.observeAllWithHistory(ListSortOrder.LAST_READ, emptySet(), HISTORY_LIMIT)
		.withErrorHandling()
		.stateIn<List<MangaWithHistory>?>(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	val updates = if (settings.isTrackerEnabled) {
		trackingRepository.observeUpdatedManga(UPDATES_LIMIT, emptySet())
			.map { list -> list.filter { it.newChapters > 0 }.map { TodayCover(it.manga, it.newChapters) } }
			.withErrorHandling()
	} else {
		flowOf(emptyList())
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	private companion object {

		const val HISTORY_LIMIT = 13
		const val UPDATES_LIMIT = 20
	}
}
