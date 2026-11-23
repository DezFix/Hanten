package hanten.wre.app.today.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.ListMode
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.history.domain.model.MangaWithHistory
import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.list.domain.MangaListMapper
import hanten.wre.app.list.ui.model.MangaListModel
import hanten.wre.app.tracker.domain.TrackingRepository
import hanten.wre.app.tracker.work.TrackWorker
import javax.inject.Inject

/**
 * "Today" home: what to continue, what got new chapters, what was read recently.
 * Covers use the same grid cards as the library, so type labels, icons and progress look the same everywhere.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
	historyRepository: HistoryRepository,
	trackingRepository: TrackingRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
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

	/** Everything read recently except the "Continue" title: manga and ranobe together. */
	val recent = history.filterNotNull()
		.map { list -> mangaListMapper.toListModelList(list.drop(1).map { it.manga }, ListMode.GRID) }
		.stateIn<List<MangaListModel>>(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	val updates = if (settings.isTrackerEnabled) {
		trackingRepository.observeUpdatedManga(UPDATES_LIMIT, emptySet())
			.map { list ->
				// the grid card shows the new chapters count as its badge
				mangaListMapper.toListModelList(list.filter { it.newChapters > 0 }.map { it.manga }, ListMode.GRID)
			}
			.withErrorHandling()
	} else {
		flowOf(emptyList())
	}.stateIn<List<MangaListModel>>(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	private companion object {

		const val HISTORY_LIMIT = 13
		const val UPDATES_LIMIT = 20
	}
}
