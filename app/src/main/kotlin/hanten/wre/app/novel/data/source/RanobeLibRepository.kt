package hanten.wre.app.novel.data.source

import hanten.wre.app.core.cache.MemoryContentCache
import hanten.wre.app.parsers.model.ContentRating
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaListFilterCapabilities
import hanten.wre.app.parsers.model.MangaListFilterOptions
import hanten.wre.app.parsers.model.MangaPage
import hanten.wre.app.parsers.model.MangaState
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.parsers.model.SortOrder
import hanten.wre.app.parsers.util.formatSimple
import hanten.wre.app.parsers.util.longHashCode
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * RanobeLib (ranobelib.me, site id 3) is the novel branch of the same
 * lib.social API used by MangaLib. Unlike manga, chapter text lives in
 * `data.content`, so a dedicated repository is used instead of LibSocialParser.
 */
class RanobeLibRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.RANOBELIB) {

	private val baseUrl = source.defaultUrl
	private val apiBase = "https://api.cdnlibs.org/api"

	private val headers = mapOf(
		"Accept" to "application/json, text/plain, */*",
		"Referer" to "$baseUrl/",
		"Origin" to baseUrl,
		"Site-Id" to SITE_ID.toString(),
	)

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.RELEVANCE,
	)

	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isMultipleTagsSupported = true,
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
	)

	override suspend fun loadFilterOptions(): MangaListFilterOptions {
		val data = JSONObject(fetchString("$apiBase/constants?fields[]=genres&fields[]=tags", headers))
			.optJSONObject("data")
			?: return MangaListFilterOptions()
		val tags = LinkedHashSet<MangaTag>()
		tags += parseConstantTags(data.optJSONArray("genres"), "g")
		tags += parseConstantTags(data.optJSONArray("tags"), "t")
		return MangaListFilterOptions(
			availableTags = tags,
			availableStates = STATES,
		)
	}

	private fun parseConstantTags(array: JSONArray?, prefix: String): List<MangaTag> {
		if (array == null) return emptyList()
		val result = ArrayList<MangaTag>(array.length())
		for (i in 0 until array.length()) {
			val tag = array.optJSONObject(i) ?: continue
			if (!tag.supportsSite(SITE_ID)) continue
			val title = tag.optString("name").takeIf { it.isNotBlank() } ?: continue
			val id = tag.optInt("id", 0)
			if (id == 0) continue
			result += MangaTag(title = title, key = "$prefix$id", source = source)
		}
		return result
	}

	private fun JSONObject.supportsSite(siteId: Int): Boolean {
		val sites = optJSONArray("site_ids") ?: return false
		for (i in 0 until sites.length()) {
			if (sites.optInt(i) == siteId) return true
		}
		return false
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / PAGE_SIZE) + 1
		val builder = okhttp3.HttpUrl.Builder()
			.scheme("https")
			.host(API_HOST)
			.addPathSegment("api")
			.addPathSegment("manga")
			.addQueryParameter("site_id[]", SITE_ID.toString())
			.addQueryParameter("fields[]", "rate")
			.addQueryParameter("fields[]", "rate_avg")
			.addQueryParameter("page", page.toString())
			.addQueryParameter("limit", PAGE_SIZE.toString())
		filter?.query?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("q", it) }
		for (state in filter?.states.orEmpty()) {
			STATE_IDS[state]?.let { builder.addQueryParameter("status[]", it.toString()) }
		}
		for (tag in filter?.tags.orEmpty()) {
			when (tag.key.firstOrNull()) {
				'g' -> builder.addQueryParameter("genres[]", tag.key.drop(1))
				't' -> builder.addQueryParameter("tags[]", tag.key.drop(1))
			}
		}
		val (sortBy, sortType) = when (order ?: defaultSortOrder) {
			SortOrder.UPDATED -> "last_chapter_at" to "desc"
			SortOrder.NEWEST -> "created_at" to "desc"
			SortOrder.RELEVANCE -> null to null
			else -> "views" to "desc"
		}
		sortBy?.let { builder.addQueryParameter("sort_by", it) }
		sortType?.let { builder.addQueryParameter("sort_type", it) }
		val json = JSONObject(fetchString(builder.build().toString(), headers))
		val data = json.optJSONArray("data") ?: return emptyList()
		return buildList(data.length()) {
			for (i in 0 until data.length()) {
				data.optJSONObject(i)?.let { jo -> parseManga(jo)?.let(::add) }
			}
		}
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val url = "$apiBase/manga/${manga.url}" +
			"?fields[]=summary&fields[]=genres&fields[]=tags&fields[]=authors"
		val data = JSONObject(fetchString(url, headers)).optJSONObject("data")
			?: return manga
		val chapters = fetchChapters(manga.url)
		val cover = data.optJSONObject("cover")
		return manga.copy(
			altTitles = setOfNotNull(data.optString("name").takeIf { it.isNotBlank() }),
			rating = data.optJSONObject("rating")
				?.optString("average")?.toFloatOrNull()?.div(10f) ?: 0f,
			coverUrl = cover?.optString("thumbnail")?.takeIf { it.isNotBlank() },
			largeCoverUrl = cover?.optString("default")?.takeIf { it.isNotBlank() },
			description = data.optString("summary").takeIf { it.isNotBlank() },
			tags = parseTags(data),
			authors = parseAuthors(data),
			state = parseState(data),
			chapters = chapters,
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val data = JSONObject(fetchString("$apiBase/manga/${chapter.url}", headers))
			.optJSONObject("data")
			?: return emptyList()
		val content = data.optString("content").takeIf { it.isNotBlank() }
			?: data.optString("body").takeIf { it.isNotBlank() }
			?: return emptyList()
		return listOf(
			MangaPage(
				id = chapter.id,
				url = content,
				preview = null,
				source = source,
			),
		)
	}

	private suspend fun fetchChapters(slug: String): List<MangaChapter> {
		val data = JSONObject(fetchString("$apiBase/manga/$slug/chapters", headers))
			.optJSONArray("data")
			?: return emptyList()
		val result = ArrayList<MangaChapter>(data.length())
		for (i in 0 until data.length()) {
			val jo = data.optJSONObject(i) ?: continue
			val number = jo.optString("number").toFloatOrNull() ?: 0f
			val volume = jo.optString("volume").toIntOrNull() ?: 0
			val name = jo.optString("name").takeIf { it.isNotBlank() }
			val branches = jo.optJSONArray("branches") ?: continue
			for (j in 0 until branches.length()) {
				val branch = branches.optJSONObject(j) ?: continue
				val id = branch.optLong("id")
				if (id == 0L) continue
				val branchId = branch.optLong("branch_id")
				result += MangaChapter(
					id = id,
					title = name,
					number = number,
					volume = volume,
					url = buildString {
						append(slug)
						append("/chapter?number=")
						append(number.formatSimple())
						append("&volume=")
						append(volume)
						if (branchId != 0L) {
							append("&branch_id=")
							append(branchId)
						}
					},
					scanlator = branch.optJSONArray("teams")
						?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() },
					uploadDate = 0L,
					branch = branch.optJSONArray("teams")
						?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() },
					source = source,
				)
			}
		}
		return result.sortedBy { it.number }
	}

	private fun parseManga(jo: JSONObject): Manga? {
		val slug = jo.optString("slug_url").takeIf { it.isNotBlank() } ?: return null
		val cover = jo.optJSONObject("cover")
		val rating = jo.optJSONObject("rating")?.optString("average")?.toFloatOrNull()?.div(10f) ?: 0f
		return Manga(
			id = "${source.name}_$slug".longHashCode(),
			title = jo.optString("rus_name").ifBlank { jo.optString("name") },
			altTitles = setOfNotNull(jo.optString("name").takeIf { it.isNotBlank() }),
			url = slug,
			publicUrl = "$baseUrl/ru/ranobe/$slug",
			rating = rating,
			contentRating = if (jo.optJSONObject("ageRestriction")?.optInt("id", 0) ?: 0 >= 3) {
				ContentRating.ADULT
			} else {
				null
			},
			coverUrl = cover?.optString("thumbnail")?.takeIf { it.isNotBlank() },
			tags = emptySet(),
			state = parseState(jo),
			authors = emptySet(),
			largeCoverUrl = cover?.optString("default")?.takeIf { it.isNotBlank() },
			source = source,
		)
	}

	private fun parseState(jo: JSONObject): MangaState? = when (jo.optJSONObject("status")?.optInt("id")) {
		1 -> MangaState.ONGOING
		2 -> MangaState.FINISHED
		3 -> MangaState.UPCOMING
		4 -> MangaState.PAUSED
		5 -> MangaState.ABANDONED
		else -> null
	}

	private fun parseTags(jo: JSONObject): Set<MangaTag> {
		val result = LinkedHashSet<MangaTag>()
		for (key in arrayOf("genres", "tags")) {
			val array = jo.optJSONArray(key) ?: continue
			for (i in 0 until array.length()) {
				val tag = array.optJSONObject(i) ?: continue
				val title = tag.optString("name").takeIf { it.isNotBlank() } ?: continue
				result += MangaTag(
					title = title,
					key = "${key.first()}${tag.optInt("id")}",
					source = source,
				)
			}
		}
		return result
	}

	private fun parseAuthors(jo: JSONObject): Set<String> {
		val array = jo.optJSONArray("authors") ?: return emptySet()
		return buildSet {
			for (i in 0 until array.length()) {
				array.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
			}
		}
	}

	private companion object {
		const val API_HOST = "api.cdnlibs.org"
		const val SITE_ID = 3
		const val PAGE_SIZE = 20
		val STATES: Set<MangaState> = linkedSetOf(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.UPCOMING,
			MangaState.PAUSED,
			MangaState.ABANDONED,
		)
		val STATE_IDS: Map<MangaState, Int> = mapOf(
			MangaState.ONGOING to 1,
			MangaState.FINISHED to 2,
			MangaState.UPCOMING to 3,
			MangaState.PAUSED to 4,
			MangaState.ABANDONED to 5,
		)
	}
}
