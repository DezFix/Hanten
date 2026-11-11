package hanten.wre.app.novel.data.source

import hanten.wre.app.parsers.model.ContentType
import hanten.wre.app.parsers.model.MangaSource

enum class NovelParserSource(
	val title: String,
	val domain: String,
	val defaultUrl: String,
	val locale: String = "en",
	val contentType: ContentType = ContentType.NOVEL,
) : MangaSource {
	RANOBE_ME(
		title = "Ranobe.me",
		domain = "ranobe.me",
		defaultUrl = "https://ranobe.me",
		locale = "ru",
	);

	companion object {
		fun fromName(name: String?): NovelParserSource? {
			if (name == null) return null
			return entries.find { it.name.equals(name, ignoreCase = true) }
		}
	}
}