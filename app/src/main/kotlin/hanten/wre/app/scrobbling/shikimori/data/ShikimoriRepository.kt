package hanten.wre.app.scrobbling.shikimori.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import hanten.wre.app.R
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.util.ext.toRequestBody
import hanten.wre.app.parsers.util.await
import hanten.wre.app.parsers.util.json.getStringOrNull
import hanten.wre.app.parsers.util.json.mapJSON
import hanten.wre.app.parsers.util.parseJson
import hanten.wre.app.parsers.util.parseJsonArray
import hanten.wre.app.parsers.util.parseRaw
import hanten.wre.app.parsers.util.toAbsoluteUrl
import hanten.wre.app.scrobbling.common.data.ScrobblerRepository
import hanten.wre.app.scrobbling.common.data.ScrobblerStorage
import hanten.wre.app.scrobbling.common.data.ScrobblingEntity
import hanten.wre.app.scrobbling.common.domain.ScrobblerAuthRequiredException
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerManga
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerMangaInfo
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerType
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerUser
import okio.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val DOMAIN = "shikimori.io"
private const val REDIRECT_URI = "hanten://shikimori-auth"
private const val BASE_URL = "https://$DOMAIN/"
private const val MANGA_PAGE_SIZE = 10
private const val RATING_MAX = 10

@Singleton
class ShikimoriRepository @Inject constructor(
	@ApplicationContext context: Context,
	@ScrobblerType(ScrobblerService.SHIKIMORI) private val okHttp: OkHttpClient,
	@ScrobblerType(ScrobblerService.SHIKIMORI) private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	private val clientId = context.getString(R.string.shikimori_clientId)
	private val clientSecret = context.getString(R.string.shikimori_clientSecret)

	override val oauthUrl: String
		get() = "${BASE_URL}oauth/authorize?client_id=$clientId&" +
			"redirect_uri=$REDIRECT_URI&response_type=code&scope=user_rates"

	override val isAuthorized: Boolean
		get() = storage.accessToken != null

	override suspend fun authorize(code: String?) {
		val body = FormBody.Builder()
		body.add("client_id", clientId)
		body.add("client_secret", clientSecret)
		if (code != null) {
			body.add("grant_type", "authorization_code")
			body.add("redirect_uri", REDIRECT_URI)
			body.add("code", code)
		} else {
			body.add("grant_type", "refresh_token")
			body.add("refresh_token", checkNotNull(storage.refreshToken))
		}
		val request = Request.Builder()
			.post(body.build())
			.url("${BASE_URL}oauth/token")
		val response = okHttp.newCall(request.build()).await().parseJson()
		check(response.has("access_token")) {
			"Shikimori auth failed: " + response.optString("error_description", response.optString("error", "unknown error"))
		}
		storage.accessToken = response.getString("access_token")
		if (response.has("refresh_token") && !response.isNull("refresh_token")) {
			storage.refreshToken = response.getString("refresh_token")
		}
		if (code != null) {
			// Fresh grants may carry no rights (e.g. approved long ago with an empty scope):
			// detect it immediately instead of looping on dead tokens.
			if (loadUserOrNull() == null) {
				storage.clear()
				throw IllegalStateException(
					"Shikimori denied access (no user_rates scope). " +
						"Revoke Hanten at shikimori.one -> settings -> applications and log in again.",
				)
			}
		}
	}

	override suspend fun loadUser(): ScrobblerUser {
		return loadUserOrNull() ?: reloadUser()
	}

	override val cachedUser: ScrobblerUser?
		get() {
			return storage.user
		}

	private suspend fun loadUserOrNull(): ScrobblerUser? {
		val request = Request.Builder()
			.get()
			.url("${BASE_URL}api/users/whoami")
		val response = okHttp.newCall(request.build()).await()
		// Shikimori answers HTTP 200 with a literal `null` body when the token is dead.
		// Parsing it as JSONObject crashes, so check first.
		val body = response.parseRaw()
		if (body.isBlank() || body.trim() == "null") {
			return null
		}
		return ShikimoriUser(JSONObject(body)).also { storage.user = it }
	}

	private suspend fun reloadUser(): ScrobblerUser {
		if (storage.refreshToken == null) {
			throw ScrobblerAuthRequiredException(ScrobblerService.SHIKIMORI)
		}
		authorize(null)
		return loadUserOrNull() ?: throw ScrobblerAuthRequiredException(ScrobblerService.SHIKIMORI)
	}

	override suspend fun unregister(mangaId: Long) {
		val entity = db.getScrobblingDao().find(ScrobblerService.SHIKIMORI.id, mangaId)
		if (entity != null) {
			// Removing the link only locally leaves the title in the user list on the website
			val url = BASE_URL.toHttpUrl().newBuilder()
				.addPathSegment("api")
				.addPathSegment("v2")
				.addPathSegment("user_rates")
				.addPathSegment(entity.id.toString())
				.build()
			val request = Request.Builder().url(url).delete().build()
			okHttp.newCall(request).await().use { }
		}
		db.getScrobblingDao().delete(ScrobblerService.SHIKIMORI.id, mangaId)
	}

	override fun logout() {
		storage.clear()
	}

	override suspend fun findManga(query: String, offset: Int): List<ScrobblerManga> {
		val page = offset / MANGA_PAGE_SIZE
		val pageOffset = offset % MANGA_PAGE_SIZE
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("mangas")
			.addEncodedQueryParameter("page", (page + 1).toString())
			.addEncodedQueryParameter("limit", MANGA_PAGE_SIZE.toString())
			.addEncodedQueryParameter("censored", false.toString())
			.addQueryParameter("search", query)
			.build()
		val request = Request.Builder().url(url).get().build()
		val response = okHttp.newCall(request).await().parseJsonArray()
		val list = response.mapJSON { ScrobblerManga(it, query) }
		return if (pageOffset != 0) list.drop(pageOffset) else list
	}

	override suspend fun createRate(mangaId: Long, scrobblerMangaId: Long) {
		val user = cachedUser ?: loadUser()
		val payload = JSONObject()
		payload.put(
			"user_rate",
			JSONObject().apply {
				put("target_id", scrobblerMangaId)
				put("target_type", "Manga")
				put("user_id", user.id)
			},
		)
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.build()
		val request = Request.Builder().url(url).post(payload.toRequestBody()).build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val payload = JSONObject()
		payload.put(
			"user_rate",
			JSONObject().apply {
				put("chapters", chapter)
			},
		)
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.addPathSegment(rateId.toString())
			.build()
		val request = Request.Builder().url(url).patch(payload.toRequestBody()).build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		// `score` is an integer column: a decimal string ("7.0") is not the same thing as 7 here
		val score = rating.roundToInt().coerceIn(0, RATING_MAX)
		val payload = JSONObject()
		payload.put(
			"user_rate",
			JSONObject().apply {
				put("score", score)
				if (comment != null) {
					put("text", comment)
				}
				if (status != null) {
					put("status", status)
				}
			},
		)
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.addPathSegment(rateId.toString())
			.build()
		val request = Request.Builder().url(url).patch(payload.toRequestBody()).build()
		val response = okHttp.newCall(request).await().parseJson()
		// The endpoint answers 200 even when a field was silently dropped, so never report success
		// on a score the user can not see on shikimori.one
		val applied = response.optInt("score", Int.MIN_VALUE)
		if (applied != score) {
			throw IOException("Shikimori did not save the score: sent $score, got $applied")
		}
		saveRate(response, mangaId)
	}

	/**
	 * Progress + status in a single PATCH. The rating overload deliberately carries no chapters,
	 * which left exported titles stuck at zero on the website.
	 */
	suspend fun updateRateWithProgress(rateId: Int, mangaId: Long, chapter: Int, status: String?) {
		val payload = JSONObject()
		payload.put(
			"user_rate",
			JSONObject().apply {
				put("chapters", chapter)
				if (status != null) {
					put("status", status)
				}
			},
		)
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.addPathSegment(rateId.toString())
			.build()
		val request = Request.Builder().url(url).patch(payload.toRequestBody()).build()
		val response = okHttp.newCall(request).await().parseJson()
		val applied = response.optInt("chapters", Int.MIN_VALUE)
		if (applied != chapter) {
			throw IOException("Shikimori did not save the progress: sent $chapter, got $applied")
		}
		saveRate(response, mangaId)
	}

	override suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo {
		val request = Request.Builder()
			.get()
			.url("${BASE_URL}api/mangas/$id")
		val response = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerMangaInfo(response)
	}

	suspend fun getUserRates(): List<ShikimoriUserRate> {
		val user = cachedUser ?: loadUser()
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.addQueryParameter("user_id", user.id.toString())
			.addQueryParameter("target_type", "Manga")
			.build()
		val request = Request.Builder().url(url).get().build()
		val response = okHttp.newCall(request).await().parseJsonArray()
		return response.mapJSON { jo ->
			ShikimoriUserRate(
				rateId = jo.getInt("id"),
				targetId = jo.getLong("target_id"),
				status = jo.getStringOrNull("status"),
				score = jo.optDouble("score", 0.0),
				chapters = jo.optInt("chapters", 0),
				comment = jo.getStringOrNull("text"),
			)
		}
	}

	suspend fun getMangaTitleVariants(targetId: Long): List<String> {
		val request = Request.Builder()
			.get()
			.url("${BASE_URL}api/mangas/$targetId")
		val response = okHttp.newCall(request.build()).await().parseJson()
		return listOfNotNull(
			response.getStringOrNull("name"),
			response.getStringOrNull("russian"),
		).filter { it.isNotBlank() }.distinct()
	}

	private suspend fun saveRate(json: JSONObject, mangaId: Long) {
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.SHIKIMORI.id,
			id = json.getInt("id"),
			mangaId = mangaId,
			targetId = json.getLong("target_id"),
			status = json.getStringOrNull("status"),
			chapter = json.optInt("chapters", 0),
			comment = json.getStringOrNull("text"),
			rating = (json.optDouble("score", 0.0).toFloat() / 10f).coerceIn(0f, 1f),
		)
		db.getScrobblingDao().upsert(entity)
	}

	private fun ScrobblerManga(json: JSONObject, sourceTitle: String) = ScrobblerManga(
		id = json.getLong("id"),
		name = json.getString("name"),
		altName = json.getStringOrNull("russian"),
		cover = json.optJSONObject("image")?.getStringOrNull("preview")?.toAbsoluteUrl(DOMAIN),
		url = json.getString("url").toAbsoluteUrl(DOMAIN),
		isBestMatch = sourceTitle.equals(json.getString("name"), ignoreCase = true)
			|| json.getStringOrNull("russian")?.equals(sourceTitle, ignoreCase = true) == true
	)

	private fun ScrobblerMangaInfo(json: JSONObject) = ScrobblerMangaInfo(
		id = json.getLong("id"),
		name = json.getString("name"),
		cover = json.optJSONObject("image")?.getStringOrNull("preview")?.toAbsoluteUrl(DOMAIN).orEmpty(),
		url = json.getStringOrNull("url")?.toAbsoluteUrl(DOMAIN).orEmpty(),
		descriptionHtml = json.getStringOrNull("description_html").orEmpty(),
	)

	@Suppress("FunctionName")
	private fun ShikimoriUser(json: JSONObject) = ScrobblerUser(
		id = json.getLong("id"),
		nickname = json.getString("nickname"),
		avatar = json.getStringOrNull("avatar"),
		service = ScrobblerService.SHIKIMORI,
	)
}

data class ShikimoriUserRate(
	val rateId: Int,
	val targetId: Long,
	val status: String?,
	val score: Double,
	val chapters: Int,
	val comment: String?,
)
