package hanten.wre.app.explore.ui.adapter

import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.explore.ui.model.MangaSourceItem
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.adapter.emptyHintAD
import hanten.wre.app.list.ui.adapter.listHeaderAD
import hanten.wre.app.list.ui.adapter.loadingStateAD
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.parsers.model.Manga

class ExploreAdapter(
	listener: ExploreListEventListener,
	clickListener: OnListItemClickListener<MangaSourceItem>,
	mangaClickListener: OnListItemClickListener<Manga>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.EXPLORE_BUTTONS, exploreButtonsAD(listener))
		addDelegate(
			ListItemType.EXPLORE_SUGGESTION,
			exploreRecommendationItemAD(mangaClickListener),
		)
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.EXPLORE_SOURCE_LIST, exploreSourceListItemAD(clickListener))
		addDelegate(ListItemType.EXPLORE_SOURCE_GRID, exploreSourceGridItemAD(clickListener))
		addDelegate(ListItemType.HINT_EMPTY, emptyHintAD(listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
