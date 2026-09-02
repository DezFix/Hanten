package hanten.wre.app.scrobbling.common.ui.config.adapter

import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.adapter.emptyStateListAD
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.scrobbling.common.domain.model.ScrobblingInfo

class ScrobblingMangaAdapter(
	clickListener: OnListItemClickListener<ScrobblingInfo>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.HEADER, scrobblingHeaderAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
		addDelegate(ListItemType.MANGA_SCROBBLING, scrobblingMangaAD(clickListener))
	}
}
