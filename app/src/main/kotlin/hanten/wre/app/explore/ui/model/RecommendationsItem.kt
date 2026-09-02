package hanten.wre.app.explore.ui.model

import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.MangaCompactListModel

data class RecommendationsItem(
	val manga: List<MangaCompactListModel>
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is RecommendationsItem
	}
}
