package hanten.wre.app.favourites.domain

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import hanten.wre.app.core.os.NetworkState
import hanten.wre.app.core.model.contentType
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.parsers.model.MangaState
import hanten.wre.app.list.domain.ListFilterOption
import hanten.wre.app.list.domain.MangaListQuickFilter

class FavoritesListQuickFilter @AssistedInject constructor(
	@Assisted private val categoryId: Long,
	private val settings: AppSettings,
	private val repository: FavouritesRepository,
	networkState: NetworkState,
) : MangaListQuickFilter(settings) {

	init {
		setFilterOption(ListFilterOption.Downloaded, !networkState.value)
	}

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.READING)
		add(ListFilterOption.Macro.NOT_STARTED)
		add(ListFilterOption.Macro.COMPLETED)
		// Hanten 3.0: type and release status, only those the library actually has
		val library = (if (categoryId == 0L) repository.getAllManga() else repository.getManga(categoryId)).distinctBy { it.id }
		val types = library.groupBy { it.contentType }
		if (types.size > 1) {
			types.entries.sortedByDescending { it.value.size }.mapTo(this) { (type, list) ->
				ListFilterOption.Type(type, list.mapTo(HashSet()) { it.source.name })
			}
		}
		val states = library.mapNotNullTo(HashSet()) { it.state }
		if (states.size > 1) {
			STATE_ORDER.filter { it in states }.mapTo(this) { ListFilterOption.State(it) }
		}
		if (repository.hasTracked(categoryId)) {
			add(ListFilterOption.Macro.TRACKED)
		}
		repository.findPopularSources(categoryId, 3).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}

	private companion object {

		val STATE_ORDER = listOf(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED)
	}

	@AssistedFactory
	interface Factory {

		fun create(categoryId: Long): FavoritesListQuickFilter
	}
}
