package hanten.wre.app.favourites.ui.list

import hanten.wre.app.databinding.SheetLibrarySortBinding
import hanten.wre.app.core.util.ext.observe
import com.google.android.material.chip.Chip
import com.google.android.material.slider.TickVisibilityMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.ui.list.ListSelectionController
import hanten.wre.app.core.util.ext.sortedByOrdinal
import hanten.wre.app.core.util.ext.withArgs
import hanten.wre.app.databinding.FragmentListBinding
import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.list.ui.MangaListFragment

@AndroidEntryPoint
class FavouritesListFragment : MangaListFragment(), PopupMenu.OnMenuItemClickListener {

	override val viewModel by viewModels<FavouritesListViewModel>()

	override val isSwipeRefreshEnabled = false

	val categoryId
		get() = viewModel.categoryId

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.recyclerView.isVP2BugWorkaroundEnabled = true
		viewModel.sortOrder.observe(viewLifecycleOwner) { order ->
			(parentFragment as? SortOrderListener)?.onSortOrderChanged(order)
		}
	}

	/** Hanten 3.0 sort sheet: every sort order as a chip, plus the cover size. */
	fun showSortSheet() {
		val context = context ?: return
		val sheetBinding = SheetLibrarySortBinding.inflate(layoutInflater)
		val dialog = BottomSheetDialog(context)
		val current = viewModel.sortOrder.value
		for (order in ListSortOrder.FAVORITES.sortedByOrdinal()) {
			val chip = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
				setText(order.titleResId)
				isCheckable = true
				isChecked = order == current
				setOnClickListener {
					viewModel.setSortOrder(order)
					dialog.dismiss()
				}
			}
			sheetBinding.chipsSort.addView(chip)
		}
		sheetBinding.sliderGridSize.value = settings.gridSize.toFloat().coerceIn(
			sheetBinding.sliderGridSize.valueFrom,
			sheetBinding.sliderGridSize.valueTo,
		)
		sheetBinding.sliderGridSize.tickVisibilityMode = TickVisibilityMode.TICK_VISIBILITY_HIDDEN
		sheetBinding.sliderGridSize.addOnChangeListener { _, value, fromUser ->
			if (fromUser) settings.gridSize = value.toInt()
		}
		dialog.setContentView(sheetBinding.root)
		dialog.show()
	}

	fun interface SortOrderListener {

		fun onSortOrderChanged(order: ListSortOrder?)
	}

	override fun onScrolledToEnd() = viewModel.requestMoreItems()

	override fun onEmptyActionClick() = viewModel.clearFilter()

	override fun onFilterClick(view: View?) {
		val menu = PopupMenu(view?.context ?: return, view)
		menu.setOnMenuItemClickListener(this)
		val orders = ListSortOrder.FAVORITES.sortedByOrdinal()
		for ((i, item) in orders.withIndex()) {
			menu.menu.add(Menu.NONE, Menu.NONE, i, item.titleResId)
		}
		menu.show()
	}

	override fun onMenuItemClick(item: MenuItem): Boolean {
		val order = ListSortOrder.FAVORITES.sortedByOrdinal().getOrNull(item.order) ?: return false
		viewModel.setSortOrder(order)
		return true
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_favourites, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_remove -> {
				viewModel.removeFromFavourites(selectedItemsIds)
				mode?.finish()
				true
			}

			R.id.action_mark_current -> {
				val itemsSnapshot = selectedItems
				MaterialAlertDialogBuilder(context ?: return false)
					.setTitle(item.title)
					.setMessage(R.string.mark_as_completed_prompt)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok) { _, _ ->
						viewModel.markAsRead(itemsSnapshot)
						mode?.finish()
					}.show()
				true
			}

			else -> super.onActionItemClicked(controller, mode, item)
		}
	}

	companion object {

		const val NO_ID = 0L

		fun newInstance(categoryId: Long) = FavouritesListFragment().withArgs(1) {
			putLong(AppRouter.KEY_ID, categoryId)
		}
	}
}
