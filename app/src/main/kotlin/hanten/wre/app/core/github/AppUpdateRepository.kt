package hanten.wre.app.core.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.R
import hanten.wre.app.core.network.BaseHttpClient
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.parsers.util.await
import hanten.wre.app.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext private val context: Context,
) {
	private val repo = context.getString(R.string.github_updates_repo)

	private val changelogUrl = buildString {
		append("https://raw.githubusercontent.com/")
		append(repo)
		append("/refs/heads/devel/CHANGELOG.md")
	}

	private val latestReleaseUrl = buildString {
		append("https://api.github.com/repos/")
		append(repo)
		append("/releases/latest")
	}

	suspend fun fetchChangelog(): String? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val request = Request.Builder()
				.get()
				.url(changelogUrl)
				.build()
			okHttp.newCall(request).await().body?.string()
		}.onFailure {
			it.printStackTraceDebug("AppUpdateRepository::fetchChangelog")
		}.getOrNull()
	}

	suspend fun fetchLatestRelease(): AppRelease? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val request = Request.Builder()
				.get()
				.url(latestReleaseUrl)
				.header("Accept", "application/vnd.github+json")
				.build()
			val body = okHttp.newCall(request).await().use { response ->
				if (!response.isSuccessful) return@runCatchingCancellable null
				response.body?.string()
			} ?: return@runCatchingCancellable null
			val json = org.json.JSONObject(body)
			if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
				return@runCatchingCancellable null
			}
			val tag = json.optString("tag_name").trim()
			if (tag.isEmpty()) return@runCatchingCancellable null
			var apkUrl: String? = null
			val assets = json.optJSONArray("assets")
			if (assets != null) {
				for (i in 0 until assets.length()) {
					val asset = assets.optJSONObject(i) ?: continue
					val name = asset.optString("name")
					if (name.endsWith(".apk", ignoreCase = true)) {
						apkUrl = asset.optString("browser_download_url").ifEmpty { null }
						break
					}
				}
			}
			AppRelease(
				tag = tag,
				changelog = json.optString("body").trim(),
				pageUrl = json.optString("html_url").ifEmpty { "https://github.com/$repo/releases" },
				apkUrl = apkUrl,
			)
		}.onFailure {
			it.printStackTraceDebug("AppUpdateRepository::fetchLatestRelease")
		}.getOrNull()
	}
}

data class AppRelease(
	val tag: String,
	val changelog: String,
	val pageUrl: String,
	val apkUrl: String?,
)
