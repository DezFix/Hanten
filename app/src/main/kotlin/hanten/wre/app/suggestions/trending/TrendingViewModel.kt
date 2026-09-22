package hanten.wre.app.suggestions.trending

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import hanten.wre.app.R
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.onFirst
import hanten.wre.app.list.domain.MangaListMapper
import hanten.wre.app.list.ui.MangaListViewModel
import hanten.wre.app.list.ui.model.EmptyState
import hanten.wre.app.list.ui.model.LoadingState
import hanten.wre.app.list.ui.model.toErrorState
import hanten.wre.app.local.data.LocalStorageChanges
import hanten.wre.app.local.domain.model.LocalManga
import hanten.wre.app.parsers.model.Manga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class TrendingViewModel @Inject constructor(
	private val repository: TrendingRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges) {

	private val reloadSignal = MutableStateFlow(0)
	private var forceNext = false

	override val content = combine(
		reloadSignal.flatMapLatest { loadTrending() },
		observeListModeWithTriggers(),
	) { list, mode ->
		if (list.isEmpty()) {
			listOf(
				EmptyState(
					icon = R.drawable.ic_empty_common,
					textPrimary = R.string.nothing_found,
					textSecondary = R.string.suggestions_trending_empty,
					actionStringRes = 0,
				),
			)
		} else {
			mangaListMapper.toListModelList(list, mode)
		}
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf(it.toErrorState(canRetry = true)))
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	override fun onRefresh() {
		forceNext = true
		reloadSignal.update { it + 1 }
	}

	override fun onRetry() {
		reloadSignal.update { it + 1 }
	}

	private fun loadTrending(): Flow<List<Manga>> = flow {
		val force = forceNext
		forceNext = false
		emit(
			repository.getTrending(force)
				.skipNsfwIfNeeded()
				.filterBlacklistedTags(),
		)
	}
}
