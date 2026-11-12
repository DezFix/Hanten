package hanten.wre.app.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Marks mangas that have explicitly downloaded novel chapters.
 * Files live in `filesDir/novels`; this table is a queryable index for lists and filters.
 */
@Entity(tableName = "novel_downloads")
data class NovelDownloadEntity(
	@PrimaryKey
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "updated_at") val updatedAt: Long,
)
