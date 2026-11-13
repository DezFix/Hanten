package hanten.wre.app.details.domain

import hanten.wre.app.core.model.MangaRelationType
import hanten.wre.app.parsers.model.Manga

/**
 * One entry of the reading order shown on the details screen.
 *
 * [manga] is null when the entry was added by title only (the work is not in the library yet);
 * in that case the UI should search for [title] instead of opening details.
 */
data class ReadingOrderItem(
	val order: Int,
	val targetId: Long,
	val manga: Manga?,
	val title: String,
	val sourceName: String?,
	val publicUrl: String?,
	val type: MangaRelationType?,
	val isCurrent: Boolean,
	val isSideStory: Boolean,
)
