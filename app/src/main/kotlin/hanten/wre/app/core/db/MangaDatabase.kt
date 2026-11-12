package hanten.wre.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import hanten.wre.app.bookmarks.data.BookmarkEntity
import hanten.wre.app.bookmarks.data.BookmarksDao
import hanten.wre.app.core.db.converters.DataConverters
import hanten.wre.app.core.db.dao.ChaptersDao
import hanten.wre.app.core.db.dao.ExternalExtensionRepoDao
import hanten.wre.app.core.db.dao.MangaDao
import hanten.wre.app.core.db.dao.MangaRelationsDao
import hanten.wre.app.core.db.dao.MangaSourcesDao
import hanten.wre.app.core.db.dao.PreferencesDao
import hanten.wre.app.core.db.dao.TagsDao
import hanten.wre.app.core.db.dao.TrackLogsDao
import hanten.wre.app.core.db.entity.ChapterEntity
import hanten.wre.app.core.db.entity.ExternalExtensionRepoEntity
import hanten.wre.app.core.db.entity.MangaEntity
import hanten.wre.app.core.db.entity.MangaPrefsEntity
import hanten.wre.app.core.db.entity.MangaSourceEntity
import hanten.wre.app.core.db.entity.MangaRelationEntity
import hanten.wre.app.core.db.entity.MangaTagsEntity
import hanten.wre.app.core.db.entity.TagEntity
import hanten.wre.app.core.db.migrations.Migration10To11
import hanten.wre.app.core.db.migrations.Migration11To12
import hanten.wre.app.core.db.migrations.Migration12To13
import hanten.wre.app.core.db.migrations.Migration13To14
import hanten.wre.app.core.db.migrations.Migration14To15
import hanten.wre.app.core.db.migrations.Migration15To16
import hanten.wre.app.core.db.migrations.Migration16To17
import hanten.wre.app.core.db.migrations.Migration17To18
import hanten.wre.app.core.db.migrations.Migration18To19
import hanten.wre.app.core.db.migrations.Migration19To20
import hanten.wre.app.core.db.migrations.Migration1To2
import hanten.wre.app.core.db.migrations.Migration20To21
import hanten.wre.app.core.db.migrations.Migration21To22
import hanten.wre.app.core.db.migrations.Migration22To23
import hanten.wre.app.core.db.migrations.Migration23To24
import hanten.wre.app.core.db.migrations.Migration24To25
import hanten.wre.app.core.db.migrations.Migration25To26
import hanten.wre.app.core.db.migrations.Migration26To27
import hanten.wre.app.core.db.migrations.Migration27To28
import hanten.wre.app.core.db.migrations.Migration28To29
import hanten.wre.app.core.db.migrations.Migration29To30
import hanten.wre.app.core.db.migrations.Migration30To31
import hanten.wre.app.core.db.migrations.Migration31To32
import hanten.wre.app.core.db.migrations.Migration32To33
import hanten.wre.app.core.db.migrations.Migration2To3
import hanten.wre.app.core.db.migrations.Migration3To4
import hanten.wre.app.core.db.migrations.Migration4To5
import hanten.wre.app.core.db.migrations.Migration5To6
import hanten.wre.app.core.db.migrations.Migration6To7
import hanten.wre.app.core.db.migrations.Migration7To8
import hanten.wre.app.core.db.migrations.Migration8To9
import hanten.wre.app.core.db.migrations.Migration9To10
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.processLifecycleScope
import hanten.wre.app.download.data.dao.DownloadQueueDao
import hanten.wre.app.download.data.dao.SmartDownloadDao
import hanten.wre.app.download.data.entity.DownloadQueueEntity
import hanten.wre.app.download.data.entity.SmartDownloadEntity
import hanten.wre.app.favourites.data.FavouriteCategoriesDao
import hanten.wre.app.favourites.data.FavouriteCategoryEntity
import hanten.wre.app.favourites.data.FavouriteEntity
import hanten.wre.app.favourites.data.FavouritesDao
import hanten.wre.app.history.data.HistoryDao
import hanten.wre.app.history.data.HistoryEntity
import hanten.wre.app.local.data.index.LocalMangaIndexDao
import hanten.wre.app.local.data.index.LocalMangaIndexEntity
import hanten.wre.app.scrobbling.common.data.ScrobblingDao
import hanten.wre.app.scrobbling.common.data.ScrobblingEntity
import hanten.wre.app.stats.data.StatsDao
import hanten.wre.app.stats.data.StatsEntity
import hanten.wre.app.suggestions.data.SuggestionDao
import hanten.wre.app.suggestions.data.SuggestionEntity
import hanten.wre.app.tracker.data.TrackEntity
import hanten.wre.app.tracker.data.TrackLogEntity
import hanten.wre.app.tracker.data.TracksDao
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val DATABASE_VERSION = 33

@Database(
	entities = [
		MangaEntity::class, TagEntity::class, HistoryEntity::class, MangaTagsEntity::class, ChapterEntity::class,
		FavouriteCategoryEntity::class, FavouriteEntity::class, MangaPrefsEntity::class, TrackEntity::class,
		TrackLogEntity::class, SuggestionEntity::class, BookmarkEntity::class, ScrobblingEntity::class,
		MangaSourceEntity::class, StatsEntity::class, LocalMangaIndexEntity::class, ExternalExtensionRepoEntity::class,
		DownloadQueueEntity::class, SmartDownloadEntity::class, MangaRelationEntity::class,
	],
	version = DATABASE_VERSION,
)
@TypeConverters(DataConverters::class)
abstract class MangaDatabase : RoomDatabase() {

	abstract fun getHistoryDao(): HistoryDao

	abstract fun getTagsDao(): TagsDao

	abstract fun getMangaDao(): MangaDao

	abstract fun getFavouritesDao(): FavouritesDao

	abstract fun getPreferencesDao(): PreferencesDao

	abstract fun getFavouriteCategoriesDao(): FavouriteCategoriesDao

	abstract fun getTracksDao(): TracksDao

	abstract fun getTrackLogsDao(): TrackLogsDao

	abstract fun getSuggestionDao(): SuggestionDao

	abstract fun getBookmarksDao(): BookmarksDao

	abstract fun getScrobblingDao(): ScrobblingDao

	abstract fun getSourcesDao(): MangaSourcesDao

	abstract fun getStatsDao(): StatsDao

	abstract fun getLocalMangaIndexDao(): LocalMangaIndexDao

	abstract fun getChaptersDao(): ChaptersDao

	abstract fun getExternalExtensionRepoDao(): ExternalExtensionRepoDao

	abstract fun getDownloadQueueDao(): DownloadQueueDao

	abstract fun getSmartDownloadDao(): SmartDownloadDao

	abstract fun getMangaRelationsDao(): MangaRelationsDao
}

fun getDatabaseMigrations(context: Context): Array<Migration> = arrayOf(
	Migration1To2(),
	Migration2To3(),
	Migration3To4(),
	Migration4To5(),
	Migration5To6(),
	Migration6To7(),
	Migration7To8(),
	Migration8To9(),
	Migration9To10(),
	Migration10To11(),
	Migration11To12(),
	Migration12To13(),
	Migration13To14(),
	Migration14To15(),
	Migration15To16(),
	Migration16To17(context),
	Migration17To18(),
	Migration18To19(),
	Migration19To20(),
	Migration20To21(),
	Migration21To22(),
	Migration22To23(),
	Migration23To24(),
	Migration24To25(),
	Migration25To26(),
	Migration26To27(),
	Migration27To28(),
	Migration28To29(),
    Migration29To30(),
    Migration30To31(),
    Migration31To32(),
    Migration32To33(),
)

fun MangaDatabase(context: Context): MangaDatabase {
	migrateDatabaseFile(context, oldName = "futon-db", newName = "hanten-db")
	return Room
		.databaseBuilder(context, MangaDatabase::class.java, "hanten-db")
		.addMigrations(*getDatabaseMigrations(context))
		.addCallback(DatabasePrePopulateCallback(context.resources))
		.build()
}

private fun migrateDatabaseFile(context: Context, oldName: String, newName: String) {
	runCatching {
		val newFile = context.getDatabasePath(newName)
		if (newFile.exists()) return
		val oldFile = context.getDatabasePath(oldName)
		if (!oldFile.exists()) return
		newFile.parentFile?.mkdirs()

		// Copy to temporary names first: a partially copied hanten-db would
		// otherwise be treated as valid on the next launch and Room would open
		// a truncated database.
		val tempMain = File(newFile.path + ".tmp")
		val tempSidecars = arrayOf("-shm", "-wal").associateWith { suffix ->
			File(tempMain.path + suffix)
		}
		tempMain.delete()
		tempSidecars.values.forEach { it.delete() }

		oldFile.copyTo(tempMain, overwrite = true)
		for ((suffix, tempExtra) in tempSidecars) {
			val oldExtra = File(oldFile.path + suffix)
			if (oldExtra.exists()) {
				oldExtra.copyTo(tempExtra, overwrite = true)
			}
		}

		// Replace sidecars first, the main file last. If the process dies in
		// between, the next launch sees newFile missing and retries the copy.
		for ((suffix, tempExtra) in tempSidecars) {
			if (tempExtra.exists()) {
				val target = File(newFile.path + suffix)
				target.delete()
				check(tempExtra.renameTo(target)) {
					"Cannot move temporary database sidecar to ${target.path}"
				}
			}
		}
		check(tempMain.renameTo(newFile)) {
			"Cannot move temporary database to ${newFile.path}"
		}
	}.onFailure { e ->
		e.printStackTraceDebug("MangaDatabase::migrateDatabaseFile")
	}
}

fun InvalidationTracker.removeObserverAsync(observer: InvalidationTracker.Observer) {
	val scope = processLifecycleScope
	if (scope.isActive) {
		processLifecycleScope.launch(Dispatchers.IO, CoroutineStart.ATOMIC) {
			removeObserver(observer)
		}
	}
}
