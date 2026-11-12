package hanten.wre.app.download.ui.list

import androidx.collection.ArrayMap
import androidx.collection.LongSet
import androidx.collection.LongSparseArray
import androidx.collection.getOrElse
import androidx.collection.set
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import hanten.wre.app.R
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.ui.model.DateTimeAgo
import hanten.wre.app.core.ui.util.ReversibleAction
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.calculateTimeAgo
import hanten.wre.app.core.util.ext.call
import hanten.wre.app.core.util.ext.isEmpty
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.download.domain.DownloadState
import hanten.wre.app.download.ui.list.chapters.DownloadChapter
import hanten.wre.app.download.ui.worker.DownloadWorker
import hanten.wre.app.list.ui.model.EmptyState
import hanten.wre.app.list.ui.model.ListHeader
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.LoadingState
import hanten.wre.app.local.data.LocalMangaRepository
import hanten.wre.app.local.data.LocalStorageChanges
import hanten.wre.app.local.domain.DeleteLocalMangaUseCase
import hanten.wre.app.local.domain.EnforceStorageQuotaUseCase
import hanten.wre.app.local.domain.model.LocalManga
import hanten.wre.app.novel.data.offline.CachedChapter
import hanten.wre.app.novel.data.offline.CachedNovel
import hanten.wre.app.novel.data.offline.NovelDownloadScheduler
import hanten.wre.app.novel.data.offline.NovelDownloadWorker
import hanten.wre.app.novel.data.offline.NovelOfflineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.util.mapToSet
import hanten.wre.app.parsers.util.runCatchingCancellable
import java.util.LinkedList
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
	private val workScheduler: DownloadWorker.Scheduler,
	private val mangaDataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
	private val localMangaRepository: LocalMangaRepository,
	private val enforceStorageQuotaUseCase: EnforceStorageQuotaUseCase,
	private val deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
	private val novelOfflineRepository: NovelOfflineRepository,
	private val novelDownloadScheduler: NovelDownloadScheduler,
) : BaseViewModel() {

	val storageUsage = MutableStateFlow<EnforceStorageQuotaUseCase.StorageUsage?>(null)

	private val mangaCache = LongSparseArray<Manga>()
	private val cacheMutex = Mutex()
	private val expanded = MutableStateFlow(emptySet<UUID>())
	private val chaptersCache = ArrayMap<UUID, StateFlow<List<DownloadChapter>?>>()

	private val novelExpanded = MutableStateFlow(emptySet<Long>())
	private val cachedNovels = MutableStateFlow<List<CachedNovel>>(emptyList())
	private val cachedNovelChapters = MutableStateFlow<Map<Long, List<CachedChapter>>>(emptyMap())

	private val works = combine(
		workScheduler.observeWorks(),
		expanded,
	) { list, exp ->
		list.toDownloadsList(exp)
	}.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	private val novelWorks = novelDownloadScheduler.observeWorks()
		.map { list -> list.filter { !it.state.isFinished } }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	private val novelItems: StateFlow<List<NovelDownloadItemModel>> = combine(
		novelWorks,
		cachedNovels,
		cachedNovelChapters,
		novelExpanded,
	) { activeWorks, novels, chapters, exp ->
		activeWorks.toNovelItems(novels, chapters, exp)
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	init {
		refreshNovelCache()
		launchJob(Dispatchers.IO) {
			var previouslyActive = emptySet<UUID>()
			novelDownloadScheduler.observeWorks().collect { workInfos ->
				val active = workInfos.filterTo(HashSet()) { !it.state.isFinished }.mapTo(HashSet()) { it.id }
				if (previouslyActive.any { it !in active }) {
					refreshNovelCache()
				}
				previouslyActive = active
			}
		}
	}

	val onActionDone = MutableEventFlow<ReversibleAction>()

	val items: StateFlow<List<ListModel>> = combine(works, novelItems) { manga, novels ->
		if (manga == null) {
			listOf<ListModel>(LoadingState)
		} else {
			when {
				manga.isEmpty() && novels.isEmpty() -> emptyStateList()
				manga.isEmpty() -> novels
				else -> manga.toUiList() + novels
			}
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	val hasPausedWorks = works.map {
		it?.any { x -> x.canResume } == true
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.WhileSubscribed(5000), false)

	val hasActiveWorks = works.map {
		it?.any { x -> x.canPause } == true
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.WhileSubscribed(5000), false)

	val hasCancellableWorks = works.map {
		it?.any { x -> !x.workState.isFinished } == true
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.WhileSubscribed(5000), false)

	fun cancel(id: UUID) {
		launchJob(Dispatchers.IO) {
			workScheduler.cancel(id)
		}
	}

	fun cancel(ids: Set<Long>) {
		launchJob(Dispatchers.IO) {
			val snapshot = works.value ?: return@launchJob
			for (work in snapshot) {
				if (work.id.mostSignificantBits in ids) {
					workScheduler.cancel(work.id)
				}
			}
			onActionDone.call(ReversibleAction(R.string.downloads_cancelled, null))
		}
	}

	fun cancelAll() {
		launchJob(Dispatchers.IO) {
			workScheduler.cancelAll()
			onActionDone.call(ReversibleAction(R.string.downloads_cancelled, null))
		}
	}

	fun pause(ids: Set<Long>) {
		val snapshot = works.value ?: return
		for (work in snapshot) {
			if (work.id.mostSignificantBits in ids) {
				workScheduler.pause(work.id)
			}
		}
		onActionDone.call(ReversibleAction(R.string.downloads_paused, null))
	}

	fun pauseAll() {
		val snapshot = works.value ?: return
		var isPaused = false
		for (work in snapshot) {
			if (work.canPause) {
				workScheduler.pause(work.id)
				isPaused = true
			}
		}
		if (isPaused) {
			onActionDone.call(ReversibleAction(R.string.downloads_paused, null))
		}
	}

	fun resumeAll() {
		val snapshot = works.value ?: return
		var isResumed = false
		for (work in snapshot) {
			if (work.workState == WorkInfo.State.RUNNING && work.isPaused) {
				workScheduler.resume(work.id)
				isResumed = true
			}
		}
		if (isResumed) {
			onActionDone.call(ReversibleAction(R.string.downloads_resumed, null))
		}
	}

	fun resume(ids: Set<Long>) {
		val snapshot = works.value ?: return
		for (work in snapshot) {
			if (work.id.mostSignificantBits in ids) {
				workScheduler.resume(work.id)
			}
		}
		onActionDone.call(ReversibleAction(R.string.downloads_resumed, null))
	}

	fun remove(ids: Set<Long>, deleteFiles: Boolean) {
		launchJob(Dispatchers.IO) {
			val snapshot = works.value ?: return@launchJob
			val uuids = HashSet<UUID>(ids.size)
			for (work in snapshot) {
				if (work.id.mostSignificantBits in ids) {
					uuids.add(work.id)
					if (deleteFiles) {
						val manga = work.manga ?: continue
						runCatchingCancellable {
							deleteLocalMangaUseCase(manga)
						}.onFailure {
							it.printStackTraceDebug("DownloadsViewModel::remove")
						}
					}
				}
			}
			workScheduler.delete(uuids)
			onActionDone.call(ReversibleAction(R.string.downloads_removed, null))
			refreshStorageUsage()
		}
	}

	fun removeCompleted(deleteFiles: Boolean) {
		launchJob(Dispatchers.IO) {
			if (deleteFiles) {
				val snapshot = works.value ?: return@launchJob
				for (work in snapshot) {
					if (work.workState.isFinished) {
						val manga = work.manga ?: continue
						runCatchingCancellable {
							deleteLocalMangaUseCase(manga)
						}.onFailure {
							it.printStackTraceDebug("DownloadsViewModel::removeCompleted")
						}
					}
				}
			}
			workScheduler.removeCompleted()
			onActionDone.call(ReversibleAction(R.string.downloads_removed, null))
			refreshStorageUsage()
		}
	}

	fun deleteChapter(item: DownloadItemModel, chapter: DownloadChapter) {
		val manga = item.manga ?: return
		launchJob(Dispatchers.IO) {
			localMangaRepository.deleteChapters(manga, setOf(chapter.id))
			refreshStorageUsage()
		}
	}

	fun refreshStorageUsage() {
		launchJob(Dispatchers.IO) {
			storageUsage.value = enforceStorageQuotaUseCase.getUsage()
		}
	}

	fun snapshot(ids: LongSet): Collection<DownloadItemModel> {
		return works.value?.filterTo(ArrayList(ids.size)) { x -> x.id.mostSignificantBits in ids }.orEmpty()
	}

	fun allIds(): Set<Long> = works.value?.mapToSet {
		it.id.mostSignificantBits
	} ?: emptySet()

	fun expandCollapse(item: DownloadItemModel) {
		expanded.update {
			if (item.id in it) {
				it - item.id
			} else {
				it + item.id
			}
		}
	}

	fun cancelNovel(id: UUID) {
		launchJob(Dispatchers.IO) {
			novelDownloadScheduler.cancel(id)
		}
	}

	fun expandCollapseNovel(item: NovelDownloadItemModel) {
		novelExpanded.update {
			if (item.mangaId in it) {
				it - item.mangaId
			} else {
				it + item.mangaId
			}
		}
	}

	fun deleteNovel(item: NovelDownloadItemModel) {
		launchJob(Dispatchers.IO) {
			novelOfflineRepository.deleteNovel(item.mangaId)
			refreshNovelCache()
		}
	}

	fun deleteNovelChapter(item: NovelDownloadItemModel, chapter: NovelChapterItem) {
		launchJob(Dispatchers.IO) {
			novelOfflineRepository.deleteChapter(item.mangaId, chapter.id)
			refreshNovelCache()
		}
	}

	fun refreshNovelCache() {
		launchJob(Dispatchers.IO) {
			runCatchingCancellable {
				val novels = novelOfflineRepository.listCachedNovels()
				cachedNovels.value = novels
				cachedNovelChapters.value = novels.associate { it.id to novelOfflineRepository.listCachedChapters(it.id) }
			}.onFailure {
				it.printStackTraceDebug("DownloadsViewModel::refreshNovelCache")
			}
		}
	}

	private fun List<WorkInfo>.toNovelItems(
		novels: List<CachedNovel>,
		chapters: Map<Long, List<CachedChapter>>,
		expanded: Set<Long>,
	): List<NovelDownloadItemModel> {
		val result = LinkedHashMap<Long, NovelDownloadItemModel>(novels.size + size)
		for (novel in novels) {
			result[novel.id] = NovelDownloadItemModel(
				mangaId = novel.id,
				title = novel.title,
				sourceName = novel.source,
				chapterCount = novel.chapterCount,
				sizeBytes = novel.sizeBytes,
				workId = null,
				done = 0,
				total = 0,
				isExpanded = novel.id in expanded,
				chapters = chapters[novel.id].orEmpty().map { chapter ->
					NovelChapterItem(chapter.chapterId, chapter.title, chapter.sizeBytes)
				},
			)
		}
		for (work in this) {
			val mangaId = work.progress.getLong(NovelDownloadWorker.KEY_MANGA_ID, 0L)
				.takeIf { it != 0L }
				?: work.tags.firstOrNull { it.startsWith(NovelDownloadWorker.TAG_MANGA_PREFIX) }
					?.removePrefix(NovelDownloadWorker.TAG_MANGA_PREFIX)
					?.toLongOrNull()
				?: continue
			val existing = result[mangaId] ?: NovelDownloadItemModel(
				mangaId = mangaId,
				title = work.progress.getString(NovelDownloadWorker.KEY_TITLE).orEmpty(),
				sourceName = "",
				chapterCount = 0,
				sizeBytes = 0L,
				workId = null,
				done = 0,
				total = 0,
				isExpanded = mangaId in expanded,
				chapters = emptyList(),
			)
			result[mangaId] = existing.copy(
				workId = work.id,
				done = work.progress.getInt(NovelDownloadWorker.KEY_PROGRESS, 0),
				total = work.progress.getInt(NovelDownloadWorker.KEY_MAX, 0),
			)
		}
		return result.values.toList()
	}

	private suspend fun List<WorkInfo>.toDownloadsList(exp: Set<UUID>): List<DownloadItemModel> {
		if (isEmpty()) {
			return emptyList()
		}
		val list = mapNotNullTo(ArrayList(size)) { it.toUiModel(it.id in exp) }
		list.sortByDescending { it.timestamp }
		return list
	}

	private fun List<DownloadItemModel>.toUiList(): List<ListModel> {
		if (isEmpty()) {
			return emptyList()
		}
		val queued = LinkedList<ListModel>()
		val running = LinkedList<ListModel>()
		val destination = ArrayDeque<ListModel>((size * 1.4).toInt())
		var prevDate: DateTimeAgo? = null
		for (item in this) {
			when (item.workState) {
				WorkInfo.State.RUNNING -> running += item
				WorkInfo.State.BLOCKED,
				WorkInfo.State.ENQUEUED -> queued += item

				else -> {
					val date = calculateTimeAgo(item.timestamp)
					if (prevDate != date) {
						destination += if (date != null) {
							ListHeader(date)
						} else {
							ListHeader(R.string.unknown)
						}
					}
					prevDate = date
					destination += item
				}
			}
		}
		if (running.isNotEmpty()) {
			running.addFirst(ListHeader(R.string.in_progress))
		}
		destination.addAll(0, running)
		if (queued.isNotEmpty()) {
			queued.addFirst(ListHeader(R.string.queued))
		}
		destination.addAll(0, queued)
		return destination
	}

	private suspend fun WorkInfo.toUiModel(isExpanded: Boolean): DownloadItemModel? {
		val workData = outputData.takeUnless { it.isEmpty }
			?: progress.takeUnless { it.isEmpty }
			?: workScheduler.getInputData(id)
			?: return null
		val mangaId = DownloadState.getMangaId(workData)
		if (mangaId == 0L) return null
		val manga = getManga(mangaId) ?: return null
		val chapters = synchronized(chaptersCache) {
			chaptersCache.getOrPut(id) {
				observeChapters(manga, id)
			}
		}
		return DownloadItemModel(
			id = id,
			workState = state,
			manga = manga,
			error = DownloadState.getError(workData),
			isIndeterminate = DownloadState.isIndeterminate(workData),
			isPaused = DownloadState.isPaused(workData),
			max = DownloadState.getMax(workData),
			progress = DownloadState.getProgress(workData),
			eta = DownloadState.getEta(workData),
			isStuck = DownloadState.isStuck(workData),
			timestamp = DownloadState.getTimestamp(workData),
			chaptersDownloaded = DownloadState.getDownloadedChapters(workData),
			isExpanded = isExpanded,
			chapters = chapters,
		)
	}

	private fun emptyStateList(): List<ListModel> = listOf(
		EmptyState(
			icon = R.drawable.ic_empty_common,
			textPrimary = R.string.text_downloads_list_holder,
			textSecondary = 0,
			actionStringRes = 0,
		),
	)

	private suspend fun getManga(mangaId: Long): Manga? {
		mangaCache[mangaId]?.let {
			return it
		}
		return cacheMutex.withLock {
			mangaCache.getOrElse(mangaId) {
				mangaDataRepository.findMangaById(mangaId, withChapters = true)?.also {
					mangaCache[mangaId] = it
				} ?: return null
			}
		}
	}

	private fun observeChapters(manga: Manga, workId: UUID): StateFlow<List<DownloadChapter>?> = flow {
		val chapterIds = workScheduler.getTask(workId)?.chaptersIds
		val chapters = (tryLoad(manga) ?: manga).chapters ?: return@flow

		suspend fun mapChapters(): List<DownloadChapter> {
			val size = chapterIds?.size ?: chapters.size
			val localChapters =
				localMangaRepository.findSavedManga(manga)?.manga?.chapters?.mapToSet { it.id }.orEmpty()
			return chapters.mapNotNullTo(ArrayList(size)) {
				if (chapterIds == null || it.id in chapterIds) {
					DownloadChapter(
						id = it.id,
						number = it.numberString(),
						name = it.name,
						isDownloaded = it.id in localChapters,
					)
				} else {
					null
				}
			}
		}
		emit(mapChapters())
		localStorageChanges.collect {
			if (it?.manga?.id == manga.id) {
				emit(mapChapters())
			}
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	private suspend fun tryLoad(manga: Manga) = runCatchingCancellable {
		mangaRepositoryFactory.create(manga.source).getDetails(manga)
	}.getOrNull()
}
