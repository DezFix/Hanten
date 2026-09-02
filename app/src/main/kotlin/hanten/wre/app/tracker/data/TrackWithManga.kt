package hanten.wre.app.tracker.data

import androidx.room.Embedded
import androidx.room.Relation
import hanten.wre.app.core.db.entity.MangaEntity

class TrackWithManga(
	@Embedded val track: TrackEntity,
	@Relation(
		parentColumn = "manga_id",
		entityColumn = "manga_id",
	)
	val manga: MangaEntity,
)
