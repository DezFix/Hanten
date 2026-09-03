package hanten.wre.app.core.crash

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.BuildConfig
import hanten.wre.app.R
import hanten.wre.app.core.network.BaseHttpClient
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.printStackTraceDebug
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends crash reports to the developer's Telegram chat via a dedicated bot.
 *
 * Works only when all of the following hold, otherwise it is a no-op:
 * - bot token and chat id are baked into the build (TG_CRASH_BOT_TOKEN / TG_CRASH_CHAT_ID),
 * - the user opted into crash reporting in settings,
 * - the last report was sent longer than [MIN_INTERVAL_MS] ago (spam protection).
 *
 * Never breaks the normal crash flow: the previous uncaught exception handler
 * is always invoked afterwards.
 */
@Singleton
class TelegramCrashReporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	@BaseHttpClient okHttp: OkHttpClient,
) {

	private val client = okHttp.newBuilder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.writeTimeout(10, TimeUnit.SECONDS)
		.readTimeout(10, TimeUnit.SECONDS)
		.callTimeout(20, TimeUnit.SECONDS)
		.build()

	fun install() {
		val token = context.getString(R.string.tg_crash_bot_token)
		val chatId = context.getString(R.string.tg_crash_chat_id)
		if (token.isEmpty() || chatId.isEmpty()) {
			return
		}
		val previous = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { thread, error ->
			runCatching {
				reportCrash(token, chatId, thread, error)
			}.onFailure {
				it.printStackTraceDebug("TelegramCrashReporter::reportCrash")
			}
			previous?.uncaughtException(thread, error)
		}
	}

	private fun reportCrash(token: String, chatId: String, thread: Thread, error: Throwable) {
		if (!settings.isCrashAnalyticsEnabled) {
			return
		}
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val now = System.currentTimeMillis()
		if (now - prefs.getLong(KEY_LAST_REPORT, 0L) < MIN_INTERVAL_MS) {
			return
		}
		prefs.edit().putLong(KEY_LAST_REPORT, now).apply()
		val url = "https://api.telegram.org/bot$token/sendMessage".toHttpUrl().newBuilder()
			.addQueryParameter("chat_id", chatId)
			.addQueryParameter("text", buildMessage(thread, error).take(MAX_MESSAGE_LENGTH))
			.addQueryParameter("disable_web_page_preview", "true")
			.build()
		val request = Request.Builder().url(url).get().build()
		client.newCall(request).execute().close()
	}

	private fun buildMessage(thread: Thread, error: Throwable): String {
		val stackTrace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
		return buildString {
			append("Hanten ")
			append(BuildConfig.VERSION_NAME)
			append(" (")
			append(BuildConfig.VERSION_CODE)
			appendLine(")")
			append("Android ")
			append(Build.VERSION.RELEASE)
			append(", sdk ")
			append(Build.VERSION.SDK_INT)
			append(", ")
			appendLine(Build.MODEL)
			append("Thread: ")
			appendLine(thread.name)
			append(stackTrace)
		}
	}

	private companion object {
		const val PREFS_NAME = "telegram_crash_reporter"
		const val KEY_LAST_REPORT = "last_report_time"
		const val MIN_INTERVAL_MS = 30 * 60 * 1000L
		const val MAX_MESSAGE_LENGTH = 3500
	}
}
