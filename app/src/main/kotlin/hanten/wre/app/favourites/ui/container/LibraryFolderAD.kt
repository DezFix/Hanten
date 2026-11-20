package hanten.wre.app.favourites.ui.container

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.databinding.ItemLibraryFolderBinding
import hanten.wre.app.databinding.ItemLibraryFolderNewBinding
import hanten.wre.app.favourites.ui.categories.adapter.CategoryListModel
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.model.ListModel

/** The "New folder" card at the end of the folders grid. */
data object NewFolderItem : ListModel {
	override fun areItemsTheSame(other: ListModel): Boolean = other === NewFolderItem
}

interface LibraryFolderListener {

	fun onFolderClick(category: FavouriteCategory)

	fun onFolderLongClick(category: FavouriteCategory): Boolean

	fun onNewFolderClick()
}

class LibraryFoldersAdapter(listener: LibraryFolderListener) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.LIBRARY_FOLDER, libraryFolderAD(listener))
		addDelegate(ListItemType.LIBRARY_FOLDER_NEW, newFolderAD(listener))
	}
}

private fun libraryFolderAD(
	listener: LibraryFolderListener,
) = adapterDelegateViewBinding<CategoryListModel, ListModel, ItemLibraryFolderBinding>(
	{ inflater, parent -> ItemLibraryFolderBinding.inflate(inflater, parent, false) },
) {
	itemView.setOnClickListener { listener.onFolderClick(item.category) }
	itemView.setOnLongClickListener { listener.onFolderLongClick(item.category) }

	bind {
		binding.textViewTitle.text = item.category.title
		binding.textViewCount.text = item.mangaCount.toString()
		// Bell = new chapters are checked for this folder
		binding.imageViewTracking.isVisible = item.isTrackerEnabled
		binding.coversView.setCoversAsync(item.covers)
	}
}

private fun newFolderAD(
	listener: LibraryFolderListener,
) = adapterDelegateViewBinding<NewFolderItem, ListModel, ItemLibraryFolderNewBinding>(
	{ inflater, parent -> ItemLibraryFolderNewBinding.inflate(inflater, parent, false) },
) {
	itemView.setOnClickListener { listener.onNewFolderClick() }
}
