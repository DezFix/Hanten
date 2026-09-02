package hanten.wre.app.download.domain.usecase

import android.util.Log
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.db.entity.ChapterEntity
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.TriStateOption
import hanten.wre.app.download.data.repository.DownloadQueueRepository
import hanten.wre.app.download.data.repository.SmartDownloadRepository
import hanten.wre.app.local.data.LocalMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartDownloadUseCase @Inject constructor(
    private val smartDownloadRepository: SmartDownloadRepository,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val localMangaRepository: LocalMangaRepository,
    private val db: MangaDatabase,
    private val settings: AppSettings,
    private val mangaRepositoryFactory: MangaRepository.Factory,
) {
    suspend operator fun invoke(manga: Manga, currentChapterId: Long) {
        if (!settings.isAutoDownloadNextChapterEnabled) return

        // Ensure chapters are loaded in DB
        var chapters = db.getChaptersDao().findAll(manga.id)
        if (chapters.isEmpty()) {
            Log.d("SmartDownload", "Chapters missing in DB, refreshing from repository")
            try {
                val repo = mangaRepositoryFactory.create(manga.source)
                val details = repo.getDetails(manga)
                val entities = details.chapters.orEmpty().withIndex().map { (index, chapter) ->
                    ChapterEntity(
                        mangaId = manga.id,
                        chapterId = chapter.id,
                        title = chapter.title.orEmpty(),
                        branch = chapter.branch,
                        index = index,
                        number = chapter.number,
                        volume = chapter.volume,
                        url = chapter.url,
                        scanlator = chapter.scanlator,
                        uploadDate = chapter.uploadDate,
                        source = chapter.source.name,
                    )
                }
                db.getChaptersDao().replaceAll(manga.id, entities)
                chapters = entities
            } catch (e: Exception) {
                Log.e("SmartDownload", "Failed to refresh chapters", e)
                return
            }
        }

        val currentChapter = chapters.find { it.chapterId == currentChapterId } ?: return
        val currentIndex = currentChapter.index

        // Sync with actual downloaded chapters to keep table accurate
        val localManga = localMangaRepository.findSavedManga(manga, withDetails = true)
        val downloadedChapterIds = localManga?.manga?.chapters?.map { it.id }?.toSet() ?: emptySet()
        val downloadedIndices = chapters
            .filter { it.chapterId in downloadedChapterIds }
            .map { it.index }
            .sorted()
            .toIntArray()

        if (downloadedIndices.isEmpty()) {
            smartDownloadRepository.updateCurrentIndex(manga.id, currentIndex)
            smartDownloadRepository.updateDownloadedIndices(manga.id, intArrayOf())
            return
        }

        val maxDownloadedIndex = downloadedIndices.maxOrNull() ?: -1

        if (currentIndex >= maxDownloadedIndex) {
            // Download next chapter in the same branch
            val branchChapters = chapters.filter { it.branch == currentChapter.branch }
            val currentIdxInBranch = branchChapters.indexOfFirst { it.chapterId == currentChapterId }
            
            if (currentIdxInBranch != -1 && currentIdxInBranch < branchChapters.size - 1) {
                val nextChapter = branchChapters[currentIdxInBranch + 1]
                if (nextChapter.chapterId !in downloadedChapterIds) {
                    Log.d("SmartDownload", "Enqueuing next chapter: ${nextChapter.title} (index ${nextChapter.index})")
                    val wifiOnly = settings.allowDownloadOnMeteredNetwork == TriStateOption.DISABLED
                    downloadQueueRepository.addToQueue(
                        manga = manga,
                        chaptersIds = longArrayOf(nextChapter.chapterId),
                        wifiOnly = wifiOnly,
                        chargingOnly = settings.isDownloadOnlyOnChargingEnabled,
                        offPeakOnly = settings.isDownloadOffPeakEnabled,
                        isPaused = false
                    )
                }
            }
        }

        // Final sync of the table
        val finalLocalManga = localMangaRepository.findSavedManga(manga, withDetails = true)
        val finalDownloadedIds = finalLocalManga?.manga?.chapters?.map { it.id }?.toSet() ?: emptySet()
        val finalDownloadedIndices = chapters
            .filter { it.chapterId in finalDownloadedIds }
            .map { it.index }
            .sorted()
            .toIntArray()

        smartDownloadRepository.updateCurrentIndex(manga.id, currentIndex)
        smartDownloadRepository.updateDownloadedIndices(manga.id, finalDownloadedIndices)
    }
}
