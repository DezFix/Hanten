package hanten.wre.app.tracker.domain

import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.list.domain.ListFilterOption
import hanten.wre.app.list.domain.MangaListQuickFilter
import javax.inject.Inject

class UpdatesListQuickFilter @Inject constructor(
	private val favouritesRepository: FavouritesRepository,
	settings: AppSettings,
) : MangaListQuickFilter(settings) {

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> =
		favouritesRepository.getMostUpdatedCategories(
			limit = 4,
		).map {
			ListFilterOption.Favorite(it)
		}
}
