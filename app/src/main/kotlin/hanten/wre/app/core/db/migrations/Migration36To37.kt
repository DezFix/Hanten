package hanten.wre.app.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Persistent metadata cache for library entries: description and refresh timestamp. */
class Migration36To37 : Migration(36, 37) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE `manga` ADD COLUMN `description` TEXT")
		db.execSQL("ALTER TABLE `manga` ADD COLUMN `details_updated_at` INTEGER NOT NULL DEFAULT 0")
	}
}
