package hanten.wre.app.favourites.domain

import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.db.entity.toManga
import hanten.wre.app.core.db.entity.toMangaTags
import hanten.wre.app.favourites.data.FavouriteManga
import hanten.wre.app.list.domain.ListFilterOption
import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.local.data.index.LocalMangaIndex
import hanten.wre.app.local.domain.LocalObserveMapper
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@Reusable
class LocalFavoritesObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<FavouriteManga, Manga>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(order, filterOptions, limit).mapToLocal()

	fun observeAll(
		categoryId: Long,
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(categoryId, order, filterOptions, limit).mapToLocal()

	override fun toManga(e: FavouriteManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: FavouriteManga, manga: Manga) = manga
}
