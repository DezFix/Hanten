package hanten.wre.app.novel.ui.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import hanten.wre.app.R
import hanten.wre.app.core.util.FileSize
import hanten.wre.app.databinding.ItemNovelDownloadBinding
import hanten.wre.app.databinding.ItemNovelDownloadChapterBinding
import hanten.wre.app.novel.data.offline.CachedChapter
import hanten.wre.app.novel.data.offline.CachedNovel

sealed interface NovelDownloadsItem {

	data class Novel(val novel: CachedNovel, val isExpanded: Boolean) : NovelDownloadsItem

	data class Chapter(val mangaId: Long, val chapter: CachedChapter) : NovelDownloadsItem
}

class NovelDownloadsAdapter(
	private val onNovelClick: (CachedNovel) -> Unit,
	private val onNovelDelete: (CachedNovel) -> Unit,
	private val onChapterDelete: (mangaId: Long, chapter: CachedChapter) -> Unit,
) : ListAdapter<NovelDownloadsItem, RecyclerView.ViewHolder>(DiffCallback) {

	override fun getItemViewType(position: Int): Int = when (getItem(position)) {
		is NovelDownloadsItem.Novel -> TYPE_NOVEL
		is NovelDownloadsItem.Chapter -> TYPE_CHAPTER
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return when (viewType) {
			TYPE_NOVEL -> NovelViewHolder(ItemNovelDownloadBinding.inflate(inflater, parent, false))
			else -> ChapterViewHolder(ItemNovelDownloadChapterBinding.inflate(inflater, parent, false))
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is NovelDownloadsItem.Novel -> (holder as NovelViewHolder).bind(item)
			is NovelDownloadsItem.Chapter -> (holder as ChapterViewHolder).bind(item)
		}
	}

	private inner class NovelViewHolder(
		private val binding: ItemNovelDownloadBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: NovelDownloadsItem.Novel) {
			val novel = item.novel
			val context = binding.root.context
			binding.textViewTitle.text = novel.title
			binding.textViewDetails.text = context.getString(
				R.string.novel_downloads_novel_details,
				novel.chapterCount,
				FileSize.BYTES.format(context, novel.sizeBytes),
				novel.source,
			)
			binding.buttonExpand.rotation = if (item.isExpanded) 180f else 0f
			binding.buttonExpand.contentDescription = context.getString(
				if (item.isExpanded) R.string.collapse else R.string.expand,
			)
			binding.root.setOnClickListener { onNovelClick(novel) }
			binding.buttonExpand.setOnClickListener { onNovelClick(novel) }
			binding.buttonDelete.setOnClickListener { onNovelDelete(novel) }
		}
	}

	private inner class ChapterViewHolder(
		private val binding: ItemNovelDownloadChapterBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: NovelDownloadsItem.Chapter) {
			val context = binding.root.context
			binding.textViewTitle.text = item.chapter.title
			binding.textViewSize.text = FileSize.BYTES.format(context, item.chapter.sizeBytes)
			binding.buttonDelete.setOnClickListener {
				onChapterDelete(item.mangaId, item.chapter)
			}
		}
	}

	private object DiffCallback : DiffUtil.ItemCallback<NovelDownloadsItem>() {

		override fun areItemsTheSame(oldItem: NovelDownloadsItem, newItem: NovelDownloadsItem): Boolean {
			return when {
				oldItem is NovelDownloadsItem.Novel && newItem is NovelDownloadsItem.Novel ->
					oldItem.novel.id == newItem.novel.id

				oldItem is NovelDownloadsItem.Chapter && newItem is NovelDownloadsItem.Chapter ->
					oldItem.mangaId == newItem.mangaId && oldItem.chapter.chapterId == newItem.chapter.chapterId

				else -> false
			}
		}

		override fun areContentsTheSame(oldItem: NovelDownloadsItem, newItem: NovelDownloadsItem): Boolean {
			return oldItem == newItem
		}
	}

	private companion object {
		private const val TYPE_NOVEL = 0
		private const val TYPE_CHAPTER = 1
	}
}
