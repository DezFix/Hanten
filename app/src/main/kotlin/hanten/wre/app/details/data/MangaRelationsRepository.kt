package hanten.wre.app.details.data

import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.db.entity.MangaRelationEntity
import hanten.wre.app.core.model.MangaRelationOrigin
import hanten.wre.app.core.model.MangaRelationType
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.parsers.model.Manga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RelatedEntry(
	val manga: Manga,
	val type: MangaRelationType,
	val origin: MangaRelationOrigin,
)

@Singleton
class MangaRelationsRepository @Inject constructor(
	db: MangaDatabase,
	private val mangaDataRepository: MangaDataRepository,
) {

	private val dao = db.getMangaRelationsDao()

	fun observeFor(mangaId: Long): Flow<List<RelatedEntry>> = dao.observeFor(mangaId).map { relations ->
		relations.mapNotNull { entity ->
			val otherId = if (entity.fromId == mangaId) entity.toId else entity.fromId
			val manga = mangaDataRepository.findMangaById(otherId, withChapters = false)
				?: return@mapNotNull null
			RelatedEntry(
				manga = manga,
				type = if (entity.fromId == mangaId) entity.type else entity.type.reverse,
				origin = entity.origin,
			)
		}
	}

	suspend fun add(fromId: Long, toId: Long, type: MangaRelationType) {
		if (fromId == toId) return
		dao.insert(
			MangaRelationEntity(
				fromId = fromId,
				toId = toId,
				type = type,
				origin = MangaRelationOrigin.MANUAL,
				createdAt = System.currentTimeMillis(),
			),
		)
	}

	suspend fun remove(fromId: Long, toId: Long, type: MangaRelationType) = dao.delete(fromId, toId, type)

	suspend fun removeBetween(a: Long, b: Long) {
		dao.findFor(a).forEach { entity ->
			val matches = (entity.fromId == a && entity.toId == b) || (entity.fromId == b && entity.toId == a)
			if (matches) {
				dao.delete(entity.fromId, entity.toId, entity.type)
			}
		}
	}

	suspend fun dump(): List<MangaRelationEntity> = dao.findAll()

	suspend fun restore(entries: List<MangaRelationEntity>, isMerge: Boolean) {
		if (!isMerge) {
			dao.clear()
		}
		entries.forEach { dao.insert(it) }
	}
}
