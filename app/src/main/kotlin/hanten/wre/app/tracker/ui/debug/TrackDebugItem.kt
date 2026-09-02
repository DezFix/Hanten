package hanten.wre.app.tracker.ui.debug

import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.parsers.model.Manga
import java.time.Instant

data class TrackDebugItem(
	val manga: Manga,
	val lastChapterId: Long,
	val newChapters: Int,
	val lastCheckTime: Instant?,
	val lastChapterDate: Instant?,
	val lastResult: Int,
	val lastError: String?,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is TrackDebugItem && other.manga.id == manga.id
	}
}
