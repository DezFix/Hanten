package hanten.wre.app.explore.ui.model

import hanten.wre.app.core.model.MangaSourceInfo
import hanten.wre.app.list.ui.ListModelDiffCallback
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.parsers.util.longHashCode

data class MangaSourceItem(
	val source: MangaSourceInfo,
	val isGrid: Boolean,
) : ListModel {

	val id: Long = source.name.longHashCode()

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaSourceItem && other.source.name == source.name
	}

	override fun getChangePayload(previousState: ListModel): Any? {
		return if (previousState is MangaSourceItem && previousState.source.name == source.name) {
			ListModelDiffCallback.PAYLOAD_ANYTHING_CHANGED
		} else {
			super<ListModel>.getChangePayload(previousState)
		}
	}
}
