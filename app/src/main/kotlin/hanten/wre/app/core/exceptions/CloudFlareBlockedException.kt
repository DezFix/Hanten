package hanten.wre.app.core.exceptions

import hanten.wre.app.core.model.UnknownMangaSource
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.network.CloudFlareHelper

class CloudFlareBlockedException(
	override val url: String,
	source: MangaSource?,
) : CloudFlareException("Blocked by CloudFlare", CloudFlareHelper.PROTECTION_BLOCKED) {

	override val source: MangaSource = source ?: UnknownMangaSource
}
