package hanten.wre.app.settings.sources.catalog

import hanten.wre.app.list.ui.ListModelDiffCallback
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.parsers.model.ContentType

data class SourceCatalogPage(
	val type: ContentType,
	val items: List<SourceCatalogItem>,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is SourceCatalogPage && other.type == type
	}

	override fun getChangePayload(previousState: ListModel): Any {
		return ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
	}
}
