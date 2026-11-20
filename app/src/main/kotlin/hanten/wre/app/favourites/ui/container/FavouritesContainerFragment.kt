package hanten.wre.app.favourites.ui.container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.hilt.android.AndroidEntryPoint
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
		actionModeDelegate.addListener(this)
		viewModel.folders.observe(viewLifecycleOwner) { adapter.items = it }
		viewModel.isFoldersMode.observe(viewLifecycleOwner, ::onModeChanged)
		addMenuProvider(FavouritesContainerMenuProvider(router))
	}

	override fun onDestroyView() {
		actionModeDelegate.removeListener(this)
		super.onDestroyView()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onButtonChecked(group: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean) {
		if (isChecked) {
			viewModel.setFoldersMode(checkedId == R.id.button_mode_folders)
		}
	}

	override fun onFolderClick(category: FavouriteCategory) = router.openFavorites(category)

	override fun onFolderLongClick(category: FavouriteCategory): Boolean {
		router.openFavoriteCategoryEdit(category.id)
		return true
	}

	override fun onNewFolderClick() = router.openFavoriteCategoryCreate()

	override fun onActionModeStarted(mode: ActionMode) {
		viewBinding?.toggleMode?.isEnabled = false
	}

	override fun onActionModeFinished(mode: ActionMode) {
		viewBinding?.toggleMode?.isEnabled = true
	}

	private fun onModeChanged(isFolders: Boolean) {
		val binding = viewBinding ?: return
		binding.recyclerFolders.isVisible = isFolders
		binding.containerAll.isVisible = !isFolders
		if (!isFolders && childFragmentManager.findFragmentByTag(TAG_ALL) == null) {
			childFragmentManager.commit {
				replace(R.id.container_all, FavouritesListFragment.newInstance(FavouritesListFragment.NO_ID), TAG_ALL)
			}
		}
	}

	private companion object {

		const val TAG_ALL = "all_titles"
	}
}
