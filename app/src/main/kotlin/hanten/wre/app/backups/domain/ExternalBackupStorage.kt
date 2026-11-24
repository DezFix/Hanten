package hanten.wre.app.backups.domain

import android.content.Context
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okio.buffer
import okio.sink
import okio.source
import org.jetbrains.annotations.Blocking
import java.io.File
import javax.inject.Inject

class ExternalBackupStorage @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
) {

	suspend fun list(): List<BackupFile> = runInterruptible(Dispatchers.IO) {
		getRootOrThrow().listFiles().mapNotNull {
			if (it.isFile && it.canRead()) {
				BackupFile(
					uri = it.uri,
					dateTime = it.name?.let { fileName ->
						BackupUtils.parseBackupDateTime(fileName)
					} ?: return@mapNotNull null,
				)
			} else {
				null
			}
		}.sortedDescending()
	}

	suspend fun listOrNull() = runCatchingCancellable {
		list()
	}.onFailure { e ->
		e.printStackTraceDebug("ExternalBackupStorage::listOrNull")
	}.getOrNull()

	suspend fun put(file: File): Uri = runInterruptible(Dispatchers.IO) {
		val out = checkNotNull(
			getRootOrThrow().createFile(
				"application/zip",
				file.nameWithoutExtension,
			),
		) {
			"Cannot create target backup file"
		}
		checkNotNull(context.contentResolver.openOutputStream(out.uri, "wt")).sink().use { sink ->
			file.source().buffer().use { src ->
				src.readAll(sink)
			}
		}
		out.uri
	}

	@CheckResult
	suspend fun delete(victim: BackupFile) = runInterruptible(Dispatchers.IO) {
		if (victim.uri.scheme == "file") {
			File(checkNotNull(victim.uri.path)).delete()
		} else {
			val df = DocumentFile.fromSingleUri(context, victim.uri)
			df != null && df.delete()
		}
	}

	suspend fun getLastBackupDate() = listOrNull()?.maxOfOrNull { it.dateTime }

	suspend fun trim(maxCount: Int): Boolean {
		if (maxCount == Int.MAX_VALUE) {
			return false
		}
		val list = listOrNull()
		if (list == null || list.size <= maxCount) {
			return false
		}
		var result = false
		for (i in maxCount until list.size) {
			if (delete(list[i])) {
				result = true
			}
		}
		return result
	}

	/**
	 * The folder the user picked, or the app's own folder when none is picked or this device has no access
	 * to it (a folder link restored from a backup made on another phone or install).
	 */
	@Blocking
	private fun getRootOrThrow(): DocumentFile {
		val picked = settings.periodicalBackupDirectory?.let { DocumentFile.fromTreeUri(context, it) }
		if (picked != null && picked.canWrite()) {
			return picked
		}
		val fallback = BackupUtils.getPeriodicBackupDir(context)
		check(fallback.isDirectory || fallback.mkdirs()) { "Cannot create backup directory ${fallback.path}" }
		return DocumentFile.fromFile(fallback)
	}
}
