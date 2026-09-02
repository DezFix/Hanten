package hanten.wre.app.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate
import hanten.wre.app.R
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.LoadingFooter

fun loadingFooterAD() = adapterDelegate<LoadingFooter, ListModel>(R.layout.item_loading_footer) {
}