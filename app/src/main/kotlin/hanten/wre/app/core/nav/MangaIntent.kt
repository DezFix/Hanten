package hanten.wre.app.core.nav

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import hanten.wre.app.core.model.parcelable.ParcelableManga
import hanten.wre.app.core.nav.AppRouter.Companion.KEY_ID
import hanten.wre.app.core.nav.AppRouter.Companion.KEY_MANGA
import hanten.wre.app.core.nav.AppRouter.Companion.KEY_SOURCE_TITLE
import hanten.wre.app.core.util.ext.getParcelableCompat
import hanten.wre.app.core.util.ext.getParcelableExtraCompat
import hanten.wre.app.parsers.model.Manga

class MangaIntent private constructor(
	@JvmField val manga: Manga?,
	@JvmField val id: Long,
	@JvmField val uri: Uri?,
	@JvmField val sourceTitle: String?,
) {

	constructor(intent: Intent?) : this(
		manga = intent?.getParcelableExtraCompat<ParcelableManga>(KEY_MANGA)?.manga,
		id = intent?.getLongExtra(KEY_ID, ID_NONE) ?: ID_NONE,
		uri = intent?.data,
		sourceTitle = intent?.getStringExtra(KEY_SOURCE_TITLE),
	)

	constructor(savedStateHandle: SavedStateHandle) : this(
		manga = savedStateHandle.get<ParcelableManga>(KEY_MANGA)?.manga,
		id = savedStateHandle[KEY_ID] ?: ID_NONE,
		uri = savedStateHandle[AppRouter.KEY_DATA],
		sourceTitle = savedStateHandle[KEY_SOURCE_TITLE],
	)

	constructor(args: Bundle?) : this(
		manga = args?.getParcelableCompat<ParcelableManga>(KEY_MANGA)?.manga,
		id = args?.getLong(KEY_ID, ID_NONE) ?: ID_NONE,
		uri = null,
		sourceTitle = args?.getString(KEY_SOURCE_TITLE),
	)

	val mangaId: Long
		get() = if (id != ID_NONE) id else manga?.id ?: uri?.lastPathSegment?.toLongOrNull() ?: ID_NONE

	companion object {

		const val ID_NONE = 0L

		fun of(manga: Manga) = MangaIntent(manga, manga.id, null, null)
	}
}
