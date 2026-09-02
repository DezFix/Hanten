package hanten.wre.app.history.ui

import android.content.Context
import hanten.wre.app.core.ui.list.fastscroll.FastScroller
import hanten.wre.app.list.ui.adapter.MangaListAdapter
import hanten.wre.app.list.ui.adapter.MangaListListener
import hanten.wre.app.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : MangaListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
