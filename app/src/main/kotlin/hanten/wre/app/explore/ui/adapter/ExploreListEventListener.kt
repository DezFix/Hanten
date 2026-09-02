package hanten.wre.app.explore.ui.adapter

import android.view.View
import hanten.wre.app.list.ui.adapter.ListHeaderClickListener
import hanten.wre.app.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
