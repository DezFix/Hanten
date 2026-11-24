package hanten.wre.app.list.ui.adapter

import androidx.core.view.isVisible
import hanten.wre.app.R
import hanten.wre.app.core.model.contentType
import hanten.wre.app.core.ui.widgets.IconsView
import hanten.wre.app.parsers.model.ContentRating
import hanten.wre.app.parsers.model.ContentType
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaState

/**
 * Hanten 3.0 (Library mockup): small round status badges on a cover, each with its own color.
 * Most important first, at most [MAX_BADGES] so they never cover the art.
 */
fun IconsView.bindCoverBadges(
	manga: Manga,
	isSaved: Boolean,
	isFavorite: Boolean,
	isStale: Boolean,
	isTracked: Boolean,
) {
	clearIcons()
	val badges = buildList {
		if (isFavorite) add(R.drawable.ic_heart_outline to COLOR_FAVORITE)
		when (manga.state) {
			MangaState.FINISHED -> add(R.drawable.ic_badge_done to COLOR_DONE)
			MangaState.ABANDONED -> add(R.drawable.ic_badge_dropped to COLOR_DROPPED)
			MangaState.PAUSED -> add(R.drawable.ic_badge_paused to COLOR_PAUSED)
			// no new chapters for a long time
			else -> if (isStale) add(R.drawable.ic_badge_frozen to COLOR_FROZEN)
		}
		if (isSaved) add(R.drawable.ic_badge_dl to COLOR_SAVED)
		if (manga.contentType == ContentType.NOVEL) add(R.drawable.ic_badge_novel to COLOR_NOVEL)
		if (isTracked) add(R.drawable.ic_badge_track to COLOR_TRACKED)
		if (manga.contentRating == ContentRating.ADULT || manga.contentType == ContentType.HENTAI) {
			add(R.drawable.ic_badge_nsfw to COLOR_NSFW)
		}
	}
	badges.take(MAX_BADGES).forEach { (icon, color) -> addIcon(icon, color) }
	isVisible = iconsCount > 0
}

private const val MAX_BADGES = 3
private const val COLOR_FAVORITE = 0xFFC7353B.toInt()
private const val COLOR_DONE = 0xFF1F2A4D.toInt()
private const val COLOR_DROPPED = 0xFF6B6F84.toInt()
private const val COLOR_PAUSED = 0xFF8A5A00.toInt()
private const val COLOR_FROZEN = 0xFF2F4B8A.toInt()
private const val COLOR_SAVED = 0xFF3F6B3A.toInt()
private const val COLOR_NOVEL = 0xFFA63D41.toInt()
private const val COLOR_TRACKED = 0xFF7A3E73.toInt()
private const val COLOR_NSFW = 0xFFB3261E.toInt()
