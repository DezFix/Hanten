package hanten.wre.app.reader.ui

import hanten.wre.app.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.parsers.model.MangaChapter
import hanten.wre.app.reader.ui.pager.ReaderPage

interface ReaderNavigationCallback {

	fun onPageSelected(page: ReaderPage): Boolean

	fun onChapterSelected(chapter: MangaChapter): Boolean

	fun onBookmarkSelected(bookmark: Bookmark): Boolean = onPageSelected(
		ReaderPage(bookmark.toMangaPage(), bookmark.page, bookmark.chapterId),
	)
}
