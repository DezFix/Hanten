package hanten.wre.app.main.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import hanten.wre.app.core.exceptions.EmptyHistoryException
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.observeAsFlow
import hanten.wre.app.core.prefs.observeAsStateFlow
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.call
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.main.domain.ReadingResumeEnabledUseCase
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.tracker.domain.TrackingRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
	private val historyRepository: HistoryRepository,
	trackingRepository: TrackingRepository,
	private val settings: AppSettings,
	readingResumeEnabledUseCase: ReadingResumeEnabledUseCase,
	private val sourcesRepository: MangaSourcesRepository,
	private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val onOpenReader = MutableEventFlow<Manga>()

	/** Easter egg: shake the phone → a random title from the library (null = the library is empty). */
	val onRandomTitle = MutableEventFlow<Manga?>()
	val onFirstStart = MutableEventFlow<Unit>()

	val isResumeEnabled = readingResumeEnabledUseCase()
		.withErrorHandling()
		.stateIn(
			scope = viewModelScope + Dispatchers.IO,
			started = SharingStarted.WhileSubscribed(5000),
			initialValue = false,
		)


	val feedCounter = trackingRepository.observeUnreadUpdatesCount()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, 0)

	val isBottomNavPinned = settings.observeAsFlow(
		AppSettings.KEY_NAV_PINNED,
	) {
		isNavBarPinned
	}.flowOn(Dispatchers.IO)

	val isIncognitoModeEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_INCOGNITO_MODE,
		valueProducer = { isIncognitoModeEnabled },
	)

	init {
		launchJob(Dispatchers.IO) {
			if (sourcesRepository.isSetupRequired()) {
				onFirstStart.call(Unit)
			}
		}
	}

	fun openLastReader() {
		launchLoadingJob(Dispatchers.IO) {
			val manga = historyRepository.getLastOrNull()
				?: throw EmptyHistoryException()
			onOpenReader.call(manga)
		}
	}

	fun openRandomFavourite() {
		launchJob(Dispatchers.IO) {
			onRandomTitle.call(favouritesRepository.getAllManga().randomOrNull())
		}
	}

	fun setIncognitoMode(isEnabled: Boolean) {
		settings.isIncognitoModeEnabled = isEnabled
	}
}
