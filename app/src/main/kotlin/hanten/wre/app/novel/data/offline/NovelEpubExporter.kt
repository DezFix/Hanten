package hanten.wre.app.novel.data.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaParserSource
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds an EPUB from explicitly downloaded novel chapters. Images are taken from the
 * offline cache (they were rewritten to `file://` there) and embedded into the book.
 */
@Singleton
class NovelEpubExporter @Inject constructor(
	private val offlineRepository: NovelOfflineRepository,
	private val mangaDataRepository: MangaDataRepository,
) {

	suspend fun export(mangaId: Long, output: File): Boolean = withContext(Dispatchers.IO) {
		val manga = mangaDataRepository.findMangaById(mangaId, withChapters = true)
			?: return@withContext false
		val chapters = manga.chapters.orEmpty()
			.filter { offlineRepository.isChapterDownloaded(mangaId, it.id) }
		if (chapters.isEmpty()) return@withContext false
		val language = languageOf(manga)

		val documents = ArrayList<Pair<String, String>>(chapters.size)
		val images = LinkedHashMap<File, String>()
		for (chapter in chapters) {
			val html = offlineRepository.readChapter(mangaId, chapter.id) ?: continue
			val title = chapter.title?.takeIf { it.isNotBlank() } ?: chapter.number.toString()
			documents += title to prepareChapterHtml(html, images)
		}
		if (documents.isEmpty()) return@withContext false

		val temp = File(output.parentFile, "${output.name}.tmp")
		runCatching {
			ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
				writeMimetype(zip)
				zip.putText("META-INF/container.xml", CONTAINER)
				zip.putText("OEBPS/content.opf", buildOpf(manga, documents.size, images.values, language))
				zip.putText("OEBPS/nav.xhtml", buildNav(documents, language))
				documents.forEachIndexed { index, (title, body) ->
					zip.putText("OEBPS/ch$index.xhtml", buildChapter(title, body, language))
				}
				images.forEach { (file, name) ->
					zip.putNextEntry(ZipEntry("OEBPS/images/$name"))
					file.inputStream().use { it.copyTo(zip) }
					zip.closeEntry()
				}
			}
			if (output.exists()) output.delete()
			temp.renameTo(output)
		}.onFailure {
			temp.delete()
			it.printStackTraceDebug()
		}.isSuccess
	}

	private fun prepareChapterHtml(html: String, images: MutableMap<File, String>): String {
		val doc = Jsoup.parseBodyFragment(html)
		doc.outputSettings()
			.syntax(Document.OutputSettings.Syntax.xml)
			.charset("UTF-8")
		doc.select("script, style, iframe, ins").remove()
		for (image in doc.select("img[src]")) {
			val src = image.attr("src")
			if (!src.startsWith("file://")) continue
			val file = runCatching { File(URI(src)) }.getOrNull() ?: continue
			if (!file.isFile) {
				image.remove()
				continue
			}
			val name = images.getOrPut(file) {
				"img_${images.size}.${file.extension.ifBlank { "jpg" }}"
			}
			image.attr("src", "images/$name")
		}
		return doc.body().html()
	}

	private fun writeMimetype(zip: ZipOutputStream) {
		val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
		val crc = CRC32().apply { update(bytes) }
		zip.putNextEntry(
			ZipEntry("mimetype").apply {
				method = ZipEntry.STORED
				size = bytes.size.toLong()
				compressedSize = bytes.size.toLong()
				this.crc = crc.value
			},
		)
		zip.write(bytes)
		zip.closeEntry()
	}

	private fun ZipOutputStream.putText(name: String, text: String) {
		putNextEntry(ZipEntry(name))
		write(text.toByteArray(Charsets.UTF_8))
		closeEntry()
	}

	private fun buildChapter(title: String, body: String, language: String): String = """
		<?xml version="1.0" encoding="utf-8"?>
		<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="$language">
		<head><title>${escapeXml(title)}</title>
		<style>
			body { font-family: serif; line-height: 1.6; margin: 1em; }
			p { margin: 0 0 0.9em 0; text-indent: 1.2em; }
			h1, h2, h3 { text-align: center; text-indent: 0; }
			img { max-width: 100%; height: auto; }
		</style>
		</head>
		<body>
		<h1>${escapeXml(title)}</h1>
		$body
		</body>
		</html>
	""".trimIndent()

	private fun buildNav(documents: List<Pair<String, String>>, language: String): String {
		val items = documents.mapIndexed { index, (title, _) ->
			"<li><a href=\"ch$index.xhtml\">${escapeXml(title)}</a></li>"
		}.joinToString("\n")
		return """
			<?xml version="1.0" encoding="utf-8"?>
			<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$language">
			<head><title>Contents</title></head>
			<body><nav epub:type="toc" id="toc"><h1>Contents</h1><ol>
			$items
			</ol></nav></body>
			</html>
		""".trimIndent()
	}

	private fun buildOpf(
		manga: Manga,
		chapterCount: Int,
		imageNames: Collection<String>,
		language: String,
	): String {
		val chapterItems = (0 until chapterCount).joinToString("\n") {
			"<item id=\"ch$it\" href=\"ch$it.xhtml\" media-type=\"application/xhtml+xml\"/>"
		}
		val imageItems = imageNames.mapIndexed { index, name ->
			"<item id=\"img$index\" href=\"images/$name\" media-type=\"${mimeOf(name)}\"/>"
		}.joinToString("\n")
		val spine = (0 until chapterCount).joinToString("\n") {
			"<itemref idref=\"ch$it\"/>"
		}
		return """
			<?xml version="1.0" encoding="utf-8"?>
			<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
			<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
			<dc:identifier id="bookid">urn:uuid:${manga.id}-${System.currentTimeMillis()}</dc:identifier>
			<dc:title>${escapeXml(manga.title)}</dc:title>
			<dc:language>$language</dc:language>
			<meta property="dcterms:modified">${java.time.Instant.now()}</meta>
			</metadata>
			<manifest>
			<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
			$chapterItems
			$imageItems
			</manifest>
			<spine>
			$spine
			</spine>
			</package>
		""".trimIndent()
	}

	private fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
		"png" -> "image/png"
		"gif" -> "image/gif"
		"webp" -> "image/webp"
		"svg" -> "image/svg+xml"
		"avif" -> "image/avif"
		else -> "image/jpeg"
	}

	private fun escapeXml(value: String): String = buildString(value.length) {
		for (char in value) {
			when (char) {
				'&' -> append("&amp;")
				'<' -> append("&lt;")
				'>' -> append("&gt;")
				'"' -> append("&quot;")
				'\'' -> append("&apos;")
				else -> append(char)
			}
		}
	}

	private fun languageOf(manga: Manga): String {
		val locale = (manga.source as? MangaParserSource)?.locale
		return locale?.takeIf { it.isNotBlank() } ?: "ru"
	}

	private companion object {
		val CONTAINER = """
			<?xml version="1.0" encoding="utf-8"?>
			<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
			<rootfiles>
			<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
			</rootfiles>
			</container>
		""".trimIndent()
	}
}
