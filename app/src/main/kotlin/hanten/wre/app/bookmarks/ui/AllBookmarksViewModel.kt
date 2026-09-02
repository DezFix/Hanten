package hanten.wre.app.bookmarks.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import hanten.wre.app.R
import hanten.wre.app.bookmarks.domain.Bookmark
import hanten.wre.app.bookmarks.domain.BookmarksRepository
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.ui.util.ReversibleAction
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.call
import hanten.wre.app.list.ui.model.EmptyState
import hanten.wre.app.list.ui.model.ListHeader
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.LoadingState
import hanten.wre.app.list.ui.model.toErrorState
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.reader.ui.PageSaveHelper
import javax.inject.Inject

@HiltViewModel
class AllBookmarksViewModel @Inject constructor(
	private val repository: BookmarksRepository,
) : BaseViewModel() {

	val onActionDone = MutableEventFlow<ReversibleAction>()

	val content: StateFlow<List<ListModel>> = repository.observeBookmarks()
		.map { list ->
			if (list.isEmpty()) {
				listOf(
					EmptyState(
						icon = R.drawable.ic_empty_favourites,
						textPrimary = R.string.no_bookmarks_yet,
						textSecondary = R.string.no_bookmarks_summary,
						actionStringRes = 0,
					),
				)
			} else {
				mapList(list)
			}
		}
		.catch { e -> emit(listOf(e.toErrorState(canRetry = false))) }
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, listOf(LoadingState))

	fun removeBookmarks(ids: Set<Long>) {
		launchJob(Dispatchers.IO) {
			val handle = repository.removeBookmarks(ids)
			onActionDone.call(ReversibleAction(R.string.bookmarks_removed, handle))
		}
	}

	fun savePages(pageSaveHelper: PageSaveHelper, ids: Set<Long>) {
		launchLoadingJob(Dispatchers.IO) {
			val tasks = content.value.mapNotNull {
				if (it !is Bookmark || it.pageId !in ids) return@mapNotNull null
				PageSaveHelper.Task(
					manga = it.manga,
					chapterId = it.chapterId,
					pageNumber = it.page + 1,
					page = it.toMangaPage(),
				)
			}
			val dest = pageSaveHelper.save(tasks)
			val msg = if (dest.size == 1) R.string.page_saved else R.string.pages_saved
			onActionDone.call(ReversibleAction(msg, null))
		}
	}

	private fun mapList(data: Map<Manga, List<Bookmark>>): List<ListModel> {
		val result = ArrayList<ListModel>(data.values.sumOf { it.size + 1 })
		for ((manga, bookmarks) in data) {
			result.add(ListHeader(manga.title, R.string.more, manga))
			result.addAll(bookmarks)
		}
		return result
	}
}
