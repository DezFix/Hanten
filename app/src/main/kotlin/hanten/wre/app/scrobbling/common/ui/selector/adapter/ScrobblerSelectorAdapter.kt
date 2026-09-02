package hanten.wre.app.scrobbling.common.ui.selector.adapter

import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.adapter.ListStateHolderListener
import hanten.wre.app.list.ui.adapter.loadingFooterAD
import hanten.wre.app.list.ui.adapter.loadingStateAD
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerManga

class ScrobblerSelectorAdapter(
	clickListener: OnListItemClickListener<ScrobblerManga>,
	stateHolderListener: ListStateHolderListener,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.MANGA_SCROBBLING, scrobblingMangaAD(clickListener))
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.HINT_EMPTY, scrobblerHintAD(stateHolderListener))
	}
}
