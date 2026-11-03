package hanten.wre.app.novel.data.offline

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import hanten.wre.app.core.network.MangaHttpClient
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.util.runCatchingCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File based cache for online novel chapters.
 *
 * Layout: `filesDir/novels/<source>_<mangaId>/chapter_<chapterId>.html` plus downloaded
 * images next to the chapter files. Metadata lives in `meta.json` inside the same folder.
 */
@Singleton
class NovelOfflineRepository @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val httpClient: OkHttpClient,
) {

	private val json = Json {
		ignoreUnknownKeys = true
	}

	private val metaLock = Any()

	private val rootDir: File
		get() = File(context.filesDir, ROOT_DIR).also { dir ->
			if (!dir.exists()) dir.mkdirs()
		}

	suspend fun cacheChapter(manga: Manga, chapter: MangaChapter, html: String) =
		withContext(Dispatchers.IO) {
			if (html.isBlank()) return@withContext
			val dir = novelDir(manga)
			if (!dir.exists() && !dir.mkdirs()) {
				return@withContext
			}

			val document = runCatchingCancellable { Jsoup.parseBodyFragment(html) }.getOrNull()
			val imageNames = mutableListOf<String>()
			if (document != null) {
				for (image in document.select("img[src]")) {
					val source = resolveImageUrl(image.attr("src"), chapter.url) ?: continue
					val file = downloadImage(source, dir, referer = chapter.url) ?: continue
					imageNames += file.name
					image.attr("src", Uri.fromFile(file).toString())
				}
			}

			val output = document?.body()?.html() ?: html
			val htmlFile = chapterFile(dir, chapter.id)
			writeTextAtomically(htmlFile, output)

			val sizeBytes = htmlFile.length() + imageNames.sumOf { File(dir, it).length() }
			updateMeta(dir, manga) { meta ->
				meta.copy(
					chapters = meta.chapters.filterNot { it.id == chapter.id } + ChapterMeta(
						id = chapter.id,
						title = chapter.title.orEmpty(),
						sizeBytes = sizeBytes,
						images = imageNames,
					),
				)
			}
		}

	fun readChapter(mangaId: Long, chapterId: Long): String? {
		val dir = findNovelDir(mangaId) ?: return null
		return runCatchingCancellable {
			chapterFile(dir, chapterId).takeIf { it.isFile }?.readText(Charsets.UTF_8)
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
	}

	fun isChapterCached(mangaId: Long, chapterId: Long): Boolean {
		val dir = findNovelDir(mangaId) ?: return false
		return chapterFile(dir, chapterId).isFile
	}

	suspend fun cacheNovel(
		manga: Manga,
		chapters: List<MangaChapter>,
		fetchHtml: suspend (MangaChapter) -> String?,
		onProgress: (done: Int, total: Int) -> Unit,
	) = withContext(Dispatchers.IO) {
		val total = chapters.size
		var done = 0
		onProgress(done, total)
		for (chapter in chapters) {
			if (!isChapterCached(manga.id, chapter.id)) {
				val html = runCatchingCancellable {
					fetchHtml(chapter)
				}.onFailure {
					it.printStackTraceDebug()
				}.getOrNull()
				if (!html.isNullOrBlank()) {
					runCatchingCancellable {
						cacheChapter(manga, chapter, html)
					}.onFailure {
						it.printStackTraceDebug()
					}
				}
			}
			done++
			onProgress(done, total)
		}
	}

	fun listCachedNovels(): List<CachedNovel> {
		return runCatchingCancellable {
			rootDir.listFiles().orEmpty()
				.filter { it.isDirectory }
				.mapNotNull { dir -> dirToCachedNovel(dir) }
				.sortedBy { it.title.lowercase() }
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrDefault(emptyList())
	}

	fun listCachedChapters(mangaId: Long): List<CachedChapter> {
		return runCatchingCancellable {
			val dir = findNovelDir(mangaId) ?: return@runCatchingCancellable emptyList()
			val meta = readMeta(dir)
			val result = ArrayList<CachedChapter>()
			for (chapter in meta?.chapters.orEmpty()) {
				val file = chapterFile(dir, chapter.id)
				if (!file.isFile) continue
				result += CachedChapter(
					chapterId = chapter.id,
					title = chapter.title.ifBlank { defaultChapterTitle(chapter.id) },
					sizeBytes = if (chapter.sizeBytes > 0) chapter.sizeBytes else file.length(),
				)
			}
			val known = result.mapTo(HashSet()) { it.chapterId }
			for (file in cachedChapterFiles(dir)) {
				val chapterId = chapterIdFromFile(file) ?: continue
				if (chapterId in known) continue
				result += CachedChapter(chapterId, defaultChapterTitle(chapterId), file.length())
			}
			result
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrDefault(emptyList())
	}

	fun deleteNovel(mangaId: Long) {
		val dir = findNovelDir(mangaId) ?: return
		runCatchingCancellable {
			dir.deleteRecursively()
		}.onFailure {
			it.printStackTraceDebug()
		}
	}

	fun deleteChapter(mangaId: Long, chapterId: Long) {
		val dir = findNovelDir(mangaId) ?: return
		runCatchingCancellable {
			val meta = readMeta(dir) ?: return@runCatchingCancellable
			val chapter = meta.chapters.firstOrNull { it.id == chapterId } ?: return@runCatchingCancellable
			val remaining = meta.chapters.filterNot { it.id == chapterId }
			val usedImages = remaining.flatMapTo(HashSet()) { it.images }
			for (image in chapter.images) {
				if (image !in usedImages) File(dir, image).delete()
			}
			chapterFile(dir, chapterId).delete()
			writeMeta(dir, meta.copy(chapters = remaining))
		}.onFailure {
			it.printStackTraceDebug()
		}
	}

	private suspend fun updateMeta(dir: File, manga: Manga, transform: (NovelMeta) -> NovelMeta) =
		withContext(Dispatchers.IO) {
			synchronized(metaLock) {
				val current = readMeta(dir) ?: NovelMeta()
				val updated = transform(
					current.copy(
						id = manga.id,
						title = manga.title,
						source = manga.source.name,
					),
				)
				writeMeta(dir, updated)
			}
		}

	private fun writeMeta(dir: File, meta: NovelMeta) {
		val text = json.encodeToString(NovelMeta.serializer(), meta)
		writeTextAtomically(File(dir, META_FILE), text)
	}

	private fun readMeta(dir: File): NovelMeta? {
		val file = File(dir, META_FILE)
		if (!file.isFile) return null
		return runCatchingCancellable {
			json.decodeFromString(NovelMeta.serializer(), file.readText(Charsets.UTF_8))
		}.getOrNull()
	}

	private fun dirToCachedNovel(dir: File): CachedNovel? {
		val chapterFiles = cachedChapterFiles(dir)
		if (chapterFiles.isEmpty()) return null
		val meta = readMeta(dir)
		val folderName = dir.name
		val id = meta?.id?.takeIf { it != 0L }
			?: folderName.substringAfterLast('_').toLongOrNull()
			?: return null
		val source = meta?.source?.takeIf { it.isNotBlank() }
			?: folderName.substringBeforeLast('_', "")
		return CachedNovel(
			id = id,
			title = meta?.title?.takeIf { it.isNotBlank() } ?: folderName,
			source = source,
			chapterCount = chapterFiles.size,
			sizeBytes = dirSize(dir),
		)
	}

	private fun dirSize(dir: File): Long {
		var size = 0L
		dir.walkTopDown().forEach { file ->
			if (file.isFile) size += file.length()
		}
		return size
	}

	private fun cachedChapterFiles(dir: File): List<File> {
		return dir.listFiles().orEmpty()
			.filter { it.isFile && it.name.startsWith(CHAPTER_PREFIX) && it.name.endsWith(HTML_EXTENSION) }
	}

	private fun chapterIdFromFile(file: File): Long? {
		return file.nameWithoutExtension.removePrefix(CHAPTER_PREFIX).toLongOrNull()
	}

	private fun chapterFile(dir: File, chapterId: Long): File {
		return File(dir, "$CHAPTER_PREFIX$chapterId$HTML_EXTENSION")
	}

	private fun defaultChapterTitle(chapterId: Long) = "Chapter $chapterId"

	private fun novelDir(manga: Manga): File {
		return File(rootDir, "${sanitizeSegment(manga.source.name)}_${manga.id}")
	}

	private fun findNovelDir(mangaId: Long): File? {
		if (mangaId == 0L) return null
		return rootDir.listFiles().orEmpty().firstOrNull { dir ->
			dir.isDirectory && dir.name.substringAfterLast('_') == mangaId.toString()
		}
	}

	private fun resolveImageUrl(raw: String, baseUrl: String?): String? {
		val src = raw.trim()
		if (src.isEmpty() || src.startsWith("data:", ignoreCase = true)) return null
		if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) {
			return src
		}
		if (src.contains("://")) return null
		if (baseUrl.isNullOrBlank()) return null
		return runCatchingCancellable {
			URI(baseUrl).resolve(src).toString()
		}.getOrNull()?.takeIf {
			it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
		}
	}

	private suspend fun downloadImage(url: String, dir: File, referer: String?): File? =
		withContext(Dispatchers.IO) {
			runCatchingCancellable {
				val request = Request.Builder()
					.url(url)
					.header("User-Agent", USER_AGENT)
					.apply {
						if (!referer.isNullOrBlank()) header("Referer", referer)
					}
					.build()
				httpClient.newCall(request).execute().use { response ->
					if (!response.isSuccessful) return@use null
					val body = response.body ?: return@use null
					val contentType = response.header("Content-Type").orEmpty()
					if (contentType.isNotEmpty() && !contentType.startsWith("image/", ignoreCase = true)) {
						return@use null
					}
					if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
					val file = File(dir, imageFileName(url, contentType))
					body.byteStream().use { input ->
						file.outputStream().use { output ->
							input.copyTo(output)
						}
					}
					if (file.length() > MAX_IMAGE_BYTES) {
						file.delete()
						null
					} else {
						file
					}
				}
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrNull()
		}

	private fun imageFileName(url: String, contentType: String): String {
		val fromUrl = url.substringBefore('?')
			.substringBefore('#')
			.substringAfterLast('.', "")
			.lowercase()
		val fromContentType = contentType.substringAfter('/', "")
			.substringBefore(';')
			.trim()
			.lowercase()
		val extension = fromUrl.takeIf { it in IMAGE_EXTENSIONS }
			?: fromContentType.takeIf { it in IMAGE_EXTENSIONS }
			?: DEFAULT_IMAGE_EXTENSION
		return "img_${sha1(url)}.$extension"
	}

	private fun sha1(value: String): String {
		return runCatchingCancellable {
			val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
			digest.joinToString("") { "%02x".format(it) }
		}.getOrDefault(value.hashCode().toUInt().toString(16))
	}

	private fun writeTextAtomically(file: File, text: String) {
		val temp = File(file.parentFile, "${file.name}.tmp")
		temp.writeText(text, Charsets.UTF_8)
		if (!temp.renameTo(file)) {
			file.writeText(text, Charsets.UTF_8)
			temp.delete()
		}
	}

	private fun sanitizeSegment(value: String): String {
		return value.replace(INVALID_NAME_CHARS, "_")
			.trim('.', '_')
			.take(MAX_NAME_LENGTH)
			.ifBlank { "unknown" }
	}

	@Serializable
	private data class NovelMeta(
		val id: Long = 0L,
		val title: String = "",
		val source: String = "",
		val chapters: List<ChapterMeta> = emptyList(),
	)

	@Serializable
	private data class ChapterMeta(
		val id: Long = 0L,
		val title: String = "",
		val sizeBytes: Long = 0L,
		val images: List<String> = emptyList(),
	)

	private companion object {
		private const val ROOT_DIR = "novels"
		private const val META_FILE = "meta.json"
		private const val CHAPTER_PREFIX = "chapter_"
		private const val HTML_EXTENSION = ".html"
		private const val DEFAULT_IMAGE_EXTENSION = "jpg"
		private const val MAX_IMAGE_BYTES = 32L * 1024L * 1024L
		private const val MAX_NAME_LENGTH = 64
		private const val USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

		private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "avif")
		private val INVALID_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
	}
}
