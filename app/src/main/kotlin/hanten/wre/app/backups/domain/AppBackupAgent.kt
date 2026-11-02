package hanten.wre.app.backups.domain

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import com.google.common.io.ByteStreams
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import hanten.wre.app.backups.data.BackupRepository
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.filter.data.SavedFiltersRepository
import hanten.wre.app.mihon.MihonExtensionManager
import hanten.wre.app.reader.data.TapGridSettings
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.util.EnumSet
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Provider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppBackupAgentEntryPoint {
    fun mihonExtensionManager(): Provider<MihonExtensionManager>
    fun mangaDatabase(): MangaDatabase
    fun appSettings(): AppSettings
}

class AppBackupAgent : BackupAgent() {

    private val entryPoint: AppBackupAgentEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            AppBackupAgentEntryPoint::class.java,
        )
    }

    private val mihonExtensionManager: Provider<MihonExtensionManager> by lazy {
        entryPoint.mihonExtensionManager()
    }

    private val backupRepository: BackupRepository by lazy {
        val database = entryPoint.mangaDatabase()
        val settings = entryPoint.appSettings()
        BackupRepository(
            database = database,
            settings = settings,
            tapGridSettings = TapGridSettings(applicationContext),
            mangaSourcesRepository = MangaSourcesRepository(
                context = applicationContext,
                db = database,
                settings = settings,
                mihonExtensionManager = mihonExtensionManager.get(),
            ),
            savedFiltersRepository = SavedFiltersRepository(
                context = applicationContext,
            ),
        )
    }

	override fun onBackup(
		oldState: ParcelFileDescriptor?,
		data: BackupDataOutput?,
		newState: ParcelFileDescriptor?
	) = Unit

	override fun onRestore(
		data: BackupDataInput?,
		appVersionCode: Int,
		newState: ParcelFileDescriptor?
	) = Unit

	override fun onFullBackup(data: FullBackupDataOutput) {
		super.onFullBackup(data)

		val file = createBackupFile(
			this,
			backupRepository,
		)
		try {
			fullBackupFile(file, data)
		} finally {
			file.delete()
		}
	}

	override fun onRestoreFile(
		data: ParcelFileDescriptor,
		size: Long,
		destination: File?,
		type: Int,
		mode: Long,
		mtime: Long
	) {
		if (destination?.name?.endsWith(".bk.zip") == true) {
			restoreBackupFile(
				data.fileDescriptor,
				size,
				backupRepository,
			)
			destination.delete()
		} else {
			super.onRestoreFile(data, size, destination, type, mode, mtime)
		}
	}

	@VisibleForTesting
	fun createBackupFile(context: Context, repository: BackupRepository): File {
		val file = BackupUtils.createTempFile(context)
		ZipOutputStream(file.outputStream()).use { output ->
			runBlocking {
				repository.createBackup(output, null)
			}
		}
		return file
	}

	@VisibleForTesting
	fun restoreBackupFile(fd: FileDescriptor, size: Long, repository: BackupRepository) {
		ZipInputStream(ByteStreams.limit(FileInputStream(fd), size)).use { input ->
			val sections = EnumSet.allOf(BackupSection::class.java)
			// managed externally
			sections.remove(BackupSection.SETTINGS)
			sections.remove(BackupSection.SETTINGS_READER_GRID)
			runBlocking {
				repository.restoreBackup(input, sections, null)
			}
		}
	}
}
