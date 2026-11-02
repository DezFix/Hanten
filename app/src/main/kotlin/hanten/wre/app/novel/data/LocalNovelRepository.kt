package hanten.wre.app.novel.data

import hanten.wre.app.core.model.LocalNovelSource
import hanten.wre.app.core.parser.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaListFilterCapabilities
import hanten.wre.app.parsers.model.MangaListFilterOptions
import hanten.wre.app.parsers.model.MangaPage
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.model.SortOrder
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNovelRepository @Inject constructor(
	private val novelStorageManager: NovelStorageManager,
) : MangaRepository {

	override val source: MangaSource = LocalNovelSource

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.NEWEST,
		SortOrder.RELEVANCE,
	)

	override var defaultSortOrder: SortOrder = SortOrder.NEWEST

	override val filterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
	)

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> = withContext(Dispatchers.IO) {
		val all = novelStorageManager.getAllNovels()
		val query = filter?.query
		val filtered = if (!query.isNullOrBlank()) {
			all.filter { it.title.contains(query, ignoreCase = true) }
		} else {
			all
		}
		filtered.drop(offset)
	}

	override suspend fun getDetails(manga: Manga): Manga = manga

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return listOf(
			MangaPage(
				id = chapter.id,
				url = chapter.url,
				preview = null,
				source = LocalNovelSource,
			)
		)
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getRelated(seed: Manga): List<Manga> = emptyList()
}