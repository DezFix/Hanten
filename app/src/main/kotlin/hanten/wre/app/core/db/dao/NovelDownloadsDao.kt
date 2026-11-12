package hanten.wre.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import hanten.wre.app.core.db.entity.NovelDownloadEntity

@Dao
interface NovelDownloadsDao {

	@Query("SELECT EXISTS(SELECT 1 FROM novel_downloads WHERE manga_id = :mangaId)")
	suspend fun contains(mangaId: Long): Boolean

	@Query("SELECT COUNT(*) FROM novel_downloads")
	suspend fun count(): Int

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entity: NovelDownloadEntity)

	@Query("DELETE FROM novel_downloads WHERE manga_id = :mangaId")
	suspend fun delete(mangaId: Long)

	@Query("DELETE FROM novel_downloads")
	suspend fun clear()
}
