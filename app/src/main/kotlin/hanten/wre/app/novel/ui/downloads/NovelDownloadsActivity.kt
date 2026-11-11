package hanten.wre.app.novel.ui.downloads

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.dialog.buildAlertDialog
import hanten.wre.app.core.util.FileSize
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.databinding.ActivityNovelDownloadsBinding
import hanten.wre.app.novel.data.offline.CachedChapter
import hanten.wre.app.novel.data.offline.CachedNovel

@AndroidEntryPoint
class NovelDownloadsActivity : BaseActivity<ActivityNovelDownloadsBinding>() {

	private val viewModel by viewModels<NovelDownloadsViewModel>()

	private lateinit var adapter: NovelDownloadsAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityNovelDownloadsBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		setTitle(R.string.novel_downloads)

		adapter = NovelDownloadsAdapter(
			onNovelClick = { novel -> viewModel.toggleExpanded(novel.id) },
			onNovelDelete = ::confirmDeleteNovel,
			onChapterDelete = ::confirmDeleteChapter,
			onCancelDownload = viewModel::cancelDownload,
		)
		viewBinding.recyclerView.adapter = adapter

		viewModel.state.observe(this) { state ->
			render(state)
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.refresh()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val spacing = resources.getDimensionPixelSize(R.dimen.list_spacing_large)
		viewBinding.appbar.updatePadding(
			left = bars.left,
			right = bars.right,
			top = bars.top,
		)
		viewBinding.textViewTotal.updatePadding(
			left = bars.left + spacing,
			right = bars.right + spacing,
		)
		viewBinding.recyclerView.updatePadding(
			left = bars.left,
			right = bars.right,
			bottom = bars.bottom + spacing,
		)
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
			.build()
	}

	private fun render(state: NovelDownloadsState) {
		val items = ArrayList<NovelDownloadsItem>(state.downloads.size + state.novels.size)
		for (download in state.downloads) {
			items += NovelDownloadsItem.Active(download)
		}
		for (novel in state.novels) {
			items += NovelDownloadsItem.Novel(novel, novel.id in state.expanded)
			if (novel.id in state.expanded) {
				state.chapters[novel.id].orEmpty().forEach { chapter ->
					items += NovelDownloadsItem.Chapter(novel.id, chapter)
				}
			}
		}
		adapter.submitList(items)

		val chaptersCount = state.novels.sumOf { it.chapterCount }
		viewBinding.textViewTotal.isVisible = state.novels.isNotEmpty()
		viewBinding.textViewTotal.text = getString(
			R.string.novel_downloads_total,
			chaptersCount,
			FileSize.BYTES.format(this, state.totalSizeBytes),
		)
		viewBinding.textViewEmpty.isVisible =
			state.novels.isEmpty() && state.downloads.isEmpty() && !state.isLoading
	}

	private fun confirmDeleteNovel(novel: CachedNovel) {
		buildAlertDialog(this) {
			setTitle(R.string.novel_downloads_delete_novel)
			setMessage(getString(R.string.novel_downloads_delete_novel_confirm, novel.title))
			setPositiveButton(R.string.delete) { _, _ ->
				viewModel.deleteNovel(novel.id)
			}
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}

	private fun confirmDeleteChapter(mangaId: Long, chapter: CachedChapter) {
		buildAlertDialog(this) {
			setTitle(R.string.delete_chapter)
			setMessage(getString(R.string.delete_chapter_confirm, chapter.title))
			setPositiveButton(R.string.delete) { _, _ ->
				viewModel.deleteChapter(mangaId, chapter.chapterId)
			}
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}
}
