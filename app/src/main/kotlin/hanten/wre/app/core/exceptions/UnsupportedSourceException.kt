package hanten.wre.app.core.exceptions

import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaSource

class UnsupportedSourceException(
	message: String?,
	val manga: Manga? = null,
	val source: MangaSource? = null,
) : IllegalArgumentException(message)
