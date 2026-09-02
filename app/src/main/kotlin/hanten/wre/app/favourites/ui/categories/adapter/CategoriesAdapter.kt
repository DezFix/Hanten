package hanten.wre.app.favourites.ui.categories.adapter

import hanten.wre.app.core.ui.ReorderableListAdapter
import hanten.wre.app.favourites.ui.categories.FavouriteCategoriesListListener
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.adapter.ListStateHolderListener
import hanten.wre.app.list.ui.adapter.emptyStateListAD
import hanten.wre.app.list.ui.adapter.loadingStateAD
import hanten.wre.app.list.ui.model.ListModel

class CategoriesAdapter(
	onItemClickListener: FavouriteCategoriesListListener,
	listListener: ListStateHolderListener,
) : ReorderableListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.CATEGORY_LARGE, categoryAD(onItemClickListener))
		addDelegate(ListItemType.NAV_ITEM, allCategoriesAD(onItemClickListener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
