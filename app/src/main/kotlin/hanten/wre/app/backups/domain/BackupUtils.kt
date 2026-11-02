package hanten.wre.app.backups.domain

import android.content.Context
import androidx.annotation.CheckResult
import hanten.wre.app.R
import hanten.wre.app.core.util.ext.printStackTraceDebug
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

object BackupUtils {

	private const val DIR_BACKUPS = "backups"
	private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

	@CheckResult
	fun createTempFile(context: Context): File {
		val dir = getAppBackupDir(context)
		dir.mkdirs()
		return File(dir, generateFileName(context))
	}

	fun getAppBackupDir(context: Context) = context.run {
		getExternalFilesDir(DIR_BACKUPS) ?: File(filesDir, DIR_BACKUPS)
	}

	fun parseBackupDateTime(fileName: String): Date? = try {
		val value = fileName.substringAfterLast('_').substringBefore('.')
		Date.from(
			LocalDateTime.parse(value, dateTimeFormatter)
				.atZone(ZoneId.systemDefault())
				.toInstant(),
		)
	} catch (e: DateTimeParseException) {
		e.printStackTraceDebug("BackupUtils::parseBackupDateTime")
		null
	}

	fun generateFileName(context: Context) = buildString {
		append(context.getString(R.string.app_name).replace(' ', '_').lowercase(Locale.ROOT))
		append('_')
		append(dateTimeFormatter.format(LocalDateTime.now()))
		append(".bk.zip")
	}
}
