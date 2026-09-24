package hanten.wre.app.scrobbling.mal.data

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import hanten.wre.app.R
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.parsers.util.await
import hanten.wre.app.parsers.util.json.getStringOrNull
import hanten.wre.app.parsers.util.json.mapJSONNotNull
import hanten.wre.app.parsers.util.parseJson
import hanten.wre.app.scrobbling.common.data.ScrobblerRepository
import hanten.wre.app.scrobbling.common.data.ScrobblerStorage
import hanten.wre.app.scrobbling.common.data.ScrobblingEntity
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerManga
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerMangaInfo
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerType
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerUser
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val REDIRECT_URI = "hanten://mal-auth"
private const val BASE_WEB_URL = "https://myanimelist.net"
private const val BASE_API_URL = "https://api.myanimelist.net/v2"

@Singleton
class MALRepository @Inject constructor(
	@ApplicationContext context: Context,
	@ScrobblerType(ScrobblerService.MAL) private val okHttp: OkHttpClient,
	@ScrobblerType(ScrobblerService.MAL) private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	private val clientId = context.getString(R.string.mal_clientId)
	private val codeVerifier: String by lazy(::generateCodeVerifier)

	override val oauthUrl: String
		get() = "$BASE_WEB_URL/v1/oauth2/authorize?" +
			"response_type=code" +
			"&client_id=$clientId" +
			"&redirect_uri=$REDIRECT_URI" +
			"&code_challenge=$codeVerifier" +
			"&code_challenge_method=plain"

	override val isAuthorized: Boolean
		get() = storage.accessToken != null

	override val cachedUser: ScrobblerUser?
		get() {
			return storage.user
		}

	override suspend fun authorize(code: String?) {
		val body = FormBody.Builder()
		if (code != null) {
			body.add("client_id", clientId)
			body.add("grant_type", "authorization_code")
			body.add("code", code)
			body.add("redirect_uri", REDIRECT_URI)
			body.add("code_verifier", codeVerifier)
		} else {
			// Refresh grant: without these two fields the request is an empty POST and a dead
			// session can never recover
			body.add("client_id", clientId)
			body.add("grant_type", "refresh_token")
			body.add("refresh_token", checkNotNull(storage.refreshToken) { "MAL refresh token is missing" })
		}
		val request = Request.Builder()
			.post(body.build())
			.url("${BASE_WEB_URL}/v1/oauth2/token")

		val response = okHttp.newCall(request.build()).await().parseJson()
		check(response.has("access_token")) {
			"MAL auth failed: " + response.optString("error_description", response.optString("error", "unknown error"))
		}
		storage.accessToken = response.getString("access_token")
		if (response.has("refresh_token") && !response.isNull("refresh_token")) {
			storage.refreshToken = response.getString("refresh_token")
		}
	}

	override suspend fun loadUser(): ScrobblerUser {
		val request = Request.Builder()
			.get()
			.url("${BASE_API_URL}/users/@me")
		val response = okHttp.newCall(request.build()).await().parseJson()
		return MALUser(response).also { storage.user = it }
	}

	override suspend fun unregister(mangaId: Long) {
		val entity = db.getScrobblingDao().find(ScrobblerService.MAL.id, mangaId)
		if (entity != null) {
			// Removing the link only locally leaves the title in the user list on the website
			val url = BASE_API_URL.toHttpUrl().newBuilder()
				.addPathSegment("v2")
				.addPathSegment("manga")
				.addPathSegment(entity.id.toString())
				.addPathSegment("my_list_status")
				.build()
			val request = Request.Builder().url(url).delete().build()
			okHttp.newCall(request).await().use { }
		}
		db.getScrobblingDao().delete(ScrobblerService.MAL.id, mangaId)
	}

	override suspend fun findManga(query: String, offset: Int): List<ScrobblerManga> {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("manga")
			.addQueryParameter("offset", offset.toString())
			.addQueryParameter("nsfw", "true")
			// WARNING! MAL API throws a 400 when the query is over 64 characters
			.addQueryParameter("q", query.take(64))
			.build()
		val request = Request.Builder().url(url).get().build()
		val response = okHttp.newCall(request).await().parseJson()
		check(response.has("data")) { "Invalid response: \"$response\"" }
		val data = response.getJSONArray("data")
		return data.mapJSONNotNull { jsonToManga(it, query) }
	}

	override suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("manga")
			.addPathSegment(id.toString())
			.addQueryParameter("fields", "synopsis")
			.build()
		val request = Request.Builder().url(url)
		val response = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerMangaInfo(response)
	}

	override suspend fun createRate(mangaId: Long, scrobblerMangaId: Long) {
		val body = FormBody.Builder()
			.add("status", "reading")
			.add("score", "0")
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("manga")
			.addPathSegment(scrobblerMangaId.toString())
			.addPathSegment("my_list_status")
			.addQueryParameter("fields", "synopsis")
			.build()
		val request = Request.Builder()
			.url(url)
			.put(body.build())
			.build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId, scrobblerMangaId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val body = FormBody.Builder()
			.add("num_chapters_read", chapter.toString())
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("manga")
			.addPathSegment(rateId.toString())
			.addPathSegment("my_list_status")
			.build()
		val request = Request.Builder()
			.url(url)
			.put(body.build())
			.build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId, rateId.toLong())
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val body = FormBody.Builder()
		// `status` and `comment` are optional on MAL: sending a literal "null" or wiping an
		// existing note during an unrelated score update is worse than omitting the field
		if (status != null) {
			body.add("status", status)
		}
		body.add("score", rating.roundToInt().toString())
		if (comment != null) {
			body.add("comments", comment)
		}
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("manga")
			.addPathSegment(rateId.toString())
			.addPathSegment("my_list_status")
			.build()
		val request = Request.Builder()
			.url(url)
			.put(body.build())
			.build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId, rateId.toLong())
	}

	private suspend fun saveRate(json: JSONObject, mangaId: Long, scrobblerMangaId: Long) {
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.MAL.id,
			id = scrobblerMangaId.toInt(),
			mangaId = mangaId,
			targetId = scrobblerMangaId,
			status = json.getStringOrNull("status"),
			chapter = json.optInt("num_chapters_read", 0),
			comment = json.getStringOrNull("comments"),
			rating = (json.optDouble("score", 0.0).toFloat() / 10f).coerceIn(0f, 1f),
		)
		db.getScrobblingDao().upsert(entity)
	}

	override fun logout() {
		storage.clear()
	}

	private fun jsonToManga(json: JSONObject, sourceTitle: String): ScrobblerManga {
		val node = json.getJSONObject("node")
		val title = node.getString("title")
		return ScrobblerManga(
			id = node.getLong("id"),
			name = title,
			altName = null,
			cover = node.optJSONObject("main_picture")?.getStringOrNull("large"),
			url = "$BASE_WEB_URL/manga/${node.getLong("id")}",
			isBestMatch = title.equals(sourceTitle, ignoreCase = true),
		)
	}

	private fun ScrobblerMangaInfo(json: JSONObject) = ScrobblerMangaInfo(
		id = json.getLong("id"),
		name = json.getString("title"),
		cover = json.optJSONObject("main_picture")?.getStringOrNull("large").orEmpty(),
		url = "$BASE_WEB_URL/manga/${json.getLong("id")}",
		descriptionHtml = json.getStringOrNull("synopsis").orEmpty(),
	)

	@Suppress("FunctionName")
	private fun MALUser(json: JSONObject) = ScrobblerUser(
		id = json.getLong("id"),
		nickname = json.getString("name"),
		avatar = json.getStringOrNull("picture"),
		service = ScrobblerService.MAL,
	)

	private fun generateCodeVerifier(): String {
		val codeVerifier = ByteArray(50)
		SecureRandom().nextBytes(codeVerifier)
		return Base64.encodeToString(codeVerifier, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
	}
}
