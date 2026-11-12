package hanten.wre.app.details.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.MenuProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import hanten.wre.app.R
import hanten.wre.app.core.model.LocalMangaSource
import hanten.wre.app.core.model.MangaRelationType
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.core.model.isLocal
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.os.AppShortcutManager
import hanten.wre.app.core.ui.dialog.buildAlertDialog
import hanten.wre.app.core.util.ext.isHttpUrl
import hanten.wre.app.databinding.DialogRelationSearchBinding
import hanten.wre.app.databinding.ItemRelationCandidateBinding
import hanten.wre.app.details.data.RelatedEntry
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.util.runCatchingCancellable

class DetailsMenuProvider(
	private val activity: FragmentActivity,
	private val viewModel: DetailsViewModel,
	private val snackbarHost: View,
	private val appShortcutManager: AppShortcutManager,
) : MenuProvider, ActivityResultCallback<ActivityResult> {

	private val activityForResultLauncher = activity.registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
		this,
	)

	private val router: AppRouter
		get() = activity.router

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_details, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		val mangaDetails = viewModel.mangaDetails.value
		val manga = mangaDetails?.toManga()
		menu.findItem(R.id.action_share).isVisible = manga != null && AppRouter.isShareSupported(manga)
		menu.findItem(R.id.action_save).isVisible =
			manga?.source != null && !manga.isLocal
		menu.findItem(R.id.action_delete).isVisible = mangaDetails?.local != null
		menu.findItem(R.id.action_browser).isVisible = manga?.publicUrl?.isHttpUrl() == true
		menu.findItem(R.id.action_alternatives).isVisible = manga?.source != LocalMangaSource
		menu.findItem(R.id.action_relations).isVisible = viewModel.getMangaOrNull() != null
		menu.findItem(R.id.action_shortcut).isVisible = ShortcutManagerCompat.isRequestPinShortcutSupported(activity)
		menu.findItem(R.id.action_scrobbling).isVisible = viewModel.isScrobblingAvailable
		menu.findItem(R.id.action_online).isVisible = viewModel.remoteManga.value != null
		menu.findItem(R.id.action_stats).isVisible = viewModel.isStatsAvailable.value
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		val manga = viewModel.getMangaOrNull() ?: return false
		when (menuItem.itemId) {
			R.id.action_share -> {
				router.showShareDialog(manga)
			}

			R.id.action_delete -> {
				buildAlertDialog(activity) {
					setTitle(R.string.delete_manga)
					setMessage(activity.getString(R.string.text_delete_local_manga, manga.title))
					setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteLocal() }
					setNegativeButton(android.R.string.cancel, null)
				}.show()
			}

			R.id.action_save -> {
				router.showDownloadDialog(manga, snackbarHost)
			}

			R.id.action_browser -> {
				router.openBrowser(url = manga.publicUrl, source = manga.source, title = manga.title)
			}

			R.id.action_online -> {
				router.openDetails(viewModel.remoteManga.value ?: return false)
			}

			R.id.action_related -> {
				router.openSearch(manga.title)
			}

			R.id.action_alternatives -> {
				router.openAlternatives(manga)
			}

			R.id.action_relations -> {
				showRelationsDialog()
			}

			R.id.action_stats -> {
				router.showStatisticSheet(manga)
			}

			R.id.action_scrobbling -> {
				router.showScrobblingSelectorSheet(manga, null)
			}

			R.id.action_shortcut -> {
				activity.lifecycleScope.launch {
					if (!appShortcutManager.requestPinShortcut(manga)) {
						Snackbar.make(snackbarHost, R.string.operation_not_supported, Snackbar.LENGTH_SHORT)
							.show()
					}
				}
			}

			R.id.action_edit_override -> {
				val intent = AppRouter.overrideEditIntent(activity, manga)
				activityForResultLauncher.launch(intent)
			}

			else -> return false
		}
		return true
	}

	override fun onActivityResult(result: ActivityResult) {
		if (result.resultCode == Activity.RESULT_OK) {
			viewModel.reload()
		}
	}

	private fun showRelationsDialog() {
		val relations = viewModel.manualRelations.value
		buildAlertDialog(activity) {
			setTitle(R.string.relations)
			if (relations.isEmpty()) {
				setMessage(R.string.relation_none)
			} else {
				val items = relations.map {
					"${it.manga.title} — ${activity.getString(it.type.titleResId)}"
				}
				setItems(items.toTypedArray()) { _, which ->
					confirmRemoveRelation(relations[which])
				}
			}
			setPositiveButton(R.string.relation_add) { _, _ -> showRelationPicker() }
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}

	private fun confirmRemoveRelation(relation: RelatedEntry) {
		buildAlertDialog(activity) {
			setMessage(activity.getString(R.string.relation_remove_confirm, relation.manga.title))
			setPositiveButton(android.R.string.ok) { _, _ ->
				viewModel.removeRelation(relation.manga)
				Snackbar.make(snackbarHost, R.string.relation_removed, Snackbar.LENGTH_SHORT).show()
			}
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}

	private fun showRelationPicker() {
		val binding = DialogRelationSearchBinding.inflate(LayoutInflater.from(activity))
		val dialog = buildAlertDialog(activity) {
			setTitle(R.string.relation_pick)
			setView(binding.root)
			setNegativeButton(android.R.string.cancel, null)
		}
		val adapter = RelationCandidateAdapter { manga ->
			dialog.dismiss()
			showRelationTypeDialog(manga)
		}
		binding.recyclerView.adapter = adapter
		var searchJob: Job? = null
		fun search(query: String) {
			searchJob?.cancel()
			searchJob = activity.lifecycleScope.launch {
				delay(300)
				val result = runCatchingCancellable {
					viewModel.searchRelationsCandidates(query)
				}.getOrDefault(emptyList())
				adapter.submitList(result)
			}
		}
		binding.editTextQuery.doOnTextChanged { text, _, _, _ ->
			search(text?.toString()?.trim().orEmpty())
		}
		dialog.show()
		search(query = "")
		binding.editTextQuery.requestFocus()
	}

	private fun showRelationTypeDialog(manga: Manga) {
		val types = MangaRelationType.entries
		buildAlertDialog(activity) {
			setTitle(R.string.relation_type)
			setItems(types.map { activity.getString(it.titleResId) }.toTypedArray()) { _, which ->
				viewModel.addRelation(manga, types[which])
				Snackbar.make(snackbarHost, R.string.relation_added, Snackbar.LENGTH_SHORT).show()
			}
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}

	private class RelationCandidateAdapter(
		private val onPick: (Manga) -> Unit,
	) : ListAdapter<Manga, RelationCandidateAdapter.ViewHolder>(DiffCallback) {

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val binding = ItemRelationCandidateBinding.inflate(
				LayoutInflater.from(parent.context),
				parent,
				false,
			)
			return ViewHolder(binding)
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			holder.bind(getItem(position))
		}

		inner class ViewHolder(
			private val binding: ItemRelationCandidateBinding,
		) : RecyclerView.ViewHolder(binding.root) {

			fun bind(manga: Manga) {
				binding.root.title = manga.title
				binding.root.subtitle = manga.source.getTitle(binding.root.context)
				binding.root.setOnClickListener { onPick(manga) }
			}
		}

		private object DiffCallback : DiffUtil.ItemCallback<Manga>() {
			override fun areItemsTheSame(oldItem: Manga, newItem: Manga) = oldItem.id == newItem.id
			override fun areContentsTheSame(oldItem: Manga, newItem: Manga) = oldItem == newItem
		}
	}
}
