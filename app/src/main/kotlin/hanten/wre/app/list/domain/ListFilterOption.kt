package hanten.wre.app.list.domain

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import hanten.wre.app.R
import hanten.wre.app.core.db.entity.toEntity
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.model.LocalMangaSource
import hanten.wre.app.core.model.unwrap
import hanten.wre.app.core.parser.external.ExternalMangaSource
import hanten.wre.app.core.parser.favicon.faviconUri
import hanten.wre.app.core.model.LocalNovelSource
import hanten.wre.app.core.model.titleResId
import hanten.wre.app.parsers.model.ContentType
import hanten.wre.app.parsers.model.MangaState
import hanten.wre.app.parsers.model.MangaParserSource
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.model.MangaTag

sealed interface ListFilterOption {

	@get:StringRes
	val titleResId: Int

	@get:DrawableRes
	val iconResId: Int

	val titleText: CharSequence?

	val groupKey: String

	fun getIconData(): Any? = null

	data object Downloaded : ListFilterOption {

		override val titleResId: Int
			get() = R.string.on_device

		override val iconResId: Int
			get() = R.drawable.ic_storage

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = "_downloaded"
	}

	enum class Macro(
		@StringRes override val titleResId: Int,
		@DrawableRes override val iconResId: Int,
	) : ListFilterOption {

		COMPLETED(R.string.filter_read_completed, R.drawable.ic_state_finished),
        READING(R.string.status_reading, R.drawable.ic_state_ongoing),
		NOT_STARTED(R.string.filter_not_started, R.drawable.ic_history),
		TRACKED(R.string.filter_tracked, R.drawable.ic_services),
		NEW_CHAPTERS(R.string.new_chapters, R.drawable.ic_updated),
		FAVORITE(R.string.favourites, R.drawable.ic_heart_outline),
		NSFW(R.string.nsfw, R.drawable.ic_nsfw),
		;

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = name
	}

	data class Branch(
		override val titleText: String?,
		val chaptersCount: Int,
	) : ListFilterOption {

		override val titleResId: Int
			get() = if (titleText == null) R.string.system_default else 0

		override val iconResId: Int
			get() = R.drawable.ic_language

		override val groupKey: String
			get() = "_branch"
	}

	/** Library: content type (manga, manhwa, ranobe…); matched by the sources that give this type */
	data class Type(
		val contentType: ContentType,
		val sourceNames: Set<String>,
	) : ListFilterOption {

		override val titleResId: Int
			get() = contentType.titleResId

		override val iconResId: Int
			get() = 0

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = "_type"
	}

	/** Catalog: sources of one language ("" = sources with several languages) */
	data class Language(
		val code: String,
		override val titleText: CharSequence?,
	) : ListFilterOption {

		override val titleResId: Int
			get() = if (titleText == null) R.string.various_languages else 0

		override val iconResId: Int
			get() = 0

		override val groupKey: String
			get() = "_lang"
	}

	/** Library: release status reported by the source (ongoing, finished, paused…) */
	data class State(
		val state: MangaState,
	) : ListFilterOption {

		override val titleResId: Int
			get() = state.titleResId

		override val iconResId: Int
			get() = 0

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = "_state"
	}

	data class Tag(
		val tag: MangaTag
	) : ListFilterOption {

		val tagId: Long = tag.toEntity().id

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = R.drawable.ic_tag

		override val titleText: String
			get() = tag.title

		override val groupKey: String
			get() = "_tag"
	}

	data class Favorite(
		val category: FavouriteCategory
	) : ListFilterOption {

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = R.drawable.ic_heart_outline

		override val titleText: String
			get() = category.title

		override val groupKey: String
			get() = "_favcat"
	}

	data class Source(
		val mangaSource: MangaSource
	) : ListFilterOption {
		override val titleResId: Int
			get() = when (mangaSource.unwrap()) {
				is ExternalMangaSource -> R.string.external_source
				LocalMangaSource -> R.string.local_storage
				hanten.wre.app.core.model.LocalNovelSource -> R.string.light_novels
				else -> 0
			}

		override val iconResId: Int
			get() = R.drawable.ic_web

		override val titleText: CharSequence?
			get() = when (val source = mangaSource.unwrap()) {
				is MangaParserSource -> source.title
				else -> null
			}

		override val groupKey: String
			get() = "_source"

		override fun getIconData() = mangaSource.faviconUri()
	}

	data class Inverted(
		val option: ListFilterOption,
		override val iconResId: Int,
		override val titleResId: Int,
		override val titleText: CharSequence?,
	) : ListFilterOption {

		override val groupKey: String
			get() = "_inv" + option.groupKey
	}

	companion object {

		val SFW
			get() = Inverted(
				option = Macro.NSFW,
				iconResId = R.drawable.ic_sfw,
				titleResId = R.string.sfw,
				titleText = null,
			)

		val NOT_FAVORITE
			get() = Inverted(
				option = Macro.FAVORITE,
				iconResId = R.drawable.ic_heart_off,
				titleResId = R.string.not_in_favorites,
				titleText = null,
			)
	}
}
