package hanten.wre.app.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration34To35 : Migration(34, 35) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE `manga_relations` ADD COLUMN `target_title` TEXT")
		db.execSQL("ALTER TABLE `manga_relations` ADD COLUMN `target_source` TEXT")
		db.execSQL("ALTER TABLE `manga_relations` ADD COLUMN `target_url` TEXT")
	}
}
