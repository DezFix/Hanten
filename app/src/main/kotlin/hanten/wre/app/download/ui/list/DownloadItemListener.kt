package hanten.wre.app.download.ui.list

import android.view.View
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.download.ui.list.chapters.DownloadChapter

interface DownloadItemListener : OnListItemClickListener<DownloadItemModel> {

	fun onCancelClick(item: DownloadItemModel)

	fun onPauseClick(item: DownloadItemModel)

	fun onResumeClick(item: DownloadItemModel)

	fun onSkipClick(item: DownloadItemModel)

	fun onSkipAllClick(item: DownloadItemModel)

	fun onExpandClick(item: DownloadItemModel)

	fun onDeleteChapterClick(item: DownloadItemModel, chapter: DownloadChapter)

	fun onCancelNovelClick(item: NovelDownloadItemModel)

	fun onExpandNovelClick(item: NovelDownloadItemModel)

	fun onDeleteNovelClick(item: NovelDownloadItemModel)

	fun onHideNovelClick(item: NovelDownloadItemModel)

	fun onNovelLongClick(item: NovelDownloadItemModel, view: View): Boolean

	fun onNovelContextClick(item: NovelDownloadItemModel, view: View): Boolean

	fun onDeleteNovelChapterClick(item: NovelDownloadItemModel, chapter: NovelChapterItem)
}
