package hanten.wre.app.backups.ui.periodical

import android.content.Context
import androidx.annotation.CheckResult
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import hanten.wre.app.R
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.network.BaseHttpClient
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.parsers.util.await
import hanten.wre.app.parsers.util.json.getBooleanOrDefault
import hanten.wre.app.parsers.util.json.getStringOrNull
import hanten.wre.app.parsers.util.parseJson
import java.io.File
import javax.inject.Inject

class TelegramBackupUploader @Inject constructor(
	private val settings: AppSettings,
	@BaseHttpClient private val client: OkHttpClient,
	@ApplicationContext private val context: Context,
) {

	/**
	 * The bot belongs to the user: a token compiled into the APK is public and would let anyone
	 * read, write and delete the backups of every install. Read from settings on each call, so
	 * rotating the token takes effect immediately.
	 */
	private val botToken: String?
		get() = settings.backupTelegramToken

	val isAvailable: Boolean
		get() = botToken != null && settings.backupTelegramChatId != null

	suspend fun uploadBackup(file: File) {
		val requestBody = file.asRequestBody("application/zip".toMediaTypeOrNull())
		val multipartBody = MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("chat_id", requireChatId())
			.addFormDataPart("document", file.name, requestBody)
			.build()
		val request = Request.Builder()
			.url(urlOf("sendDocument").build())
			.post(multipartBody)
			.build()
		client.newCall(request).await().consume()
	}

	suspend fun sendTestMessage() {
		val request = Request.Builder()
			.url(urlOf("getMe").build())
			.build()
		val username = client.newCall(request).await().parseJson()
			.getJSONObject("result")
			.getStringOrNull("username")
		settings.backupTelegramBotName = username
		sendMessage(context.getString(R.string.backup_tg_echo))
	}

	/**
	 * Numeric chat ids are awkward to look up, and the bot already knows them: after the user
	 * presses Start, its pending updates carry the chat id.
	 */
	suspend fun discoverChatId(): Long? {
		val url = urlOf("getUpdates")
			.addQueryParameter("limit", "20")
			.addQueryParameter("timeout", "0")
			.build()
		val response = client.newCall(Request.Builder().url(url).build()).await().parseJson()
		val updates = response.optJSONArray("result") ?: return null
		var found: Long? = null
		for (i in 0 until updates.length()) {
			val chat = updates.optJSONObject(i)
				?.optJSONObject("message")
				?.optJSONObject("chat")
				?: continue
			val type = chat.getStringOrNull("type")
			if (type == "private") {
				found = chat.optLong("id")
			}
		}
		return found
	}

	@CheckResult
	fun openBotInApp(router: AppRouter): Boolean {
		val botUsername = settings.backupTelegramBotName ?: return false
		return router.openExternalBrowser("tg://resolve?domain=$botUsername") ||
			router.openExternalBrowser("https://t.me/$botUsername")
	}

	private suspend fun sendMessage(message: String) {
		val url = urlOf("sendMessage")
			.addQueryParameter("chat_id", requireChatId())
			.addQueryParameter("text", message)
			.build()
		val request = Request.Builder()
			.url(url)
			.build()
		client.newCall(request).await().consume()
	}

	private fun requireChatId() = checkNotNull(settings.backupTelegramChatId) {
		"Telegram chat ID not set in settings"
	}

	private fun requireToken() = checkNotNull(settings.backupTelegramToken) {
		"Telegram bot token not set in settings"
	}

	private fun Response.consume() {
		if (isSuccessful) {
			closeQuietly()
			return
		}
		val jo = parseJson()
		if (!jo.getBooleanOrDefault("ok", true)) {
			throw RuntimeException(jo.getStringOrNull("description"))
		}
	}

	private fun urlOf(method: String) = HttpUrl.Builder()
		.scheme("https")
		.host("api.telegram.org")
		.addPathSegment("bot${requireToken()}")
		.addPathSegment(method)
}
