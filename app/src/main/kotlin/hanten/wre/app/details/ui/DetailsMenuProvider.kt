package hanten.wre.app.details.ui

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hanten.wre.app.R
import hanten.wre.app.core.model.LocalMangaSource
import hanten.wre.app.core.model.isLocal
import hanten.wre.app.core.model.isNovel
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.os.AppShortcutManager
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.core.ui.dialog.buildAlertDialog
import hanten.wre.app.core.util.ext.isHttpUrl
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.novel.data.offline.NovelOfflineRepository
import hanten.wre.app.novel.ui.downloads.NovelDownloadsActivity
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
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
				if (manga.isNovel) {
					showNovelOfflineDialog(manga)
				} else {
					router.showDownloadDialog(manga, snackbarHost)
				}
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

	private fun offlineEntryPoint(): NovelOfflineEntryPoint {
		return EntryPointAccessors.fromApplication(
			activity.applicationContext,
			NovelOfflineEntryPoint::class.java,
		)
	}

	private fun showNovelOfflineDialog(manga: Manga) {
		val chapters = viewModel.mangaDetails.value?.allChapters.orEmpty()
		val repository = offlineEntryPoint().novelOfflineRepository()
		activity.lifecycleScope.launch {
			val cachedCount = withContext(Dispatchers.IO) {
				chapters.count { chapter -> repository.isChapterCached(manga.id, chapter.id) }
			}
			buildAlertDialog(activity) {
				setTitle(R.string.novel_offline_title)
				if (chapters.isEmpty()) {
					setMessage(R.string.novel_offline_no_chapters)
					setPositiveButton(R.string.novel_downloads) { _, _ ->
						openNovelDownloads()
					}
				} else {
					setMessage(
						activity.getString(
							R.string.novel_offline_cached_chapters,
							cachedCount,
							chapters.size,
						),
					)
					setPositiveButton(R.string.novel_offline_download_all) { _, _ ->
						cacheNovelChapters(manga, chapters, repository)
					}
					setNeutralButton(R.string.novel_downloads) { _, _ ->
						openNovelDownloads()
					}
					setNegativeButton(android.R.string.cancel, null)
				}
			}.show()
		}
	}

	private fun cacheNovelChapters(
		manga: Manga,
		chapters: List<MangaChapter>,
		repository: NovelOfflineRepository,
	) {
		activity.lifecycleScope.launch {
			val factory = offlineEntryPoint().mangaRepositoryFactory()
			val sourceRepository = runCatchingCancellable {
				factory.create(manga.source)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrNull()
			if (sourceRepository == null) {
				Snackbar.make(snackbarHost, R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
				return@launch
			}

			var progressBar: Snackbar? = null
			repository.cacheNovel(
				manga = manga,
				chapters = chapters,
				fetchHtml = { chapter -> fetchChapterHtml(sourceRepository, chapter) },
				onProgress = { done, total ->
					activity.runOnUiThread {
						val message = activity.getString(R.string.novel_offline_progress, done, total)
						val current = progressBar ?: Snackbar.make(
							snackbarHost,
							message,
							Snackbar.LENGTH_INDEFINITE,
						).also {
							it.show()
						}
						progressBar = current
						current.setText(message)
					}
				},
			)
			progressBar?.dismiss()

			val cachedCount = withContext(Dispatchers.IO) {
				chapters.count { chapter -> repository.isChapterCached(manga.id, chapter.id) }
			}
			Snackbar.make(
				snackbarHost,
				activity.getString(R.string.novel_offline_done, cachedCount),
				Snackbar.LENGTH_LONG,
			).setAction(R.string.novel_downloads) {
				openNovelDownloads()
			}.show()
		}
	}

	private suspend fun fetchChapterHtml(
		repository: MangaRepository,
		chapter: MangaChapter,
	): String? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val pages = repository.getPages(chapter)
			buildString {
				for (page in pages) {
					val content = page.url.trim()
					if (content.startsWith("<")) {
						append(content)
					} else if (content.isNotEmpty()) {
						append("<p>").append(content).append("</p>")
					}
				}
			}
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()?.takeIf { it.isNotBlank() }
	}

	private fun openNovelDownloads() {
		activity.startActivity(Intent(activity, NovelDownloadsActivity::class.java))
	}
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NovelOfflineEntryPoint {

	fun novelOfflineRepository(): NovelOfflineRepository

	fun mangaRepositoryFactory(): MangaRepository.Factory
}
