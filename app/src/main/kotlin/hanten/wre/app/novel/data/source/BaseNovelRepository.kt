package hanten.wre.app.novel.data.source

import hanten.wre.app.core.cache.MemoryContentCache
import hanten.wre.app.core.parser.CachingMangaRepository
import hanten.wre.app.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaListFilterCapabilities
import hanten.wre.app.parsers.model.MangaListFilterOptions
import hanten.wre.app.parsers.model.MangaPage
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.model.SortOrder
import hanten.wre.app.parsers.util.runCatchingCancellable

abstract class BaseNovelRepository(
	cache: MemoryContentCache,
	protected val httpClient: OkHttpClient,
	override val source: NovelParserSource,
) : CachingMangaRepository(cache) {

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.RELEVANCE,
	)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
	)

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

	protected suspend fun fetchDocument(
		url: String,
		headers: Map<String, String> = emptyMap(),
	): Document = withContext(Dispatchers.IO) {
		val requestBuilder = Request.Builder()
			.url(url)
			.tag(MangaSource::class.java, source)
			.header("User-Agent", DEFAULT_USER_AGENT)
			.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
			.header("Accept-Language", "en-US,en;q=0.9")

		for ((key, value) in headers) {
			requestBuilder.header(key, value)
		}

		httpClient.newCall(requestBuilder.build()).execute().use { response ->
			if (!response.isSuccessful) {
				throw RuntimeException("HTTP error ${response.code} fetching $url")
			}
			val body = response.body?.string().orEmpty()
			Jsoup.parse(body, url)
		}
	}

	protected suspend fun fetchString(
		url: String,
		headers: Map<String, String> = emptyMap(),
	): String = withContext(Dispatchers.IO) {
		val requestBuilder = Request.Builder()
			.url(url)
			.tag(MangaSource::class.java, source)
			.header("User-Agent", DEFAULT_USER_AGENT)
			.header("Accept", "*/*")

		for ((key, value) in headers) {
			requestBuilder.header(key, value)
		}

		httpClient.newCall(requestBuilder.build()).execute().use { response ->
			if (!response.isSuccessful) {
				throw RuntimeException("HTTP error ${response.code} fetching $url")
			}
			response.body?.string().orEmpty()
		}
	}

	protected suspend fun postJson(
		url: String,
		jsonBody: String,
		headers: Map<String, String> = emptyMap(),
	): String = withContext(Dispatchers.IO) {
		val mediaType = "application/json; charset=utf-8".toMediaType()
		val requestBody = jsonBody.toRequestBody(mediaType)
		val requestBuilder = Request.Builder()
			.url(url)
			.tag(MangaSource::class.java, source)
			.post(requestBody)
			.header("User-Agent", DEFAULT_USER_AGENT)
			.header("Accept", "application/json, text/javascript, */*; q=0.01")
			.header("Content-Type", "application/json;charset=UTF-8")

		for ((key, value) in headers) {
			requestBuilder.header(key, value)
		}

		httpClient.newCall(requestBuilder.build()).execute().use { response ->
			if (!response.isSuccessful) {
				throw RuntimeException("HTTP error ${response.code} posting to $url")
			}
			response.body?.string().orEmpty()
		}
	}

	companion object {
		const val DEFAULT_USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
	}
}