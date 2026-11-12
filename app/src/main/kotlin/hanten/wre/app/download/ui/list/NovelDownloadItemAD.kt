package hanten.wre.app.download.ui.list

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.R
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.util.FileSize
import hanten.wre.app.core.util.ext.setContentDescriptionAndTooltip
import hanten.wre.app.databinding.ItemNovelDownloadBinding
import hanten.wre.app.databinding.ItemNovelDownloadChapterBinding
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.model.ListModel

fun novelDownloadItemAD(
	listener: DownloadItemListener,
) = adapterDelegateViewBinding<NovelDownloadItemModel, ListModel, ItemNovelDownloadBinding>(
	{ inflater, parent -> ItemNovelDownloadBinding.inflate(inflater, parent, false) },
) {

	val chaptersAdapter = BaseListAdapter<NovelChapterItem>()
		.addDelegate(
			ListItemType.NOVEL_DOWNLOAD_CHAPTER,
			novelDownloadChapterAD { chapter ->
				listener.onDeleteNovelChapterClick(item, chapter)
			},
		)

	binding.recyclerViewChapters.adapter = chaptersAdapter

	binding.buttonCancel.setOnClickListener { listener.onCancelNovelClick(item) }
	binding.buttonPause.setOnClickListener { listener.onPauseNovelClick(item) }
	binding.buttonShare.setOnClickListener { listener.onShareNovelClick(item) }
	binding.buttonRemove.setOnClickListener { listener.onHideNovelClick(item) }
	binding.buttonDelete.setOnClickListener { listener.onDeleteNovelClick(item) }
	binding.buttonExpand.setOnClickListener { listener.onExpandNovelClick(item) }
	binding.root.setOnClickListener { listener.onExpandNovelClick(item) }
	binding.root.setOnLongClickListener { listener.onNovelLongClick(item, it) }
	binding.root.setOnCreateContextMenuListener { _, view, _ -> listener.onNovelContextClick(item, view) }

	bind {
		val context = binding.root.context
		binding.textViewTitle.text = item.title.ifBlank {
			context.getString(R.string.unknown)
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.textViewStatus.setText(
			if (item.isActive) R.string.manga_downloading_ else R.string.download_complete,
		)
		binding.textViewDetails.text = context.getString(
			R.string.novel_downloads_novel_details,
			item.chapterCount,
			FileSize.BYTES.format(context, item.sizeBytes),
			item.sourceName,
		)

		binding.progressBar.isVisible = item.isActive
		binding.textViewProgress.isVisible = item.isActive
		binding.buttonCancel.isVisible = item.isActive
		binding.buttonPause.isVisible = item.isActive
		binding.buttonShare.isVisible = !item.isActive
		binding.buttonRemove.isVisible = !item.isActive
		binding.buttonDelete.isVisible = !item.isActive
		if (item.isActive) {
			binding.progressBar.isIndeterminate = item.total <= 0
			if (item.total > 0) {
				binding.progressBar.max = item.total
				binding.progressBar.setProgressCompat(item.done, true)
			}
			binding.textViewProgress.text = if (item.total > 0) {
				context.getString(R.string.novel_download_in_progress, item.done, item.total)
			} else {
				context.getString(R.string.novel_download_queued)
			}
		}

		binding.buttonExpand.isChecked = item.isExpanded
		binding.buttonExpand.isVisible = item.chapters.isNotEmpty()
		binding.buttonExpand.setContentDescriptionAndTooltip(
			if (item.isExpanded) R.string.collapse else R.string.expand,
		)
		binding.recyclerViewChapters.isVisible = item.isExpanded && item.chapters.isNotEmpty()
		chaptersAdapter.setItems(item.chapters)
	}
}

private fun novelDownloadChapterAD(
	onDeleteClick: (NovelChapterItem) -> Unit,
) = adapterDelegateViewBinding<NovelChapterItem, NovelChapterItem, ItemNovelDownloadChapterBinding>(
	{ inflater, parent -> ItemNovelDownloadChapterBinding.inflate(inflater, parent, false) },
) {

	binding.buttonDelete.setOnClickListener {
		onDeleteClick(item)
	}

	bind {
		binding.textViewTitle.text = item.title
		binding.textViewSize.text = FileSize.BYTES.format(context, item.sizeBytes)
	}
}
