package hanten.wre.app.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration32To33 : Migration(32, 33) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `manga_relations` (
				`from_id` INTEGER NOT NULL,
				`to_id` INTEGER NOT NULL,
				`type` TEXT NOT NULL,
				`origin` TEXT NOT NULL,
				`created_at` INTEGER NOT NULL,
				PRIMARY KEY(`from_id`, `to_id`, `type`)
			)
			""".trimIndent(),
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_relations_from_id` ON `manga_relations` (`from_id`)")
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_relations_to_id` ON `manga_relations` (`to_id`)")
	}
}
