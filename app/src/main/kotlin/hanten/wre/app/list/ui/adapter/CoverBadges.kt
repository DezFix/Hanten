package hanten.wre.app.list.ui.adapter

import androidx.core.view.isVisible
import hanten.wre.app.R
import hanten.wre.app.core.model.contentType
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.ui.widgets.IconsView
import hanten.wre.app.parsers.model.ContentRating
import hanten.wre.app.parsers.model.ContentType
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaState
import androidx.appcompat.R as appcompatR

/**
 * Hanten 3.0: small round status badges on a cover, most important first, at most [MAX_BADGES]
 * so they never cover the art. They follow the app theme: plate in the surface color, icon in
 * the accent (18+ in the error color), so they change with the theme and accent settings.
 */
fun IconsView.bindCoverBadges(
	manga: Manga,
	isSaved: Boolean,
	isFavorite: Boolean,
	isStale: Boolean,
	isTracked: Boolean,
) {
	clearIcons()
	val accent = context.getThemeColor(appcompatR.attr.colorPrimary)
	val warning = context.getThemeColor(appcompatR.attr.colorError)
	val badges = buildList {
		if (isFavorite) add(R.drawable.ic_heart_outline to accent)
		when (manga.state) {
			MangaState.FINISHED -> add(R.drawable.ic_badge_done to accent)
			MangaState.ABANDONED -> add(R.drawable.ic_badge_dropped to accent)
			MangaState.PAUSED -> add(R.drawable.ic_badge_paused to accent)
			// no new chapters for a long time
			else -> if (isStale) add(R.drawable.ic_badge_frozen to accent)
		}
		if (isSaved) add(R.drawable.ic_badge_dl to accent)
		if (manga.contentType == ContentType.NOVEL) add(R.drawable.ic_badge_novel to accent)
		if (isTracked) add(R.drawable.ic_badge_track to accent)
		if (manga.contentRating == ContentRating.ADULT || manga.contentType == ContentType.HENTAI) {
			add(R.drawable.ic_badge_nsfw to warning)
		}
	}
	badges.take(MAX_BADGES).forEach { (icon, color) -> addIcon(icon, color) }
	isVisible = iconsCount > 0
}

private const val MAX_BADGES = 3
