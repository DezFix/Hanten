package hanten.wre.app.core.ui.model

import hanten.wre.app.parsers.model.ContentRating

data class MangaOverride(
	val coverUrl: String?,
	val title: String?,
	val contentRating: ContentRating?,
)
