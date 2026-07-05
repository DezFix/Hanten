package io.github.landwarderer.futon.download.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.landwarderer.futon.download.data.entity.SmartDownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartDownloadDao {
    @Query("SELECT * FROM smart_downloads WHERE manga_id = :mangaId")
    suspend fun get(mangaId: Long): SmartDownloadEntity?

    @Query("SELECT * FROM smart_downloads WHERE manga_id = :mangaId")
    fun observe(mangaId: Long): Flow<SmartDownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SmartDownloadEntity)

    @Update
    suspend fun update(entity: SmartDownloadEntity)

    @Query("DELETE FROM smart_downloads WHERE manga_id = :mangaId")
    suspend fun delete(mangaId: Long)
}
