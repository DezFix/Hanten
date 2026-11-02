package hanten.wre.app.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import hanten.wre.app.R

@Keep
enum class AppMode(
	@StringRes val titleRes: Int,
) {
	MANGA(R.string.content_type_manga),
	NOVEL(R.string.content_type_novel),
	;

	val isNovel: Boolean
		get() = this == NOVEL

	val isManga: Boolean
		get() = this == MANGA
}
