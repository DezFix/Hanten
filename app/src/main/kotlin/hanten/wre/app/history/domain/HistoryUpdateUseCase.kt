package hanten.wre.app.history.domain

import android.util.Log
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.processLifecycleScope
import hanten.wre.app.download.data.repository.DownloadQueueRepository
import hanten.wre.app.download.domain.usecase.SmartDownloadUseCase
import hanten.wre.app.download.ui.worker.DownloadWorker
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.local.data.LocalMangaRepository
import hanten.wre.app.local.domain.DeleteReadChaptersUseCase
import hanten.wre.app.reader.ui.ReaderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryUpdateUseCase @Inject constructor(
	private val historyRepository: HistoryRepository,
	private val settings: AppSettings,
	private val db: MangaDatabase,
	private val downloadQueueRepository: DownloadQueueRepository,
	private val deleteReadChaptersUseCase: DeleteReadChaptersUseCase,
	private val localMangaRepository: LocalMangaRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val smartDownloadUseCase: SmartDownloadUseCase,
) {

	@Volatile
	private var lastCheckedChapterId: Long = -1L

	// Page turns fire this many times per second. Independent writes could land out of order and
	// roll the history back to an older page, so they are serialized and only the newest survives.
	private val pendingUpdates = Channel<PendingUpdate>(Channel.CONFLATED)

	init {
		processLifecycleScope.launch(Dispatchers.IO) {
			for (update in pendingUpdates) {
				runCatchingCancellable {
					withContext(NonCancellable) {
						invoke(update.manga, update.readerState, update.percent)
					}
				}.onFailure {
					it.printStackTraceDebug("HistoryUpdateUseCase::invokeAsync")
				}
			}
		}
	}

	suspend operator fun invoke(manga: Manga, readerState: ReaderState, percent: Float) {
		historyRepository.addOrUpdate(
			manga = manga,
			chapterId = readerState.chapterId,
			page = readerState.page,
			scroll = readerState.scroll,
			percent = percent,
			force = false,
		)
		if (settings.isAutoDownloadNextChapterEnabled && lastCheckedChapterId != readerState.chapterId) {
			Log.d("SmartDownloads", "Chapter changed, triggering smart download for ${manga.title}")
			lastCheckedChapterId = readerState.chapterId
			smartDownloadUseCase(manga, readerState.chapterId)
		}
	}

	fun invokeAsync(
		manga: Manga,
		readerState: ReaderState,
		percent: Float
	) {
		pendingUpdates.trySend(
			PendingUpdate(
				manga = manga,
				readerState = readerState,
				percent = percent,
			),
		)
	}

	private data class PendingUpdate(
		val manga: Manga,
		val readerState: ReaderState,
		val percent: Float,
	)
}
