package hanten.wre.app.stats.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import hanten.wre.app.R
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.ui.util.ReversibleAction
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.call
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.stats.data.StatsRepository
import hanten.wre.app.stats.domain.StatsPeriod
import hanten.wre.app.stats.domain.StatsRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
	private val repository: StatsRepository,
	favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val period = MutableStateFlow(StatsPeriod.WEEK)
	val byGenre = MutableStateFlow(false)
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val selectedCategories = MutableStateFlow<Set<Long>>(emptySet())
	val favoriteCategories = favouritesRepository.observeCategories()
		.take(1)

	val readingStats = MutableStateFlow<List<StatsRecord>>(emptyList())

	init {
		launchJob(Dispatchers.IO) {
			combine(
				period,
				selectedCategories,
				byGenre,
				::Triple,
			).collectLatest { (p, categories, genre) ->
				readingStats.value = withLoading {
					repository.getReadingStats(p, categories, genre)
				}
			}
		}
	}

	fun setCategoryChecked(category: FavouriteCategory, checked: Boolean) {
		val snapshot = selectedCategories.value.toMutableSet()
		if (checked) {
			snapshot.add(category.id)
		} else {
			snapshot.remove(category.id)
		}
		selectedCategories.value = snapshot
	}

	fun clearStats() {
		launchLoadingJob(Dispatchers.IO) {
			repository.clearStats()
			readingStats.value = emptyList()
			onActionDone.call(ReversibleAction(R.string.stats_cleared, null))
		}
	}
}
