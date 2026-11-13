package hanten.wre.app.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Manual series relations were replaced by automatic hints; the table is no longer used.
 * Older backups may still contain a `relations` section and it is skipped safely.
 */
class Migration35To36 : Migration(35, 36) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("DROP TABLE IF EXISTS `manga_relations`")
	}
}
