package hanten.wre.app.favourites.ui.container

import android.content.res.ColorStateList
import androidx.core.graphics.drawable.toDrawable
import androidx.appcompat.R as appcompatR
import androidx.core.view.isVisible
import com.google.android.material.R as materialR
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.util.ext.getThemeColor
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
	// Empty slots stay visible as tiles, like in the mockup (the default fallback matches the card color)
	val emptyTile = context.getThemeColor(materialR.attr.colorSurfaceContainerHighest).toDrawable()
	arrayOf(binding.imageViewCover1, binding.imageViewCover2, binding.imageViewCover3).forEach {
		it.fallbackDrawable = emptyTile
		// a cover that failed to load is just an empty slot here, not a dark red tile
		it.errorDrawable = emptyTile
	}
	val accent = context.getThemeColor(appcompatR.attr.colorPrimary)
	val strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
	itemView.setOnClickListener { listener.onFolderClick(item.category) }
	itemView.setOnLongClickListener { listener.onFolderLongClick(item.category) }

	bind {
		binding.textViewTitle.text = item.category.title
		// Folder color: tinted folder icon and a thin frame; no color = theme accent, no frame
		val color = item.category.color
		binding.imageViewFolder.imageTintList = ColorStateList.valueOf(if (color != 0) color else accent)
		binding.root.strokeWidth = if (color != 0) strokeWidth else 0
		if (color != 0) binding.root.setStrokeColor(ColorStateList.valueOf(color))
		binding.textViewCount.text = item.mangaCount.toString()
		// Bell = new chapters are checked for this folder
		binding.imageViewTracking.isVisible = item.isTrackerEnabled
		binding.imageViewCover1.setImageAsync(item.covers.getOrNull(0))
		binding.imageViewCover2.setImageAsync(item.covers.getOrNull(1))
		binding.imageViewCover3.setImageAsync(item.covers.getOrNull(2))
	}
}

private fun newFolderAD(
	listener: LibraryFolderListener,
) = adapterDelegateViewBinding<NewFolderItem, ListModel, ItemLibraryFolderNewBinding>(
	{ inflater, parent -> ItemLibraryFolderNewBinding.inflate(inflater, parent, false) },
) {
	itemView.setOnClickListener { listener.onNewFolderClick() }
}
