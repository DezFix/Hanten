package hanten.wre.app.search.domain

import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.SortOrder

data class SearchResults(
	val listFilter: MangaListFilter,
	val sortOrder: SortOrder,
	val manga: List<Manga>,
)
