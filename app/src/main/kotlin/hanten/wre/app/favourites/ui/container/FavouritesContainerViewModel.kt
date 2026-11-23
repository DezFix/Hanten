package hanten.wre.app.favourites.ui.container

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.ListMode
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.favourites.ui.categories.adapter.CategoryListModel
import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.list.ui.model.ListModel
import javax.inject.Inject

/**
 * Library home: user folders (favourite categories) shown as cards, or one flat list of all titles.
 * Folders are the existing categories, so backups from Kotatsu/Futon keep working unchanged.
 */
@HiltViewModel
class FavouritesContainerViewModel @Inject constructor(
	private val settings: AppSettings,
	private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val isFoldersMode = MutableStateFlow(settings.isLibraryFoldersMode)

	var listMode: ListMode
		get() = settings.favoritesListMode
		set(value) {
			settings.favoritesListMode = value
		}

	val folders = favouritesRepository.observeCategoriesWithCovers()
		.map { map ->
			buildList<ListModel> {
				for ((category, covers) in map) {
					if (!category.isVisibleInLibrary) continue
					add(
						CategoryListModel(
							mangaCount = covers.size,
							covers = covers.take(COVERS_PREVIEW),
							category = category,
							isTrackerEnabled = category.isTrackingEnabled && isTrackingFavourites(),
							isActionsEnabled = false,
						),
					)
				}
				add(NewFolderItem)
			}
		}
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(NewFolderItem))

	fun createFolder(title: String, isTrackerEnabled: Boolean) {
		if (title.isBlank()) return
		launchJob(Dispatchers.IO) {
			favouritesRepository.createCategory(
				title = title.trim(),
				sortOrder = ListSortOrder.NEWEST,
				isTrackerEnabled = isTrackerEnabled,
				isVisibleOnShelf = true,
			)
		}
	}

	fun setFoldersMode(value: Boolean) {
		if (isFoldersMode.value != value) {
			isFoldersMode.value = value
			settings.isLibraryFoldersMode = value
		}
	}

	/** The per-folder switch only matters when update checks run for favourites at all. */
	private fun isTrackingFavourites() =
		settings.isTrackerEnabled && AppSettings.TRACK_FAVOURITES in settings.trackSources

	private companion object {

		const val COVERS_PREVIEW = 3
	}
}
