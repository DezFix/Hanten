package hanten.wre.app.explore.ui.model

import hanten.wre.app.list.ui.model.ListModel

/** Hanten 3.0 Catalog footer: links to the source catalog, Tachiyomi extensions and source management. */
data object ExploreMoreSources : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other === ExploreMoreSources
}
