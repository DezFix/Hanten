package hanten.wre.app.upcoming.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.tracker.work.TrackWorker
import hanten.wre.app.upcoming.domain.GetUpcomingUseCase
import hanten.wre.app.upcoming.domain.UpcomingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import javax.inject.Inject

@HiltViewModel
class UpcomingViewModel @Inject constructor(
	private val getUpcoming: GetUpcomingUseCase,
	private val trackScheduler: TrackWorker.Scheduler,
) : BaseViewModel() {

	/** null until the first load */
	val items = MutableStateFlow<List<UpcomingItem>?>(null)

	val isChecking = trackScheduler.observeIsRunning()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

	init {
		reload()
		// a finished check brings fresh chapter dates
		launchJob(Dispatchers.Default) {
			isChecking.drop(1).filter { !it }.collect { reload() }
		}
	}

	fun reload() {
		launchLoadingJob(Dispatchers.Default) {
			items.value = getUpcoming()
		}
	}

	fun checkNow() {
		trackScheduler.startNow()
	}
}
