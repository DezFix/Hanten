package hanten.wre.app.core.exceptions

import okio.IOException
import hanten.wre.app.parsers.model.MangaSource

abstract class CloudFlareException(
	message: String,
	val state: Int,
) : IOException(message) {

	abstract val url: String

	abstract val source: MangaSource
}
