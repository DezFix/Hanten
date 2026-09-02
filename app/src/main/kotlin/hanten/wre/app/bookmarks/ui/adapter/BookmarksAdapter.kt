package hanten.wre.app.bookmarks.ui.adapter

import android.content.Context
import hanten.wre.app.bookmarks.domain.Bookmark
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.ui.list.fastscroll.FastScroller
import hanten.wre.app.list.ui.adapter.ListHeaderClickListener
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.adapter.emptyStateListAD
import hanten.wre.app.list.ui.adapter.errorStateListAD
import hanten.wre.app.list.ui.adapter.listHeaderAD
import hanten.wre.app.list.ui.adapter.loadingFooterAD
import hanten.wre.app.list.ui.adapter.loadingStateAD
import hanten.wre.app.list.ui.model.ListModel

class BookmarksAdapter(
	clickListener: OnListItemClickListener<Bookmark>,
	headerClickListener: ListHeaderClickListener?,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.PAGE_THUMB, bookmarkLargeAD(clickListener))
		addDelegate(ListItemType.HEADER, listHeaderAD(headerClickListener))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(null))
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
