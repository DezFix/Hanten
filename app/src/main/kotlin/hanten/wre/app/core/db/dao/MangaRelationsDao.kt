package hanten.wre.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import hanten.wre.app.core.db.entity.MangaRelationEntity
import hanten.wre.app.core.model.MangaRelationType
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaRelationsDao {

	@Query("SELECT * FROM manga_relations WHERE from_id = :mangaId OR to_id = :mangaId")
	fun observeFor(mangaId: Long): Flow<List<MangaRelationEntity>>

	@Query("SELECT * FROM manga_relations WHERE from_id = :mangaId OR to_id = :mangaId")
	suspend fun findFor(mangaId: Long): List<MangaRelationEntity>

	@Query("SELECT * FROM manga_relations")
	suspend fun findAll(): List<MangaRelationEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(entity: MangaRelationEntity)

	@Query("DELETE FROM manga_relations WHERE from_id = :fromId AND to_id = :toId AND type = :type")
	suspend fun delete(fromId: Long, toId: Long, type: MangaRelationType)

	@Query("DELETE FROM manga_relations")
	suspend fun clear()
}
