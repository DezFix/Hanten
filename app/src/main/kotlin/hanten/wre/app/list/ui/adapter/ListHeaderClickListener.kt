package hanten.wre.app.list.ui.adapter

import android.view.View
import hanten.wre.app.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)
}
