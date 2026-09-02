package hanten.wre.app.search.ui.suggestion

import android.text.TextWatcher
import android.widget.TextView
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.search.domain.SearchKind

interface SearchSuggestionListener : TextWatcher, TextView.OnEditorActionListener {

	fun onMangaClick(manga: Manga)

	fun onQueryClick(query: String, kind: SearchKind, submit: Boolean)

	fun onSourceToggle(source: MangaSource, isEnabled: Boolean)

	fun onSourceClick(source: MangaSource)

	fun onTagClick(tag: MangaTag)
}
