package hanten.wre.app.suggestions.domain

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.db.entity.toEntities
import hanten.wre.app.core.db.entity.toEntity
import hanten.wre.app.core.db.entity.toManga
import hanten.wre.app.core.db.entity.toMangaTagsList
import hanten.wre.app.core.model.toMangaSources
import hanten.wre.app.core.util.ext.mapItems
import hanten.wre.app.list.domain.ListFilterOption
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.suggestions.data.SuggestionEntity
import hanten.wre.app.suggestions.data.SuggestionWithManga
import javax.inject.Inject

class SuggestionRepository @Inject constructor(
	private val db: MangaDatabase,
) {

	fun observeAll(): Flow<List<Manga>> {
		return db.getSuggestionDao().observeAll().mapItems {
			it.toManga()
		}
	}

	fun observeAll(limit: Int, filterOptions: Set<ListFilterOption>): Flow<List<Manga>> {
		return db.getSuggestionDao().observeAll(limit, filterOptions).mapItems {
			it.toManga()
		}
	}

	suspend fun getRandomList(limit: Int): List<Manga> {
		return db.getSuggestionDao().getRandom(limit).map {
			it.toManga()
		}
	}

	suspend fun clear() {
		db.getSuggestionDao().deleteAll()
	}

	suspend fun isEmpty(): Boolean {
		return db.getSuggestionDao().count() == 0
	}

	suspend fun getTopTags(limit: Int): List<MangaTag> {
		return db.getSuggestionDao().getTopTags(limit)
			.toMangaTagsList()
	}

	suspend fun getTopSources(limit: Int): List<MangaSource> {
		return db.getSuggestionDao().getTopSources(limit)
			.toMangaSources()
	}

	suspend fun replace(suggestions: Iterable<MangaSuggestion>) {
		db.withTransaction {
			db.getSuggestionDao().deleteAll()
			suggestions.forEach { (manga, relevance) ->
				val tags = manga.tags.toEntities()
				db.getTagsDao().upsert(tags)
				db.getMangaDao().upsert(manga.toEntity(), tags)
				db.getSuggestionDao().upsert(
					SuggestionEntity(
						mangaId = manga.id,
						relevance = relevance,
						createdAt = System.currentTimeMillis(),
					),
				)
			}
		}
	}

	private fun SuggestionWithManga.toManga() = manga.toManga(emptySet(), null)
}
