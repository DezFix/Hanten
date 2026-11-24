package hanten.wre.app.core.github

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.BuildConfig
import hanten.wre.app.R
import hanten.wre.app.core.os.AppValidator
import hanten.wre.app.databinding.SheetAppUpdateBinding
import hanten.wre.app.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

	/** Hanten 3.0: «Доступно обновление» as a bottom sheet in the app style; the download runs inside it. */
	fun showUpdateDialog(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
		release: AppRelease,
	) = showUpdateSheet(activity, anchor, permissionLauncher, release, startDownload = false)

	private fun showUpdateSheet(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
		release: AppRelease,
		startDownload: Boolean,
	) {
		val binding = SheetAppUpdateBinding.inflate(activity.layoutInflater)
		val dialog = BottomSheetDialog(activity)
		dialog.setContentView(binding.root)
		dialog.behavior.skipCollapsed = true
		dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
		binding.textVersion.text = activity.getString(
			R.string.update_version_line,
			release.tag.trimStart('v', 'V'),
			BuildConfig.VERSION_NAME,
		)
		binding.textChangelog.text = release.changelog.ifBlank { activity.getString(R.string.update_no_changelog) }
		val maxChangelogHeight = (activity.resources.displayMetrics.heightPixels * 0.4f).toInt()
		binding.scrollChangelog.post {
			if (binding.scrollChangelog.height > maxChangelogHeight) {
				binding.scrollChangelog.updateLayoutParams { height = maxChangelogHeight }
			}
		}
		binding.buttonLater.setOnClickListener { dialog.dismiss() }
		binding.buttonSkip.setOnClickListener {
			prefs().edit().putString(KEY_SKIPPED_UPDATE_TAG, release.tag).apply()
			dialog.dismiss()
		}
		binding.buttonInstall.setOnClickListener {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
				dialog.dismiss()
				startUpdate(activity, anchor, permissionLauncher, release)
			} else {
				downloadInSheet(activity, anchor, permissionLauncher, release, binding, dialog)
			}
		}
		dialog.show()
		if (startDownload) {
			downloadInSheet(activity, anchor, permissionLauncher, release, binding, dialog)
		}
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
	) = showUpdateSheet(activity, anchor, permissionLauncher, release, startDownload = true)

	private fun downloadInSheet(
		activity: FragmentActivity,
		anchor: View,
		permissionLauncher: ActivityResultLauncher<Intent>,
		release: AppRelease,
		binding: SheetAppUpdateBinding,
		dialog: BottomSheetDialog,
	) {
		val apkUrl = release.apkUrl
		if (apkUrl.isNullOrEmpty()) {
			dialog.dismiss()
			activity.startActivity(Intent(Intent.ACTION_VIEW, release.pageUrl.toUri()))
			return
		}
		val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
		val destFile = File(File(activity.cacheDir, DIR_UPDATES), "Hanten-$safeTag.apk")
		binding.layoutActions.isVisible = false
		binding.layoutProgress.isVisible = true
		binding.textTitle.text = activity.getString(R.string.update_downloading_title, release.tag)
		binding.textProgress.text = activity.getString(R.string.update_download_progress, 0, "", "")
		binding.progress.isIndeterminate = true
		dialog.setCancelable(false)
		val downloadJob = activity.lifecycleScope.launch {
			try {
				val file = withContext(Dispatchers.IO) {
					destFile.parentFile?.listFiles()?.forEach { old ->
						if (old != destFile) old.delete()
					}
					downloader.downloadUpdate(apkUrl, destFile) { done, total ->
						binding.root.post {
							if (total != null && total > 0) {
								val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
								binding.progress.isIndeterminate = false
								binding.progress.max = 100
								binding.progress.setProgressCompat(pct, true)
								binding.textProgress.text = activity.getString(
									R.string.update_download_progress,
									pct,
									formatSize(activity, done),
									formatSize(activity, total),
								)
							} else {
								binding.textProgress.text = formatSize(activity, done)
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
		binding.buttonCancel.setOnClickListener { downloadJob.cancel() }
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
