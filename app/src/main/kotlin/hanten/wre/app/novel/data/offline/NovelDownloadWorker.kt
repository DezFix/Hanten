package hanten.wre.app.novel.data.offline

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import hanten.wre.app.R
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.download.data.repository.DownloadQueueRepository
import hanten.wre.app.core.util.ext.toFileNameSafe
import hanten.wre.app.local.data.LocalMangaRepository
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.util.runCatchingCancellable
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltWorker
class NovelDownloadWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val mangaDataRepository: MangaDataRepository,
	private val repositoryFactory: MangaRepository.Factory,
	private val offlineRepository: NovelOfflineRepository,
	private val epubExporter: NovelEpubExporter,
	private val localMangaRepository: LocalMangaRepository,
	private val downloadQueueRepository: DownloadQueueRepository,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		setForeground(getForegroundInfo())
		return try {
			val mangaId = inputData.getLong(KEY_MANGA_ID, 0L)
			val chapterIds = inputData.getLongArray(KEY_CHAPTER_IDS)?.toList().orEmpty()
			val manga = mangaDataRepository.findMangaById(mangaId, withChapters = true)
				?: return Result.failure()
			val chapters = chapterIds.mapNotNull { chapterId ->
				manga.chapters?.firstOrNull { it.id == chapterId }
			}
			val repository = repositoryFactory.create(manga.source)
			val total = chapters.size
			var done = 0
			setProgress(progressData(manga, done, total))
			for (chapter in chapters) {
				if (isStopped) {
					return Result.failure()
				}
				coroutineContext.ensureActive()
				if (!offlineRepository.isChapterDownloaded(manga.id, chapter.id)) {
					val html = repository.getPages(chapter).joinToString(separator = "") { it.url }
					offlineRepository.cacheChapter(manga, chapter, html, explicit = true)
				}
				done++
				setProgress(progressData(manga, done, total))
			}
			exportEpub(manga)
			downloadQueueRepository.removeFromQueueByManga(manga.id)
			Result.success()
		} catch (e: CancellationException) {
			throw e
		} catch (e: IOException) {
			Result.retry()
		} catch (e: Throwable) {
			e.printStackTraceDebug("NovelDownloadWorker::doWork")
			Result.failure()
		} finally {
			NotificationManagerCompat.from(applicationContext).cancel(id.hashCode())
		}
	}

	override suspend fun getForegroundInfo(): ForegroundInfo {
		val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
			.setName(applicationContext.getString(R.string.downloads))
			.setShowBadge(false)
			.setSound(null, null)
			.setVibrationEnabled(false)
			.build()
		NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
		val contentIntent = PendingIntentCompat.getActivity(
			applicationContext,
			0,
			Intent(applicationContext, hanten.wre.app.download.ui.list.DownloadsActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
			},
			0,
			false,
		)
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_download)
			.setContentTitle(applicationContext.getString(R.string.downloads))
			.setContentText(applicationContext.getString(R.string.novel_download_started))
			.setContentIntent(contentIntent)
			.setOngoing(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.build()
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			ForegroundInfo(id.hashCode(), notification)
		}
	}

	private suspend fun exportEpub(manga: Manga) {
		val root = localMangaRepository.getOutputDir(manga, fallback = null) ?: return
		if (!root.exists() && !root.mkdirs()) return
		epubExporter.export(manga.id, File(root, "${manga.title.toFileNameSafe()}.epub"))
	}

	private fun progressData(manga: Manga, done: Int, total: Int) = workDataOf(
		KEY_MANGA_ID to manga.id,
		KEY_PROGRESS to done,
		KEY_MAX to total,
		KEY_TITLE to manga.title,
	)

	private fun Manga.originUrl(): String? = runCatchingCancellable {
		val uri = URI(publicUrl.ifBlank { url })
		val host = uri.host ?: return@runCatchingCancellable null
		"${uri.scheme ?: "https"}://$host/"
	}.getOrNull()

	companion object {

		const val TAG = "novel_download"
		const val TAG_MANGA_PREFIX = "novel_manga_"
		private const val CHANNEL_ID = "novel_downloads"
		const val KEY_MANGA_ID = "manga_id"
		const val KEY_CHAPTER_IDS = "chapter_ids"
		const val KEY_ALLOW_METERED = "allow_metered"
		const val KEY_PROGRESS = "progress"
		const val KEY_MAX = "max"
		const val KEY_TITLE = "title"

		fun uniqueName(mangaId: Long) = "novel_download_$mangaId"

		fun createRequest(
			manga: Manga,
			chapterIds: LongArray,
			allowMetered: Boolean,
		): OneTimeWorkRequest = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
			.setConstraints(
				Constraints.Builder()
					.setRequiredNetworkType(
						if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED,
					)
					.build(),
			)
			.addTag(TAG)
			.addTag(TAG_MANGA_PREFIX + manga.id)
			.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
			.setInputData(
				workDataOf(
					KEY_MANGA_ID to manga.id,
					KEY_CHAPTER_IDS to chapterIds,
					KEY_ALLOW_METERED to allowMetered,
				),
			)
			.build()
	}
}

@Reusable
class NovelDownloadScheduler @Inject constructor(
	@ApplicationContext context: Context,
	private val workManager: WorkManager,
	private val mangaDataRepository: MangaDataRepository,
	private val downloadQueueRepository: DownloadQueueRepository,
) {

	suspend fun enqueue(manga: Manga, chapterIds: LongArray, allowMetered: Boolean) {
		mangaDataRepository.storeManga(manga, replaceExisting = true)
		workManager.enqueueUniqueWork(
			NovelDownloadWorker.uniqueName(manga.id),
			ExistingWorkPolicy.KEEP,
			NovelDownloadWorker.createRequest(manga, chapterIds, allowMetered),
		)
	}

	/** Stops the running work and puts the whole novel back into the queue as paused. */
	suspend fun pause(manga: Manga) {
		workManager.cancelUniqueWork(NovelDownloadWorker.uniqueName(manga.id))
		val existing = downloadQueueRepository.getQueue().filter { it.mangaId == manga.id }
		if (existing.isNotEmpty()) {
			existing.forEach { downloadQueueRepository.updatePaused(it.id, true) }
			return
		}
		val chapters = manga.chapters ?: return
		if (chapters.isEmpty()) return
		downloadQueueRepository.addToQueue(
			manga = manga,
			chaptersIds = chapters.map { it.id }.toLongArray(),
			isPaused = true,
		)
	}

	fun observeWorks(): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(NovelDownloadWorker.TAG)

	fun cancel(id: UUID) {
		workManager.cancelWorkById(id)
	}

	fun cancelAll() {
		workManager.cancelAllWorkByTag(NovelDownloadWorker.TAG)
	}
}
