package hanten.wre.app.favourites.ui.categories

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.ui.list.OnListItemClickListener

interface FavouriteCategoriesListListener : OnListItemClickListener<FavouriteCategory?> {

	fun onDragHandleTouch(holder: RecyclerView.ViewHolder): Boolean

	fun onEditClick(item: FavouriteCategory, view: View)

	fun onShowAllClick(isChecked: Boolean)
}
