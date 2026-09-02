package hanten.wre.app.details.ui.scrobbling

import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.list.ui.model.ListModel

class ScrollingInfoAdapter(
	router: AppRouter,
) : BaseListAdapter<ListModel>() {

	init {
		delegatesManager.addDelegate(scrobblingInfoAD(router))
	}
}
