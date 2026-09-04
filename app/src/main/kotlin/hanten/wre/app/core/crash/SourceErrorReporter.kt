package hanten.wre.app.core.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.BuildConfig
import hanten.wre.app.core.exceptions.CloudFlareProtectedException
import hanten.wre.app.core.exceptions.InteractiveActionRequiredException
import hanten.wre.app.core.exceptions.ProxyConfigException
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.parsers.exception.AuthRequiredException
import hanten.wre.app.parsers.exception.NotFoundException
import hanten.wre.app.parsers.model.MangaSource
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Sends source (parser) failures to our Bugsink instance when the user opted in.
 *
 * Unlike app crashes, these are caught errors surfaced in the UI
 * ("Something went wrong" on a manga page, empty chapters, etc.).
 * User flows (login, captcha, Cloudflare, proxy setup) are never reported.
 * Rate-limited per source + error type, so a dead source can't spam.
 */
@Singleton
class SourceErrorReporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
) {

	fun report(error: Throwable, source: MangaSource?) {
		if (!settings.isSourceErrorReportsEnabled) {
			return
		}
		if (!io.sentry.Sentry.isEnabled()) {
			return
		}
		if (!shouldReport(error)) {
			return
		}
		val key = "${source?.name ?: "unknown"}|${error.javaClass.simpleName}"
		if (!checkThrottle(key)) {
			return
		}
		runCatching {
			io.sentry.Sentry.captureException(error) { scope ->
				scope.setTag("kind", "source_error")
				scope.setTag("source", source?.name ?: "unknown")
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

	private fun shouldReport(error: Throwable): Boolean = when (error) {
		is CancellationException,
		is AuthRequiredException,
		is InteractiveActionRequiredException,
		is CloudFlareProtectedException,
		is ProxyConfigException,
		is NotFoundException,
		is SSLException,
		is SocketTimeoutException,
			-> false

		else -> true
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
		const val PREFS_NAME = "source_error_reports"
		const val MIN_INTERVAL_MS = 60 * 60 * 1000L
		const val MAX_MESSAGE_LENGTH = 500
	}
}
