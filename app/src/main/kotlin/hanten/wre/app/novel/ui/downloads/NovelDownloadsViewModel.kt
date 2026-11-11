package hanten.wre.app.novel.ui.downloads

import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.novel.data.offline.CachedChapter
import hanten.wre.app.novel.data.offline.CachedNovel
import hanten.wre.app.novel.data.offline.NovelDownloadScheduler
import hanten.wre.app.novel.data.offline.NovelDownloadWorker
import hanten.wre.app.novel.data.offline.NovelOfflineRepository
import hanten.wre.app.parsers.util.runCatchingCancellable
import java.util.UUID
import javax.inject.Inject

data class ActiveNovelDownload(
	val id: UUID,
	val title: String,
	val done: Int,
	val total: Int,
	val state: WorkInfo.State,
)

data class NovelDownloadsState(
	val downloads: List<ActiveNovelDownload> = emptyList(),
	val novels: List<CachedNovel> = emptyList(),
	val chapters: Map<Long, List<CachedChapter>> = emptyMap(),
	val expanded: Set<Long> = emptySet(),
	val totalSizeBytes: Long = 0L,
	val isLoading: Boolean = true,
)

@HiltViewModel
class NovelDownloadsViewModel @Inject constructor(
	private val repository: NovelOfflineRepository,
	private val scheduler: NovelDownloadScheduler,
) : BaseViewModel() {

	private val _state = MutableStateFlow(NovelDownloadsState())
	val state: StateFlow<NovelDownloadsState> = _state.asStateFlow()

	init {
		refresh()
		launchJob(Dispatchers.IO) {
			var hadActive = false
			scheduler.observeWorks().collect { works ->
				val downloads = works.toActiveDownloads()
				_state.update { it.copy(downloads = downloads) }
				if (hadActive && downloads.isEmpty()) {
					loadState()
				}
				hadActive = downloads.isNotEmpty()
			}
		}
	}

	fun refresh() {
		viewModelScope.launch(Dispatchers.IO) {
			loadState()
		}
	}

	fun toggleExpanded(mangaId: Long) {
		val current = _state.value
		val expanded = if (mangaId in current.expanded) {
			current.expanded - mangaId
		} else {
			current.expanded + mangaId
		}
		_state.value = current.copy(expanded = expanded)
	}

	fun cancelDownload(id: UUID) {
		scheduler.cancel(id)
	}

	fun deleteNovel(mangaId: Long) {
		viewModelScope.launch(Dispatchers.IO) {
			repository.deleteNovel(mangaId)
			loadState()
		}
	}

	fun deleteChapter(mangaId: Long, chapterId: Long) {
		viewModelScope.launch(Dispatchers.IO) {
			repository.deleteChapter(mangaId, chapterId)
			loadState()
		}
	}

	private fun List<WorkInfo>.toActiveDownloads(): List<ActiveNovelDownload> = filter {
		!it.state.isFinished
	}.map { work ->
		val progress = work.progress
		ActiveNovelDownload(
			id = work.id,
			title = progress.getString(NovelDownloadWorker.KEY_TITLE).orEmpty(),
			done = progress.getInt(NovelDownloadWorker.KEY_PROGRESS, 0),
			total = progress.getInt(NovelDownloadWorker.KEY_MAX, 0),
			state = work.state,
		)
	}

	private fun loadState() {
		runCatchingCancellable {
			val novels = repository.listCachedNovels()
			val chapters = novels.associate { novel ->
				novel.id to repository.listCachedChapters(novel.id)
			}
			val expanded = _state.value.expanded.filterTo(HashSet()) { id ->
				novels.any { it.id == id }
			}
			_state.value = _state.value.copy(
				novels = novels,
				chapters = chapters,
				expanded = expanded,
				totalSizeBytes = novels.sumOf { it.sizeBytes },
				isLoading = false,
			)
		}.onFailure {
			it.printStackTraceDebug()
			_state.value = _state.value.copy(isLoading = false)
		}
	}
}
