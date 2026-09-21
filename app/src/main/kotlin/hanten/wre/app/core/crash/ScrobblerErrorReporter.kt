package hanten.wre.app.core.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.BuildConfig
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends tracker (scrobbler) failures to our Bugsink instance when the user opted in.
 *
 * Unlike app crashes, these are caught errors surfaced in the UI
 * (login failures, broken lists, sync errors).
 * Rate-limited per service + error type.
 */
@Singleton
class ScrobblerErrorReporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
) {

	fun report(error: Throwable, service: ScrobblerService?) {
		if (!settings.isErrorReportsEnabled) {
			return
		}
		if (!io.sentry.Sentry.isEnabled()) {
			return
		}
		if (error is CancellationException) {
			return
		}
		val key = "scrobbler|${service?.name ?: "unknown"}|${error.javaClass.simpleName}"
		if (!checkThrottle(key)) {
			return
		}
		runCatching {
			io.sentry.Sentry.captureException(error) { scope ->
				scope.setTag("kind", "scrobbler_error")
				scope.setTag("service", service?.name ?: "unknown")
				scope.setExtra(
					"app_version",
					BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
				)
				error.message?.takeIf { it.isNotBlank() }?.let {
					scope.setExtra("error_message", it.take(MAX_MESSAGE_LENGTH))
				}
			}
		}
	}

	private fun checkThrottle(key: String): Boolean {
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val now = System.currentTimeMillis()
		if (now - prefs.getLong(key.hashCode().toString(), 0L) < MIN_INTERVAL_MS) {
			return false
		}
		prefs.edit().putLong(key.hashCode().toString(), now).apply()
		return true
	}

	private companion object {
		const val PREFS_NAME = "scrobbler_error_reports"
		const val MIN_INTERVAL_MS = 60 * 60 * 1000L
		const val MAX_MESSAGE_LENGTH = 500
	}
}
