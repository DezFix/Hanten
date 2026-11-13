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
	@ColumnInfo(name = "target_title") val targetTitle: String? = null,
	@ColumnInfo(name = "target_source") val targetSource: String? = null,
	@ColumnInfo(name = "target_url") val targetUrl: String? = null,
) {

	/**
	 * A relation added by title only has no source snapshot. Linked works always get
	 * [targetSource] from [Manga.source], so this also distinguishes legacy rows.
	 */
	val isUnlinked: Boolean
		get() = targetTitle != null && targetSource == null
}
