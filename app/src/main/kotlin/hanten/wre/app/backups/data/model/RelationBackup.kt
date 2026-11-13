package hanten.wre.app.backups.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import hanten.wre.app.core.db.entity.MangaRelationEntity
import hanten.wre.app.core.model.MangaRelationOrigin
import hanten.wre.app.core.model.MangaRelationType

@Serializable
class RelationBackup(
	@SerialName("from_id") val fromId: Long,
	@SerialName("to_id") val toId: Long,
	@SerialName("type") val type: MangaRelationType,
	@SerialName("origin") val origin: MangaRelationOrigin = MangaRelationOrigin.MANUAL,
	@SerialName("created_at") val createdAt: Long,
	@SerialName("target_title") val targetTitle: String? = null,
	@SerialName("target_source") val targetSource: String? = null,
	@SerialName("target_url") val targetUrl: String? = null,
) {

	constructor(entity: MangaRelationEntity) : this(
		fromId = entity.fromId,
		toId = entity.toId,
		type = entity.type,
		origin = entity.origin,
		createdAt = entity.createdAt,
		targetTitle = entity.targetTitle,
		targetSource = entity.targetSource,
		targetUrl = entity.targetUrl,
	)

	fun toEntity() = MangaRelationEntity(
		fromId = fromId,
		toId = toId,
		type = type,
		origin = origin,
		createdAt = createdAt,
		targetTitle = targetTitle,
		targetSource = targetSource,
		targetUrl = targetUrl,
	)
}
