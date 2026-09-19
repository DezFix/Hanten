package hanten.wre.app.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration30To31 : Migration(30, 31) {
	override fun migrate(db: SupportSQLiteDatabase) {
		// manga: findAllBySource(source), findByPublicUrl(public_url)
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_source` ON `manga` (`source`)")
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_public_url` ON `manga` (`public_url`)")
		// history: WHERE deleted_at = 0 ORDER BY updated_at DESC
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_deleted_at_updated_at` ON `history` (`deleted_at`, `updated_at`)")
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_updated_at` ON `history` (`updated_at`)")
		// tracks: ORDER BY last_check_time, WHERE chapters_new > 0 ORDER BY last_chapter_date DESC
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_last_check_time` ON `tracks` (`last_check_time`)")
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_chapters_new_last_chapter_date` ON `tracks` (`chapters_new`, `last_chapter_date`)")
		// track_logs: WHERE unread = 1, ORDER BY created_at DESC
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_logs_unread_created_at` ON `track_logs` (`unread`, `created_at`)")
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_logs_created_at` ON `track_logs` (`created_at`)")
	}
}
