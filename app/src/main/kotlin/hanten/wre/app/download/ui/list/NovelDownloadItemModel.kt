package hanten.wre.app.download.ui.list

import hanten.wre.app.list.ui.ListModelDiffCallback
import hanten.wre.app.list.ui.model.ListModel
import java.util.UUID

data class NovelChapterItem(
	val id: Long,
	val title: String,
	val sizeBytes: Long,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NovelChapterItem && other.id == id
	}
}

data class NovelDownloadItemModel(
	val mangaId: Long,
	val title: String,
	val sourceName: String,
	val chapterCount: Int,
	val sizeBytes: Long,
	val workId: UUID?,
	val done: Int,
	val total: Int,
	val isExpanded: Boolean,
	val chapters: List<NovelChapterItem>,
) : ListModel {

	val isActive: Boolean
		get() = workId != null

	override fun areItemsTheSame(other: ListModel): Boolean {
		if (other !is NovelDownloadItemModel) {
			return false
		}
		return if (workId != null && other.workId != null) {
			workId == other.workId
		} else {
			mangaId == other.mangaId
		}
	}

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is NovelDownloadItemModel -> super.getChangePayload(previousState)
		isExpanded != previousState.isExpanded -> ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED
		else -> ListModelDiffCallback.PAYLOAD_PROGRESS_CHANGED
	}
}
