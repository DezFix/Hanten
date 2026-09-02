package hanten.wre.app.favourites.domain.model

import hanten.wre.app.core.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource by lazy { MangaSource(source) }
}
