package hanten.wre.app.novel.data.epub

import android.util.Xml
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import hanten.wre.app.core.util.ext.printStackTraceDebug
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.openZip
import okio.buffer
import hanten.wre.app.parsers.util.longHashCode
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

class EpubParser(private val file: File) {

	private var zipFs: FileSystem? = null

	private fun zipFileSystem(): FileSystem = zipFs ?: FileSystem.SYSTEM.openZip(file.toOkioPath()).also { zipFs = it }

	fun close() {
		runCatching { zipFs?.close() }
		zipFs = null
	}

	fun parseBook(): EpubBook? {
		return try {
			val zipFs = zipFileSystem()
			val opfPathStr = getOpfPath(zipFs) ?: return null
			val opfPath = opfPathStr.toPath()
			val opfDir = opfPath.parent?.toString()?.removePrefix("/") ?: ""

			var title = file.nameWithoutExtension
			var author: String? = null
			var description: String? = null
			var coverHref: String? = null

			val manifest = mutableMapOf<String, ManifestItem>()
			val spine = mutableListOf<String>()
			var tocHref: String? = null
			var navHref: String? = null

			zipFs.read(opfPath) {
				val parser = Xml.newPullParser().apply {
					setInput(inputStream(), "UTF-8")
				}
				var eventType = parser.eventType
				var currentTag = ""

				while (eventType != XmlPullParser.END_DOCUMENT) {
					when (eventType) {
						XmlPullParser.START_TAG -> {
							currentTag = parser.name.lowercase()
							when (currentTag) {
								"item" -> {
									val id = parser.getAttributeValue(null, "id").orEmpty()
									val href = parser.getAttributeValue(null, "href").orEmpty()
									val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
									val properties = parser.getAttributeValue(null, "properties").orEmpty()
									manifest[id] = ManifestItem(id, href, mediaType, properties)
									if (properties.contains("cover-image") || id.contains("cover", ignoreCase = true)) {
										coverHref = resolveHref(opfDir, href)
									}
									if (properties.split(' ').contains("nav")) {
										navHref = resolveHref(opfDir, href)
									}
								}
								"itemref" -> {
									val idref = parser.getAttributeValue(null, "idref").orEmpty()
									spine.add(idref)
								}
								"spine" -> {
									val tocId = parser.getAttributeValue(null, "toc")
									if (tocId != null) {
										manifest[tocId]?.let { tocHref = resolveHref(opfDir, it.href) }
									}
								}
							}
						}
						XmlPullParser.TEXT -> {
							val text = parser.text?.trim().orEmpty()
							if (text.isNotEmpty()) {
								when {
									currentTag.endsWith("title") && title == file.nameWithoutExtension -> title = text
									currentTag.endsWith("creator") && author == null -> author = text
									currentTag.endsWith("description") && description == null -> description = text
								}
							}
						}
					}
					eventType = parser.next()
				}
			}

			// Read table of contents for chapter titles if available
			val tocTitles = mutableMapOf<String, String>()
			if (tocHref != null) {
				val tocPath = tocHref?.toPath()
				if (tocPath != null && zipFs.exists(tocPath)) {
					zipFs.read(tocPath) {
						parseTocNcx(inputStream(), tocTitles)
					}
				}
			}
			// EPUB 3 may ship only nav.xhtml and no NCX.
			navHref?.toPath()?.let { navPath ->
				if (tocTitles.isEmpty() && zipFs.exists(navPath)) {
					zipFs.read(navPath) {
						parseTocNav(readUtf8(), navPath.parent?.toString().orEmpty(), tocTitles)
					}
				}
			}

			val chapters = spine.mapNotNull { idref ->
				val item = manifest[idref] ?: return@mapNotNull null
				val resolved = resolveHref(opfDir, item.href)
				val chapterTitle = tocTitles[resolved]
					?: tocTitles[item.href]
					?: item.href.substringAfterLast('/').substringBeforeLast('.')
						.replace('-', ' ')
						.replace('_', ' ')
						.replaceFirstChar { it.uppercase() }
				EpubChapter(
					id = resolved.longHashCode(),
					title = chapterTitle,
					href = resolved,
					index = spine.indexOf(idref),
				)
			}

			EpubBook(
				id = file.absolutePath.longHashCode(),
				title = title,
				author = author,
				description = description,
				coverHref = coverHref,
				chapters = chapters,
				opfDir = opfDir,
			)
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	fun readEntry(path: String): InputStream? {
		return try {
			val zipFs = zipFileSystem()
			val okioPath = path.removePrefix("/").toPath()
			if (zipFs.exists(okioPath)) {
				zipFs.source(okioPath).buffer().inputStream()
			} else {
				null
			}
		} catch (e: Exception) {
			null
		}
	}

	fun getChapterHtml(href: String): String? {
		return try {
			val zipFs = zipFileSystem()
			val okioPath = href.removePrefix("/").toPath()
			if (!zipFs.exists(okioPath)) return null
			zipFs.read(okioPath) {
				readUtf8()
			}
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	private fun getOpfPath(zipFs: FileSystem): String? {
		val containerPath = "META-INF/container.xml".toPath()
		if (!zipFs.exists(containerPath)) return null
		return zipFs.read(containerPath) {
			val parser = Xml.newPullParser().apply {
				setInput(inputStream(), "UTF-8")
			}
			var eventType = parser.eventType
			var opfPath: String? = null
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
					opfPath = parser.getAttributeValue(null, "full-path")
					break
				}
				eventType = parser.next()
			}
			opfPath
		}
	}

	private fun parseTocNav(html: String, dir: String, result: MutableMap<String, String>) {
		try {
			val document = Jsoup.parse(html, "", Parser.xmlParser())
			for (link in document.select("nav a[href]")) {
				val href = link.attr("href").substringBefore('#')
				val title = link.text().trim()
				if (href.isNotBlank() && title.isNotBlank()) {
					result.putIfAbsent(resolveHref(dir.removePrefix("/"), href), title)
				}
			}
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}

	private fun parseTocNcx(input: InputStream, result: MutableMap<String, String>) {
		try {
			val parser = Xml.newPullParser().apply {
				setInput(input, "UTF-8")
			}
			var eventType = parser.eventType
			var currentLabel = ""
			var inNavLabel = false

			while (eventType != XmlPullParser.END_DOCUMENT) {
				when (eventType) {
					XmlPullParser.START_TAG -> {
						val tag = parser.name.lowercase()
						if (tag == "navlabel") inNavLabel = true
						if (tag == "content") {
							val src = parser.getAttributeValue(null, "src").orEmpty().substringBefore('#')
							if (currentLabel.isNotEmpty() && src.isNotEmpty()) {
								result[src] = currentLabel
							}
						}
					}
					XmlPullParser.TEXT -> {
						if (inNavLabel) {
							val text = parser.text?.trim().orEmpty()
							if (text.isNotEmpty()) currentLabel = text
						}
					}
					XmlPullParser.END_TAG -> {
						if (parser.name.equals("navlabel", ignoreCase = true)) {
							inNavLabel = false
						}
					}
				}
				eventType = parser.next()
			}
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}

	private fun resolveHref(baseDir: String, href: String): String {
		return if (baseDir.isEmpty()) {
			href.removePrefix("/")
		} else {
			"$baseDir/${href.removePrefix("/")}"
		}
	}

	private data class ManifestItem(
		val id: String,
		val href: String,
		val mediaType: String,
		val properties: String,
	)
}