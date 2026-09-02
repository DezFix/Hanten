package hanten.wre.app.history.data

import dagger.Reusable
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.db.entity.toManga
import hanten.wre.app.core.db.entity.toMangaTags
import hanten.wre.app.history.domain.model.MangaWithHistory
import hanten.wre.app.list.domain.ListFilterOption
import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.local.data.index.LocalMangaIndex
import hanten.wre.app.local.domain.LocalObserveMapper
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@Reusable
class HistoryLocalObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<HistoryWithManga, MangaWithHistory>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	) = db.getHistoryDao().observeAll(order, filterOptions, limit).mapToLocal()

	override fun toManga(e: HistoryWithManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: HistoryWithManga, manga: Manga) = MangaWithHistory(
		manga = manga,
		history = e.history.toMangaHistory(),
	)
}
