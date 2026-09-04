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

/**
 * Our own simple ad blocker.
 *
 * Blocks third-party requests to known ad/tracker/popunder/cryptominer
 * domains (suffix match). Deliberately minimal so it can never break
 * page logic:
 * - first-party requests (same host as the page) are never blocked,
 * - main frames are handled by the caller (see [BrowserClient]),
 * - no script injection, no cosmetic rules, no regex filters.
 */
@Reusable
class AdBlock @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
) {

	@Volatile
	private var suffixes: Set<String>? = null

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
		val host = url.host.lowercase()
		val pageHost = baseUrl?.host?.lowercase()
		if (pageHost != null && host == pageHost) {
			return true // never block first-party
		}
		val list = synchronized(this) {
			suffixes ?: loadSuffixes().also { suffixes = it }
		} ?: return true
		val blocked = list.any { suffix -> host == suffix || host.endsWith(".$suffix") }
		if (blocked) {
			Log.i(TAG, "Blocked $url (page: $baseUrl)")
		}
		return !blocked
	}

	@WorkerThread
	private fun loadSuffixes(): Set<String>? = runCatchingCancellable {
		val file = cachedFile(context)
		val lines = if (file.exists() && file.isNotEmpty()) {
			file.readLines()
		} else {
			context.assets.open(ASSET_NAME).bufferedReader().readLines()
		}
		lines.mapNotNullTo(HashSet()) { parseHost(it) }
	}.onFailure { e ->
		e.printStackTraceDebug("AdBlock::loadSuffixes")
	}.getOrNull()

	internal fun onListsUpdated() {
		synchronized(this) {
			suffixes = null
		}
	}

	class Updater @Inject constructor(
		@ApplicationContext private val context: Context,
		@BaseHttpClient private val okHttpClient: OkHttpClient,
		private val adBlock: AdBlock,
	) {

		suspend fun updateList() {
			runCatchingCancellable {
				downloadList(LIST_URL, cachedFile(context))
			}.onFailure { e ->
				e.printStackTraceDebug("AdBlock::updateList $LIST_URL")
			}
			// drop legacy EasyList-era files, they are superseded by adservers.txt
			cachedFile(context).parentFile?.listFiles { file ->
				file.isFile && file.name != CACHE_FILENAME
			}?.forEach { it.delete() }
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

		fun parseHost(line: String): String? {
			var s = line.trim().lowercase()
			if (s.isEmpty() || s.startsWith('#') || s.startsWith('!') || s.startsWith('[')) {
				return null
			}
			s = s.removePrefix("||").removePrefix("|").removePrefix("@@")
			s = s.substringBefore('$').substringBefore('/').substringBefore('^').trim().trimEnd('.')
			if (s.isEmpty() || '.' !in s || ' ' in s || '*' in s || '%' in s) {
				return null
			}
			return s
		}

		fun cachedFile(context: Context): File {
			val root = File(context.externalCacheDir ?: context.cacheDir, LIST_DIR)
			root.mkdir()
			return File(root, CACHE_FILENAME)
		}

		private const val LIST_DIR = "adblock"
		private const val CACHE_FILENAME = "adservers.txt"
		private const val ASSET_NAME = "adservers.txt"
		private const val LIST_URL =
			"https://raw.githubusercontent.com/DezFix/filters/refs/heads/main/adservers.txt"
		private const val TAG = "AdBlock"
	}
}
