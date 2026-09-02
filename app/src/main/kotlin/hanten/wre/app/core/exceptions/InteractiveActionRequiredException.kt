package hanten.wre.app.core.exceptions

import okio.IOException
import hanten.wre.app.parsers.model.MangaSource

class InteractiveActionRequiredException(
	val source: MangaSource,
	val url: String,
) : IOException("Interactive action is required for ${source.name}")
