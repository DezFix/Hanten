package hanten.wre.app.core.network.webview.adblock

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.core.network.BaseHttpClient
import hanten.wre.app.core.network.CommonHeaders
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.isNotEmpty
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.parsers.util.await
import hanten.wre.app.parsers.util.requireBody
import hanten.wre.app.parsers.util.runCatchingCancellable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.sink
import java.io.File
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Reusable
class AdBlock @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
) {

	private var rules: RulesList? = null

	@WorkerThread
	fun shouldLoadUrl(url: String, baseUrl: String?): Boolean {
		return shouldLoadUrl(
			url.lowercase().toHttpUrlOrNull() ?: return true,
			baseUrl?.lowercase()?.toHttpUrlOrNull(),
		)
	}

	@WorkerThread
	fun shouldLoadUrl(url: HttpUrl, baseUrl: HttpUrl?): Boolean {
		if (!settings.isAdBlockEnabled) {
			return true
		}
		return synchronized(this) {
			rules ?: parseRules().also { rules = it }
		}?.let {
			val rule = it[url, baseUrl]
			if (rule != null) {
				Log.i(TAG, "Blocked $url by $rule")
			}
			rule == null
		} ?: true
	}

	@WorkerThread
	private fun parseRules() = runCatchingCancellable {
		val rules = RulesList()
		listsDir(context).listFiles { file ->
			file.isFile && file.extension == "txt"
		}?.sortedBy { it.name }?.forEach { file ->
			file.useLines { lines ->
				lines.forEach { line -> rules.add(line) }
			}
		}
		rules.trimToSize()
		rules
	}.onFailure { e ->
		e.printStackTraceDebug("AdBlock::parseRules")
	}.getOrNull()

	internal fun onListsUpdated() {
		synchronized(this) {
			rules = null
		}
	}

	class Updater @Inject constructor(
		@ApplicationContext private val context: Context,
		@BaseHttpClient private val okHttpClient: OkHttpClient,
		private val adBlock: AdBlock,
	) {

		suspend fun updateList() {
			LIST_URLS.forEach { (fileName, url) ->
				runCatchingCancellable {
					downloadList(url, File(listsDir(context), fileName))
				}.onFailure { e ->
					e.printStackTraceDebug("AdBlock::updateList $url")
				}
			}
			adBlock.onListsUpdated()
		}

		private suspend fun downloadList(url: String, file: File) {
			val dateFormat = SimpleDateFormat(CommonHeaders.DATE_FORMAT, Locale.ENGLISH)
			val requestBuilder = Request.Builder()
				.url(url)
				.get()
			if (file.exists() && file.isNotEmpty()) {
				val lastModified = file.lastModified()
				requestBuilder.header(CommonHeaders.IF_MODIFIED_SINCE, dateFormat.format(Date(lastModified)))
			}
			okHttpClient.newCall(
				requestBuilder.build(),
			).await().use { response ->
				if (response.code == HttpURLConnection.HTTP_NOT_MODIFIED) {
					return
				}
				val lastModified = response.header(CommonHeaders.LAST_MODIFIED)?.let {
					runCatching {
						dateFormat.parse(it)
					}.getOrNull()
				}?.time ?: System.currentTimeMillis()
				response.requireBody().source().use { source ->
					file.sink().use { sink ->
						source.readAll(sink)
					}
					file.setLastModified(lastModified)
				}
			}
		}

	}

	private companion object {

		fun listsDir(context: Context): File {
			val root = File(context.externalCacheDir ?: context.cacheDir, LIST_DIR)
			root.mkdir()
			return root
		}

		private const val LIST_DIR = "adblock"
		private const val TAG = "AdBlock"

		// (file name, url): EasyList + EasyPrivacy + RU AdList (our priority locales)
		private val LIST_URLS = arrayOf(
			"easylist.txt" to "https://easylist.to/easylist/easylist.txt",
			"easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt",
			"ruadlist.txt" to "https://easylist-downloads.adblockplus.org/ruadlist.txt",
		)
	}
}
