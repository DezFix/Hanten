package hanten.wre.app.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Hanten 3.0: library folders get a color. */
class Migration37To38 : Migration(37, 38) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE `favourite_categories` ADD COLUMN `color` INTEGER NOT NULL DEFAULT 0")
	}
}
