package hanten.wre.app.explore.ui.model

import hanten.wre.app.core.model.MangaSourceInfo
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.parsers.util.longHashCode

data class MangaSourceItem(
	val source: MangaSourceInfo,
	val isGrid: Boolean,
) : ListModel {

	val id: Long = source.name.longHashCode()

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaSourceItem && other.source == source
	}
}
