package hanten.wre.app.core.github

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.BuildConfig
import hanten.wre.app.R
import hanten.wre.app.core.os.AppValidator
import hanten.wre.app.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared app-update flow (check, changelog dialog, in-app download and install).
 * Used by MainActivity (automatic check on start) and the About screen (manual check).
 */
@Singleton
class AppUpdateFlow @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val repository: AppUpdateRepository,
	private val downloader: AppUpdateDownloader,
	private val validator: AppValidator,
) {

	var pendingRelease: AppRelease? = null
		private set
	var pendingFile: File? = null
		private set

	suspend fun checkForUpdate(ignoreSkippedTag: Boolean): AppRelease? = withContext(Dispatchers.IO) {
		val release = repository.fetchLatestRelease() ?: return@withContext null
		val latest = VersionId(release.tag.trimStart('v', 'V'))
		val current = VersionId(BuildConfig.VERSION_NAME.trimStart('v', 'V'))
		if (latest <= current) return@withContext null
		if (!ignoreSkippedTag && prefs().getString(KEY_SKIPPED_UPDATE_TAG, null) == release.tag) {
			return@withContext null
		}
		release
	}

	fun showUpdateDialog(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
		release: AppRelease,
	) {
		val changelog = release.changelog.ifBlank { activity.getString(R.string.update_no_changelog) }
		MaterialAlertDialogBuilder(activity)
			.setTitle(activity.getString(R.string.update_available_title, release.tag))
			.setMessage(changelog)
			.setPositiveButton(R.string.update_download_and_install) { _, _ ->
				startUpdate(activity, anchor, permissionLauncher, release)
			}
			.setNeutralButton(R.string.update_skip_version) { _, _ ->
				prefs().edit().putString(KEY_SKIPPED_UPDATE_TAG, release.tag).apply()
			}
			.setNegativeButton(R.string.update_later, null)
			.show()
	}

	fun handlePermissionResult(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
	) {
		val file = pendingFile
		pendingFile = null
		if (file != null && file.exists()) {
			installUpdate(activity, anchor, permissionLauncher, file)
		} else {
			pendingRelease?.let { startUpdate(activity, anchor, permissionLauncher, it) }
			pendingRelease = null
		}
	}

	private fun startUpdate(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
		release: AppRelease,
	) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
			pendingRelease = release
			Snackbar.make(anchor, R.string.update_allow_unknown, Snackbar.LENGTH_LONG)
				.setAction(R.string.update_open_settings) {
					openUnknownSourcesSettings(activity, permissionLauncher)
				}
				.show()
			return
		}
		downloadUpdate(activity, anchor, permissionLauncher, release)
	}

	private fun openUnknownSourcesSettings(
		activity: FragmentActivity,
		permissionLauncher: ActivityResultLauncher<Intent>,
	) {
		runCatching {
			val intent = Intent(
				Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
				"package:${activity.packageName}".toUri(),
			)
			permissionLauncher.launch(intent)
		}.onFailure {
			it.printStackTraceDebug("AppUpdateFlow::openUnknownSourcesSettings")
		}
	}

	private fun downloadUpdate(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
		release: AppRelease,
	) {
		val apkUrl = release.apkUrl
		if (apkUrl.isNullOrEmpty()) {
			activity.startActivity(Intent(Intent.ACTION_VIEW, release.pageUrl.toUri()))
			return
		}
		val destFile = File(File(activity.cacheDir, DIR_UPDATES), "Hanten-${release.tag}.apk")
		val density = activity.resources.displayMetrics.density
		val padding = (24 * density).toInt()
		val layout = LinearLayout(activity).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, (padding / 2), padding, 0)
		}
		val progress = LinearProgressIndicator(activity).apply {
			isIndeterminate = true
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT,
			)
		}
		val percentView = TextView(activity).apply {
			text = activity.getString(R.string.update_download_progress, 0, "", "")
		}
		layout.addView(progress)
		layout.addView(percentView)
		var downloadJob: Job? = null
		val dialog = MaterialAlertDialogBuilder(activity)
			.setTitle(activity.getString(R.string.update_downloading_title, release.tag))
			.setView(layout)
			.setNegativeButton(R.string.update_cancel) { _, _ ->
				downloadJob?.cancel()
			}
			.setCancelable(false)
			.show()
		downloadJob = activity.lifecycleScope.launch {
			try {
				val file = withContext(Dispatchers.IO) {
					destFile.parentFile?.listFiles()?.forEach { old ->
						if (old != destFile) old.delete()
					}
					downloader.downloadUpdate(apkUrl, destFile) { done, total ->
						layout.post {
							if (total != null && total > 0) {
								val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
								progress.isIndeterminate = false
								progress.max = 100
								progress.progress = pct
								percentView.text = activity.getString(
									R.string.update_download_progress,
									pct,
									formatSize(activity, done),
									formatSize(activity, total),
								)
							} else {
								percentView.text = formatSize(activity, done)
							}
						}
					}
				}
				dialog.dismiss()
				installUpdate(activity, anchor, permissionLauncher, file)
			} catch (e: CancellationException) {
				dialog.dismiss()
				throw e
			} catch (e: Exception) {
				dialog.dismiss()
				e.printStackTraceDebug("AppUpdateFlow::downloadUpdate")
				showDownloadError(activity, anchor, release, e)
			}
		}
	}

	private fun installUpdate(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
		file: File,
	) {
		if (!validator.isTrustedApk(file)) {
			file.delete()
			Snackbar.make(anchor, R.string.update_install_untrusted, Snackbar.LENGTH_LONG).show()
			return
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
			!activity.packageManager.canRequestPackageInstalls()
		) {
			pendingFile = file
			Snackbar.make(anchor, R.string.update_allow_unknown, Snackbar.LENGTH_LONG)
				.setAction(R.string.update_open_settings) {
					openUnknownSourcesSettings(activity, permissionLauncher)
				}
				.show()
			return
		}
		runCatching {
			val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
			val intent = Intent(Intent.ACTION_VIEW)
				.setDataAndType(uri, "application/vnd.android.package-archive")
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			activity.startActivity(intent)
		}.onFailure {
			it.printStackTraceDebug("AppUpdateFlow::installUpdate")
			Snackbar.make(anchor, R.string.update_download_failed, Snackbar.LENGTH_LONG).show()
		}
	}

	private fun showDownloadError(
		activity: FragmentActivity,
		anchor: View,
		release: AppRelease,
		e: Throwable,
	) {
		val message = activity.getString(
			R.string.update_download_failed,
			e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
		)
		Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
			.setAction(R.string.update_open_in_browser) {
				activity.startActivity(Intent(Intent.ACTION_VIEW, release.pageUrl.toUri()))
			}
			.show()
	}

	private fun prefs() = appContext.getSharedPreferences(PREFS_APP_UPDATES, Context.MODE_PRIVATE)

	private companion object {
		const val PREFS_APP_UPDATES = "app_updates"
		const val KEY_SKIPPED_UPDATE_TAG = "skipped_update_tag"
		const val DIR_UPDATES = "updates"

		fun formatSize(context: Context, bytes: Long): String =
			Formatter.formatShortFileSize(context, bytes)
	}
}
