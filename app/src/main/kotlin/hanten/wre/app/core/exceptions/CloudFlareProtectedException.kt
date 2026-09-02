package hanten.wre.app.core.exceptions

import okhttp3.Headers
import hanten.wre.app.core.model.UnknownMangaSource
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.network.CloudFlareHelper

class CloudFlareProtectedException(
	override val url: String,
	source: MangaSource?,
	@Transient val headers: Headers,
) : CloudFlareException("Protected by CloudFlare", CloudFlareHelper.PROTECTION_CAPTCHA) {

	override val source: MangaSource = source ?: UnknownMangaSource
}
