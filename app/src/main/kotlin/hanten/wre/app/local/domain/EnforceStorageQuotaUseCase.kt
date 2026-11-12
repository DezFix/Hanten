package hanten.wre.app.local.domain

import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.local.data.LocalMangaRepository
import hanten.wre.app.novel.data.offline.NovelOfflineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class EnforceStorageQuotaUseCase @Inject constructor(
    private val settings: AppSettings,
    private val localMangaRepository: LocalMangaRepository,
    private val novelOfflineRepository: NovelOfflineRepository,
    private val db: MangaDatabase,
) {
    suspend operator fun invoke(): Boolean = withContext(Dispatchers.IO) {
        val quotaMb = settings.downloadStorageQuota
        if (quotaMb <= 0) return@withContext true

        val quotaBytes = quotaMb * 1024 * 1024
        val storageDirs = localMangaRepository.getAllFiles().toList()
        var mangaSize = storageDirs.sumOf { getDirSize(it) }
        val novelSize = novelOfflineRepository.getCacheSizeBytes()

        if (mangaSize + novelSize <= quotaBytes) return@withContext true

        // Get all downloaded chapters and sort by oldest accessed (approx by history or file time)
        // Here we use file last modified time as a proxy for "oldest"
        val allChapters = localMangaRepository.getRawList()
            .flatMap { manga ->
                val dir = localMangaRepository.getOutputDir(manga.manga, null)
                dir?.listFiles()?.filter { it.isDirectory }?.map { it to manga } ?: emptyList()
            }
            .sortedBy { it.first.lastModified() }

        for ((chapterDir, localManga) in allChapters) {
            if (mangaSize + novelSize <= quotaBytes) break

            val size = getDirSize(chapterDir)
            val chapterId = chapterDir.name.toLongOrNull() ?: continue

            localMangaRepository.deleteChapters(localManga.manga, setOf(chapterId))
            mangaSize -= size
        }

        if (mangaSize + novelSize > quotaBytes) {
            // Only read-only cache is evicted here; explicit novel downloads are kept.
            novelOfflineRepository.trimCache((quotaBytes - mangaSize).coerceAtLeast(0))
        }

        mangaSize + novelOfflineRepository.getCacheSizeBytes() <= quotaBytes
    }

    suspend fun getUsage(): StorageUsage? = withContext(Dispatchers.IO) {
        val quotaMb = settings.downloadStorageQuota
        if (quotaMb <= 0) return@withContext null
        val storageDirs = localMangaRepository.getAllFiles().toList()
        val currentSize = storageDirs.sumOf { getDirSize(it) } + novelOfflineRepository.getCacheSizeBytes()
        StorageUsage(currentSize, quotaMb * 1024 * 1024)
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    data class StorageUsage(val currentBytes: Long, val totalBytes: Long)
}
