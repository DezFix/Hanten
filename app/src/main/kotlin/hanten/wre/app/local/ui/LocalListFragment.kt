package hanten.wre.app.local.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import hanten.wre.app.R
import hanten.wre.app.core.model.LocalMangaSource
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.ui.list.ListSelectionController
import hanten.wre.app.core.ui.widgets.TipView
import hanten.wre.app.core.util.ShareHelper
import hanten.wre.app.core.util.ext.addMenuProvider
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.core.util.ext.toFileOrNull
import hanten.wre.app.core.util.ext.toUriOrNull
import hanten.wre.app.core.util.ext.tryLaunch
import hanten.wre.app.databinding.FragmentListBinding
import hanten.wre.app.filter.ui.FilterCoordinator
import hanten.wre.app.list.ui.MangaListFragment
import hanten.wre.app.remotelist.ui.MangaSearchMenuProvider
import hanten.wre.app.remotelist.ui.RemoteListFragment
import hanten.wre.app.settings.storage.RequestStorageManagerPermissionContract
import java.io.File

class LocalListFragment : MangaListFragment(), FilterCoordinator.Owner {

	private val permissionRequestLauncher = registerForActivityResult(
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			RequestStorageManagerPermissionContract()
		} else {
			ActivityResultContracts.RequestPermission()
		},
	) {
		if (it) {
			viewModel.onRefresh()
		}
	}

	private val novelFileLauncher = registerForActivityResult(
		ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (!uris.isNullOrEmpty()) {
			viewModel.importNovels(uris)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val args = arguments ?: Bundle(1)
		args.putString(
			RemoteListFragment.ARG_SOURCE,
			LocalMangaSource.name,
		) // required by FilterCoordinator
		arguments = args
	}

	override val viewModel by viewModels<LocalListViewModel>()

	override val filterCoordinator: FilterCoordinator
		get() = viewModel.filterCoordinator

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		addMenuProvider(LocalListMenuProvider(this, this::onEmptyActionClick))
		addMenuProvider(MangaSearchMenuProvider(filterCoordinator, viewModel))
		viewModel.onMangaRemoved.observeEvent(viewLifecycleOwner) { onItemRemoved() }
	}

	override fun onEmptyActionClick() {
		showImportDialog()
	}

	private fun showImportDialog() {
		val options = arrayOf(
			getString(R.string.add_folder),
			getString(R.string.select_files),
			getString(R.string.novel_import_file),
		)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string._import)
			.setItems(options) { _, which ->
				when (which) {
					2 -> novelFileLauncher.launch(NOVEL_MIME_TYPES)
					else -> router.showImportDialog()
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	override fun onFilterClick(view: View?) {
		router.showFilterSheet()
	}

	override fun onPrimaryButtonClick(tipView: TipView) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			permissionRequestLauncher.launch("")
		} else if (!permissionRequestLauncher.tryLaunch(Manifest.permission.READ_EXTERNAL_STORAGE)) {
			Snackbar.make(tipView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	override fun onSecondaryButtonClick(tipView: TipView) {
		router.openDirectoriesSettings()
	}

	override fun onScrolledToEnd() = viewModel.loadNextPage()

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu,
	): Boolean {
		menuInflater.inflate(R.menu.mode_local, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onActionItemClicked(
		controller: ListSelectionController,
		mode: ActionMode?,
		item: MenuItem,
	): Boolean {
		return when (item.itemId) {
			R.id.action_remove -> {
				showDeletionConfirm(selectedItemsIds, mode)
				true
			}

			R.id.action_share -> {
				val files = selectedItems.map {
					it.url.toUriOrNull()?.toFileOrNull() ?: File(it.url)
				}
				ShareHelper(requireContext()).shareCbz(files)
				mode?.finish()
				true
			}

			else -> super.onActionItemClicked(controller, mode, item)
		}
	}

	private fun showDeletionConfirm(ids: Set<Long>, mode: ActionMode?) {
		MaterialAlertDialogBuilder(context ?: return)
			.setTitle(R.string.delete_manga)
			.setMessage(getString(R.string.text_delete_local_manga_batch))
			.setPositiveButton(R.string.delete) { _, _ ->
				viewModel.delete(ids)
				mode?.finish()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun onItemRemoved() {
		Snackbar.make(
			requireViewBinding().recyclerView,
			R.string.removal_completed,
			Snackbar.LENGTH_SHORT,
		).show()
	}

	companion object {
		private val NOVEL_MIME_TYPES = arrayOf(
			"application/epub+zip",
			"application/pdf",
			"application/octet-stream",
			"*/*",
		)
	}
}
