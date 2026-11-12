package hanten.wre.app.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import hanten.wre.app.core.model.MangaRelationOrigin
import hanten.wre.app.core.model.MangaRelationType

@Entity(
	tableName = "manga_relations",
	primaryKeys = ["from_id", "to_id", "type"],
	indices = [Index("from_id"), Index("to_id")],
)
data class MangaRelationEntity(
	@ColumnInfo(name = "from_id") val fromId: Long,
	@ColumnInfo(name = "to_id") val toId: Long,
	val type: MangaRelationType,
	val origin: MangaRelationOrigin,
	@ColumnInfo(name = "created_at") val createdAt: Long,
)
