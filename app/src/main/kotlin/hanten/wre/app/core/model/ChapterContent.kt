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
