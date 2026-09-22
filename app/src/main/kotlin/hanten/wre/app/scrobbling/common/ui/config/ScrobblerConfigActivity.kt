package hanten.wre.app.scrobbling.common.ui.config

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.exceptions.resolve.SnackbarErrorObserver
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.util.ext.consumeAllSystemBarsInsets
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.core.util.ext.showOrHide
import hanten.wre.app.core.util.ext.systemBarsInsets
import hanten.wre.app.databinding.ActivityScrobblerConfigBinding
import hanten.wre.app.list.ui.adapter.TypedListSpacingDecoration
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerUser
import hanten.wre.app.scrobbling.common.domain.model.ScrobblingInfo
import hanten.wre.app.scrobbling.shikimori.domain.ShikimoriImportUseCase
import hanten.wre.app.scrobbling.shikimori.domain.ShikimoriExportUseCase
import hanten.wre.app.scrobbling.common.ui.config.adapter.ScrobblingMangaAdapter
import androidx.appcompat.R as appcompatR

@AndroidEntryPoint
class ScrobblerConfigActivity : BaseActivity<ActivityScrobblerConfigBinding>(),
	OnListItemClickListener<ScrobblingInfo>, View.OnClickListener {

	private val viewModel: ScrobblerConfigViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityScrobblerConfigBinding.inflate(layoutInflater))
		setTitle(viewModel.titleResId)
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)

		val listAdapter = ScrobblingMangaAdapter(this)
		with(viewBinding.recyclerView) {
			adapter = listAdapter
			setHasFixedSize(true)
			val decoration = TypedListSpacingDecoration(context, false)
			addItemDecoration(decoration)
		}
		viewBinding.imageViewAvatar.setOnClickListener(this)

		viewModel.content.observe(this, listAdapter)
		viewModel.user.observe(this, this::onUserChanged)
		viewModel.isLoading.observe(this, this::onLoadingStateChanged)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
		viewModel.onLoggedOut.observeEvent(this) {
			finishAfterTransition()
		}
		viewModel.onImportDone.observeEvent(this, ::showImportResult)
		viewModel.onExportDone.observeEvent(this, ::showExportResult)
		addMenuProvider(ImportMenuProvider())

		processIntent(intent)
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		processIntent(intent)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val basePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)
		viewBinding.appbar.updatePadding(
			top = barsInsets.top,
			left = barsInsets.left,
			right = barsInsets.right,
		)
		viewBinding.recyclerView.setPadding(
			barsInsets.left + basePadding,
			barsInsets.top + basePadding,
			barsInsets.right + basePadding,
			barsInsets.bottom + basePadding,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onItemClick(item: ScrobblingInfo, view: View) {
		router.openDetails(item.mangaId)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.imageView_avatar -> showUserDialog()
		}
	}

	private fun processIntent(intent: Intent) {
		if (intent.action == Intent.ACTION_VIEW) {
			val uri = intent.data ?: return
			val code = uri.getQueryParameter("code")
			if (!code.isNullOrEmpty()) {
				viewModel.onAuthCodeReceived(code)
			}
		}
	}

	private fun onUserChanged(user: ScrobblerUser?) {
		if (user == null) {
			viewBinding.imageViewAvatar.disposeImage()
			viewBinding.imageViewAvatar.setImageResource(appcompatR.drawable.abc_ic_menu_overflow_material)
			return
		}
		viewBinding.imageViewAvatar.setImageAsync(user.avatar)
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		viewBinding.progressBar.showOrHide(isLoading)
	}

	private fun showImportResult(result: ShikimoriImportUseCase.ImportResult) {
		val skipped = result.skipped.take(MAX_SKIPPED_NAMES).joinToString(separator = "\n")
		val message = StringBuilder(getString(R.string.scrobbler_import_done, result.imported, result.skipped.size))
		if (skipped.isNotEmpty()) {
			message.append("\n\n").append(skipped)
			if (result.skipped.size > MAX_SKIPPED_NAMES) {
				message.append("\n… (+${result.skipped.size - MAX_SKIPPED_NAMES})")
			}
		}
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.scrobbler_import)
			.setMessage(message.toString())
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}

	private fun showExportResult(result: ShikimoriExportUseCase.ExportResult) {
		val skipped = result.skipped.take(MAX_SKIPPED_NAMES).joinToString(separator = "\n")
		val message = StringBuilder(getString(R.string.scrobbler_export_done, result.exported, result.skipped.size))
		if (skipped.isNotEmpty()) {
			message.append("\n\n").append(skipped)
			if (result.skipped.size > MAX_SKIPPED_NAMES) {
				message.append("\n… (+${result.skipped.size - MAX_SKIPPED_NAMES})")
			}
		}
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.scrobbler_export)
			.setMessage(message.toString())
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}

	private inner class ImportMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_scrobbler_config, menu)
		}

		override fun onPrepareMenu(menu: Menu) {
			val supported = viewModel.isImportSupported
			menu.findItem(R.id.action_import)?.isVisible = supported
			menu.findItem(R.id.action_export)?.isVisible = supported
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			when (menuItem.itemId) {
				R.id.action_import -> {
					viewModel.startImport(getString(R.string.scrobbler_import_category))
					return true
				}

				R.id.action_export -> {
					viewModel.startExport()
					return true
				}
			}
			return false
		}
	}
	private fun showUserDialog() {
		MaterialAlertDialogBuilder(this)
			.setTitle(title)
			.setMessage(getString(R.string.logged_in_as, viewModel.user.value?.nickname))
			.setNegativeButton(R.string.close, null)
			.setPositiveButton(R.string.logout) { _, _ ->
				viewModel.logout()
			}.show()
	}

	companion object {
		const val HOST_SHIKIMORI_AUTH = "shikimori-auth"
		const val HOST_ANILIST_AUTH = "anilist-auth"
		const val HOST_MAL_AUTH = "mal-auth"

		private const val MAX_SKIPPED_NAMES = 15
	}
}
