package hanten.wre.app.core.model

import hanten.wre.app.parsers.model.MangaPage

/** Unified chapter payload: images for manga, HTML for novels. */
sealed interface ChapterContent {

	data class Images(val pages: List<MangaPage>) : ChapterContent

	data class Document(
		val html: String,
		val baseUrl: String?,
	) : ChapterContent
}

/** HTML for the text reader and the offline cache. Image chapters become a list of <img> tags. */
fun ChapterContent.toReaderHtml(): String = when (this) {
	is ChapterContent.Document -> html
	is ChapterContent.Images -> pages.joinToString(separator = "") { page ->
		"<p><img src=\"" + page.url.replace("\"", "&quot;") + "\"/></p>"
	}
}
