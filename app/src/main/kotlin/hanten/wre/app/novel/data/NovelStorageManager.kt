package hanten.wre.app.novel.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hanten.wre.app.core.model.LocalNovelSource
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.isFileUri
import hanten.wre.app.core.util.ext.isReadable
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.resolveFile
import hanten.wre.app.core.util.ext.toFileOrNull
import hanten.wre.app.novel.data.epub.EpubParser
import hanten.wre.app.novel.data.pdf.PdfRendererHelper
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.parsers.util.longHashCode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelStorageManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val mangaRepository: MangaDataRepository,
	private val settings: AppSettings,
) {

	private val parseCache = ConcurrentHashMap<String, CachedNovel>()

	val novelsDir: File
		get() {
			val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "novels")
			if (!dir.exists()) dir.mkdirs()
			return dir
		}

	fun getNovelDirectories(): List<File> {
		return (listOf(novelsDir) + settings.userSpecifiedNovelDirectories.toList()).distinct()
	}

	fun addNovelDirectory(dir: File) {
		settings.userSpecifiedNovelDirectories = settings.userSpecifiedNovelDirectories + dir
	}

	fun removeNovelDirectory(dir: File) {
		settings.userSpecifiedNovelDirectories = settings.userSpecifiedNovelDirectories - dir
	}

	suspend fun importNovel(uri: Uri): Manga? = withContext(Dispatchers.IO) {
		try {
			val sourceFile = when {
				uri.isFileUri() -> uri.toFileOrNull()
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && DocumentsContract.isTreeUri(uri) ->
					uri.resolveFile(context)
				else -> null
			}?.takeIf { it.exists() && it.isReadable() }

			// An already managed file must not be copied again.
			if (sourceFile != null && sourceFile.parentFile?.canonicalFile == novelsDir.canonicalFile) {
				return@withContext parseFileToManga(sourceFile)
			}

			val destFile = novelDestinationFile(uri, sourceFile?.name) ?: return@withContext null
			if (sourceFile != null) {
				sourceFile.copyTo(destFile, overwrite = false)
			} else {
				context.contentResolver.openInputStream(uri)?.use { input ->
					FileOutputStream(destFile).use { output ->
						input.copyTo(output)
					}
				} ?: return@withContext null
			}

			parseFileToManga(destFile)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	suspend fun getAllNovels(): List<Manga> = withContext(Dispatchers.IO) {
		val directories = getNovelDirectories()
		val novels = mutableListOf<Manga>()
		val seenPaths = mutableSetOf<String>()

		for (dir in directories) {
			try {
				if (!dir.exists() || !dir.isDirectory || !dir.isReadable()) continue
				val files = dir.listFiles() ?: continue
				for (file in files) {
					if (file.isHidden || file.name.startsWith(".")) continue
					if (!file.isFile || !file.isReadable()) continue
					if (!isSupportedFile(file)) continue
					if (!seenPaths.add(file.absolutePath)) continue

					parseFileToManga(file)?.let { novels.add(it) }
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
			}
		}
		novels
	}

	private suspend fun parseFileToManga(file: File): Manga? {
		return try {
			val isEpub = file.extension.equals("epub", ignoreCase = true)
			val isPdf = file.extension.equals("pdf", ignoreCase = true)
			if (!isEpub && !isPdf) return null

			val stamp = file.lastModified() * 31L + file.length()
			parseCache[file.absolutePath]?.takeIf { it.stamp == stamp }?.let {
				return it.manga
			}

			val title = file.nameWithoutExtension
			val mangaId = file.absolutePath.longHashCode()

			val manga = if (isEpub) {
				val epubParser = EpubParser(file)
				val book = epubParser.parseBook()
				val cover = extractCover(file, epubParser, book?.coverHref)
				epubParser.close()
				val chapters = book?.chapters?.map { ec ->
					MangaChapter(
						id = ec.id,
						title = ec.title,
						number = ec.index.toFloat(),
						volume = 0,
						url = ec.href,
						uploadDate = 0L,
						source = LocalNovelSource,
						scanlator = null,
						branch = null,
					)
				} ?: emptyList()

				Manga(
					id = mangaId,
					title = book?.title ?: title,
					altTitle = null,
					url = file.absolutePath,
					publicUrl = file.absolutePath,
					rating = -1f,
					isNsfw = false,
					coverUrl = cover.orEmpty(),
					tags = emptySet<MangaTag>(),
					state = null,
					author = book?.author,
					largeCoverUrl = null,
					description = book?.description,
					chapters = chapters,
					source = LocalNovelSource,
				)
			} else {
				val pdfHelper = PdfRendererHelper(file)
				val count = pdfHelper.pageCount
				val chapters = (0 until count).map { pageIdx ->
					MangaChapter(
						id = pageIdx.toLong(),
						title = "Page ${pageIdx + 1}",
						number = (pageIdx + 1).toFloat(),
						volume = 0,
						url = "pdf://page/$pageIdx",
						uploadDate = 0L,
						source = LocalNovelSource,
						scanlator = null,
						branch = null,
					)
				}
				pdfHelper.close()

				Manga(
					id = mangaId,
					title = title,
					altTitle = null,
					url = file.absolutePath,
					publicUrl = file.absolutePath,
					rating = -1f,
					isNsfw = false,
					coverUrl = "",
					tags = emptySet<MangaTag>(),
					state = null,
					author = null,
					largeCoverUrl = null,
					description = "PDF Document ($count pages)",
					chapters = chapters,
					source = LocalNovelSource,
				)
			}

			parseCache[file.absolutePath] = CachedNovel(stamp, manga)
			mangaRepository.storeManga(manga, replaceExisting = true)
			manga
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	private fun extractCover(file: File, parser: EpubParser, href: String?): String? {
		if (href.isNullOrBlank()) return null
		return try {
			val dir = File(context.cacheDir, "novel_covers").apply { mkdirs() }
			val extension = href.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "jpg"
			val name = "${file.absolutePath.hashCode().toUInt().toString(16)}_${href.hashCode().toUInt().toString(16)}"
			val output = File(dir, "$name.$extension")
			if (!output.isFile) {
				parser.readEntry(href)?.use { input ->
					output.outputStream().use { out -> input.copyTo(out) }
				} ?: return null
			}
			output.toURI().toString()
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	private fun novelDestinationFile(uri: Uri, fallbackName: String? = null): File? {
		val dir = novelsDir
		val type = try {
			context.contentResolver.getType(uri)
		} catch (e: Exception) {
			null
		}
		val ext = when {
			type?.contains("epub", ignoreCase = true) == true -> ".epub"
			type?.contains("pdf", ignoreCase = true) == true -> ".pdf"
			else -> null
		}
		val fallback = "novel_${System.currentTimeMillis()}${ext.orEmpty()}"
		val rawName = (queryDisplayName(uri) ?: fallbackName)
			?.let { File(it).name }
			?.takeIf { it.isNotBlank() && it != "." && it != ".." }
		val name = when {
			rawName == null -> fallback
			rawName.endsWith(".epub", ignoreCase = true) || rawName.endsWith(".pdf", ignoreCase = true) -> rawName
			ext != null -> rawName + ext
			else -> fallback
		}

		val base = name.substringBeforeLast('.', name)
		val suffix = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
		for (index in 0..MAX_DUPLICATE_INDEX) {
			val candidateName = if (index == 0) name else "$base ($index)$suffix"
			val candidate = File(dir, candidateName)
			if (!candidate.exists()) {
				return candidate.takeIf { isInside(dir, it) }
			}
		}
		return null
	}

	private fun isInside(dir: File, file: File): Boolean {
		return try {
			file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
		} catch (e: Exception) {
			false
		}
	}

	private fun queryDisplayName(uri: Uri): String? {
		return try {
			context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
				if (cursor.moveToFirst()) {
					val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					if (idx != -1) cursor.getString(idx) else null
				} else null
			}
		} catch (e: Exception) {
			null
		}
	}

	private data class CachedNovel(val stamp: Long, val manga: Manga)

	companion object {
		private const val MAX_DUPLICATE_INDEX = 100

		fun isSupportedFile(file: File): Boolean {
			return file.extension.equals("epub", ignoreCase = true) ||
				file.extension.equals("pdf", ignoreCase = true)
		}
	}
}