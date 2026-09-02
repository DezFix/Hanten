package hanten.wre.app.suggestions.domain

import android.util.Log
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.parsers.util.almostEquals

class TagsBlacklist(
	private val tags: Set<String>,
	private val threshold: Float,
) {

	fun isNotEmpty() = tags.isNotEmpty()

	operator fun contains(manga: Manga): Boolean {
		if (tags.isEmpty()) {
			return false
		}
		for (mangaTag in manga.tags) {
			for (tagTitle in tags) {
				if (mangaTag.title.almostEquals(tagTitle, threshold)) {
					Log.d("TagsBlacklist", "Manga \"${manga.title}\" blacklisted by tag: $tagTitle")
					return true
				}
			}
		}
		return false
	}

	operator fun contains(tag: MangaTag): Boolean = tags.any { tagTitle ->
		val matches = tag.title.almostEquals(tagTitle, threshold)
		if (matches) {
			Log.d("TagsBlacklist", "Tag \"${tag.title}\" is blacklisted (matches: $tagTitle)")
		}
		matches
	}
}
