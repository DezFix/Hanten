package hanten.wre.app.details.ui.model

import hanten.wre.app.list.ui.ListModelDiffCallback
import hanten.wre.app.list.ui.model.ListModel

data class MangaBranch(
	val name: String?,
	val count: Int,
	val isSelected: Boolean,
	val isCurrent: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaBranch && other.name == name
	}

	override fun getChangePayload(previousState: ListModel): Any? {
		return if (previousState is MangaBranch && previousState.isSelected != isSelected) {
			ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED
		} else {
			super.getChangePayload(previousState)
		}
	}

	override fun toString(): String {
		return "$name: $count"
	}
}
