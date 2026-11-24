package hanten.wre.app.favourites.ui.container

import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.core.util.ext.getThemeColor
import androidx.core.text.inSpans
import androidx.annotation.StringRes
import android.text.style.ForegroundColorSpan
import android.text.SpannableStringBuilder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import hanten.wre.app.favourites.ui.FolderColorPicker
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.core.prefs.ListMode
import hanten.wre.app.R
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.ui.BaseFragment
import hanten.wre.app.core.ui.util.ActionModeListener
import hanten.wre.app.core.ui.util.RecyclerViewOwner
import hanten.wre.app.core.util.ext.addMenuProvider
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.databinding.FragmentFavouritesContainerBinding
import hanten.wre.app.favourites.ui.list.FavouritesListFragment

/**
 * Library tab: a switch between folders (cards with covers; a folder opens its own list) and
 * all titles in one list. Folders are created, renamed and hidden through the existing category editor.
 */
@AndroidEntryPoint
class FavouritesContainerFragment : BaseFragment<FragmentFavouritesContainerBinding>(),
	ActionModeListener,
	RecyclerViewOwner,
	LibraryFolderListener,
	FavouritesListFragment.SortOrderListener,
	MaterialButtonToggleGroup.OnButtonCheckedListener {

	private val viewModel: FavouritesContainerViewModel by viewModels()

	override val recyclerView: RecyclerView?
		get() = if (viewModel.isFoldersMode.value) {
			viewBinding?.recyclerFolders
		} else {
			(childFragmentManager.findFragmentByTag(TAG_ALL) as? RecyclerViewOwner)?.recyclerView
		}

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentFavouritesContainerBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentFavouritesContainerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val adapter = LibraryFoldersAdapter(this)
		binding.recyclerFolders.layoutManager = GridLayoutManager(
			binding.root.context,
			resources.getInteger(R.integer.library_folder_columns),
		)
		binding.recyclerFolders.adapter = adapter
		binding.toggleMode.check(
			if (viewModel.isFoldersMode.value) R.id.button_mode_folders else R.id.button_mode_all,
		)
		binding.toggleMode.addOnButtonCheckedListener(this)
		binding.toggleView.check(
			when (viewModel.listMode) {
				ListMode.GRID -> R.id.button_view_grid
				ListMode.LIST -> R.id.button_view_list
				ListMode.DETAILED_LIST -> R.id.button_view_detailed
			},
		)
		binding.toggleView.addOnButtonCheckedListener(this)
		actionModeDelegate.addListener(this)
		viewModel.folders.observe(viewLifecycleOwner) { adapter.items = it }
		viewModel.isFoldersMode.observe(viewLifecycleOwner, ::onModeChanged)
		addMenuProvider(FavouritesContainerMenuProvider(router, ::onNewFolderClick))
		binding.buttonSort.setOnClickListener {
			(childFragmentManager.findFragmentByTag(TAG_ALL) as? FavouritesListFragment)?.showSortSheet()
		}
		viewModel.foldersCount.observe(viewLifecycleOwner) { count ->
			binding.buttonModeFolders.text = segmentText(R.string.library_folders, count)
		}
		viewModel.titlesCount.observe(viewLifecycleOwner) { count ->
			binding.buttonModeAll.text = segmentText(R.string.library_all_titles, count)
		}
	}

	override fun onDestroyView() {
		actionModeDelegate.removeListener(this)
		super.onDestroyView()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onButtonChecked(group: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean) {
		if (!isChecked) return
		when (checkedId) {
			R.id.button_view_grid -> viewModel.listMode = ListMode.GRID
			R.id.button_view_list -> viewModel.listMode = ListMode.LIST
			R.id.button_view_detailed -> viewModel.listMode = ListMode.DETAILED_LIST
			else -> viewModel.setFoldersMode(checkedId == R.id.button_mode_folders)
		}
	}

	override fun onFolderClick(category: FavouriteCategory) = router.openFavorites(category)

	override fun onFolderLongClick(category: FavouriteCategory): Boolean {
		router.openFavoriteCategoryEdit(category.id)
		return true
	}

	/** Quick folder creation; everything else (order, visibility) stays in the folder editor (long press). */
	override fun onNewFolderClick() {
		val context = context ?: return
		val padding = resources.getDimensionPixelSize(R.dimen.margin_normal)
		val input = TextInputEditText(context).apply {
			hint = getString(R.string.library_folder_name)
			setSingleLine()
		}
		val track = MaterialSwitch(context).apply {
			setText(R.string.library_folder_track)
			isChecked = true
		}
		val colorsRow = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			setPadding(0, padding / 2, 0, padding / 4)
		}
		val colorPicker = FolderColorPicker(colorsRow)
		val colorsTitle = TextView(context).apply {
			setText(R.string.folder_color)
			setPadding(0, padding, 0, 0)
		}
		val content = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, padding / 2, padding, 0)
			addView(input)
			addView(track)
			addView(colorsTitle)
			addView(HorizontalScrollView(context).apply {
				isHorizontalScrollBarEnabled = false
				addView(colorsRow)
			})
		}
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.library_new_folder)
			.setView(content)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.library_create) { _, _ ->
				viewModel.createFolder(input.text?.toString().orEmpty(), track.isChecked, colorPicker.color)
			}
			.show()
	}

	override fun onActionModeStarted(mode: ActionMode) {
		viewBinding?.toggleMode?.isEnabled = false
		viewBinding?.toggleView?.isEnabled = false
	}

	override fun onActionModeFinished(mode: ActionMode) {
		viewBinding?.toggleMode?.isEnabled = true
		viewBinding?.toggleView?.isEnabled = true
	}

	private fun onModeChanged(isFolders: Boolean) {
		val binding = viewBinding ?: return
		binding.recyclerFolders.isVisible = isFolders
		binding.containerAll.isVisible = !isFolders
		binding.layoutListControls.isVisible = !isFolders
		if (!isFolders && childFragmentManager.findFragmentByTag(TAG_ALL) == null) {
			childFragmentManager.commit {
				replace(R.id.container_all, FavouritesListFragment.newInstance(FavouritesListFragment.NO_ID), TAG_ALL)
			}
		}
	}

	private companion object {

		const val TAG_ALL = "all_titles"
	}

	override fun onSortOrderChanged(order: ListSortOrder?) {
		viewBinding?.buttonSort?.text = getString(order?.titleResId ?: R.string.sort_order)
	}

	/** "Folders 5" with the number in a quieter color, like the mockup's switch. */
	private fun segmentText(@StringRes title: Int, count: Int): CharSequence {
		val muted = requireContext().getThemeColor(android.R.attr.textColorSecondary)
		return SpannableStringBuilder(getString(title)).append("  ").inSpans(ForegroundColorSpan(muted)) {
			append(count.toString())
		}
	}
}
