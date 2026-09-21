package hanten.wre.app.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration31To32 : Migration(31, 32) {
	override fun migrate(db: SupportSQLiteDatabase) {
		// Kitsu tracker was removed: drop its orphaned scrobbling rows (service id 4)
		db.execSQL("DELETE FROM `scrobblings` WHERE `scrobbler` = 4")
	}
}
