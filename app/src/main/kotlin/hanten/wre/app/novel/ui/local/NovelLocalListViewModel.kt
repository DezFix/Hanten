package hanten.wre.app.novel.ui.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.isFileUri
import hanten.wre.app.core.util.ext.resolveFile
import hanten.wre.app.core.util.ext.toFileOrNull
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.explore.domain.ExploreRepository
import hanten.wre.app.filter.ui.FilterCoordinator
import hanten.wre.app.list.domain.MangaListMapper
import hanten.wre.app.local.data.LocalStorageChanges
import hanten.wre.app.local.domain.model.LocalManga
import hanten.wre.app.novel.data.NovelStorageManager
import hanten.wre.app.remotelist.ui.RemoteListViewModel
import javax.inject.Inject

@HiltViewModel
class NovelLocalListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	filterCoordinator: FilterCoordinator,
	settings: AppSettings,
	mangaListMapper: MangaListMapper,
	exploreRepository: ExploreRepository,
	sourcesRepository: MangaSourcesRepository,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
	private val novelStorageManager: NovelStorageManager,
	@ApplicationContext private val context: Context,
) : RemoteListViewModel(
	savedStateHandle = savedStateHandle,
	mangaRepositoryFactory = mangaRepositoryFactory,
	filterCoordinator = filterCoordinator,
	settings = settings,
	mangaListMapper = mangaListMapper,
	exploreRepository = exploreRepository,
	sourcesRepository = sourcesRepository,
	mangaDataRepository = mangaDataRepository,
	localStorageChanges = localStorageChanges,
) {

	fun importFolder(uri: Uri) {
		launchJob(Dispatchers.IO) {
			val dir = (if (uri.isFileUri()) uri.toFileOrNull() else uri.resolveFile(context))
				?: uri.resolveFile(context)
			if (dir != null && (dir.exists() || dir.mkdirs())) {
				novelStorageManager.addNovelDirectory(dir)
			}
			onRefresh()
		}
	}

	fun importFiles(uris: List<Uri>) {
		launchJob(Dispatchers.IO) {
			for (uri in uris) {
				novelStorageManager.importNovel(uri)
			}
			onRefresh()
		}
	}
}