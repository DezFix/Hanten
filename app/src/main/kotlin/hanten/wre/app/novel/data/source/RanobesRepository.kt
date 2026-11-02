package hanten.wre.app.novel.data.source

import hanten.wre.app.core.cache.MemoryContentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaListFilterCapabilities
import hanten.wre.app.parsers.model.MangaPage
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.parsers.model.SortOrder
import hanten.wre.app.parsers.util.longHashCode

/**
 * Ranobes (ranobes.com). The site is a DLE engine without a public API:
 * `/ranobe/` is the catalog, `/popular.html` the all-time top, `/ranobe/{id}-{slug}.html`
 * the novel page and `/chapters/{slug}/page/{n}/` the paginated chapter list.
 * There is no server-side title search, so search is done locally by [SearchV2Helper].
 */
class RanobesRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.RANOBES) {

	private val baseUrl = "https://ranobes.com"

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
	)

	override var defaultSortOrder: SortOrder = SortOrder.NEWEST

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = false,
		isSearchWithFiltersSupported = false,
	)

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / PAGE_SIZE) + 1
		val url = when {
			(page > 1) -> "$baseUrl/ranobe/page/$page/"
			(order ?: defaultSortOrder) == SortOrder.POPULARITY -> "$baseUrl/popular.html"
			else -> "$baseUrl/ranobe/"
		}
		val doc = fetchDocument(url)
		val result = LinkedHashMap<Long, Manga>()
		for (article in doc.select("article")) {
			val manga = parseNovelItem(article) ?: continue
			result.putIfAbsent(manga.id, manga)
		}
		return result.values.toList()
	}

	private fun parseNovelItem(article: Element): Manga? {
		val link = article.select("a[href]").firstOrNull {
			NOVEL_PATH.matches(it.attr("href")) || NOVEL_PATH.matches(it.absUrl("href"))
		} ?: return null
		val href = link.absUrl("href").ifBlank {
			val rel = link.attr("href")
			if (rel.startsWith("/")) "$baseUrl$rel" else rel
		}
		val title = link.selectFirst(".title-limit")?.text()?.trim()
			?.takeIf { it.isNotEmpty() }
			?: link.text().trim().takeIf { it.isNotEmpty() }
			?: article.selectFirst("img[alt]")?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() }
			?: return null
		val cover = extractCoverUrl(article)
		val description = article.selectFirst(".text, .moreless__short, .moreless__full")
			?.text()?.trim()?.takeIf { it.isNotEmpty() }
		return Manga(
			id = "${source.name}_$href".longHashCode(),
			title = title,
			altTitles = emptySet(),
			url = href,
			publicUrl = href,
			rating = parseRating(article),
			contentRating = null,
			coverUrl = cover,
			tags = emptySet<MangaTag>(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = cover,
			description = description,
			chapters = null,
			source = source,
		)
	}

	private fun parseRating(article: Element): Float {
		val width = article.selectFirst(".current-rating")?.attr("style").orEmpty()
		val percent = Regex("""width:\s*(\d+)%""").find(width)?.groupValues?.get(1)?.toIntOrNull()
			?: return 0f
		return (percent / 10f).coerceIn(0f, 10f)
	}

	private fun extractCoverUrl(element: Element): String? {
		val img = element.selectFirst("img[src]")
		val src = img?.absUrl("src")?.ifEmpty { img.attr("src") }
		if (!src.isNullOrEmpty() && !src.startsWith("data:")) {
			return normalizeCoverUrl(src)
		}
		val style = element.selectFirst("[style*=background-image], [style*=background]")?.attr("style")
			.orEmpty()
		val match = Regex("""url\(['"]?(.*?)['"]?\)""").find(style) ?: return null
		val raw = match.groupValues[1]
		val abs = when {
			raw.startsWith("http://") || raw.startsWith("https://") -> raw
			raw.startsWith("//") -> "https:$raw"
			raw.startsWith("/") -> "$baseUrl$raw"
			else -> "$baseUrl/$raw"
		}
		return normalizeCoverUrl(abs)
	}

	private fun normalizeCoverUrl(url: String) = url.replace("/thumbs/", "/")

	/** Keeps old bookmarks (ranobes.net / ranobes.top) working. */
	private fun sanitizeUrl(url: String): String = url
		.replace("ranobes.net", "ranobes.com")
		.replace("ranobes.top", "ranobes.com")

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val novelUrl = sanitizeUrl(manga.url)
		val doc = fetchDocument(novelUrl)
		val poster = doc.selectFirst(".r-fullstory-poster, .poster")
		val cover = poster?.let(::extractCoverUrl) ?: manga.coverUrl
		val description = doc.selectFirst("#fs-info .r-desription .cont-text, .r-desription .cont-text, #fs-info")
			?.text()?.trim()?.takeIf { it.isNotEmpty() }
		val authors = doc.select("a[href*=/cloud/authors/], a[href*=/cloud/translater/]")
			.map { it.text().trim() }
			.filter { it.isNotEmpty() }
			.toSet()
		val chapters = fetchChapters(novelUrl)
		return manga.copy(
			url = novelUrl,
			publicUrl = novelUrl,
			title = doc.selectFirst("h1.title")?.ownText()?.trim()?.takeIf { it.isNotEmpty() } ?: manga.title,
			description = description,
			coverUrl = cover,
			largeCoverUrl = cover,
			authors = authors.ifEmpty { manga.authors },
			chapters = chapters,
		)
	}

	private suspend fun fetchChapters(novelUrl: String): List<MangaChapter> {
		val slug = novelUrl.substringAfterLast('/')
			.removeSuffix(".html")
			.substringAfter('-')
		if (slug.isEmpty()) return emptyList()
		val chaptersUrl = "$baseUrl/chapters/$slug/"
		val firstPage = runCatching { fetchDocument(chaptersUrl) }.getOrNull() ?: return emptyList()
		val lastPage = firstPage.select("a[href*=/page/]")
			.mapNotNull { Regex("""/page/(\d+)/""").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }
			.maxOrNull()
			?.coerceAtMost(MAX_CHAPTER_PAGES)
			?: 1
		val pages = coroutineScope {
			val semaphore = Semaphore(MAX_PARALLELISM)
			listOf(firstPage) + (2..lastPage).map { page ->
				async(Dispatchers.IO) {
					semaphore.withPermit {
						runCatching { fetchDocument("${chaptersUrl}page/$page/") }.getOrNull()
					}
				}
			}.map { deferred -> deferred.await() ?: firstPage }
		}
		val raw = ArrayList<Pair<String, String>>()
		for (doc in pages) {
			val items = doc.select("#dle-content .cat_line, .cat_block.cat_line")
			for (item in items) {
				val link = item.selectFirst("a[href]") ?: continue
				val href = link.absUrl("href").ifBlank { link.attr("href") }
				val title = link.selectFirst(".title")?.text()?.trim()
					?: link.attr("title").trim()
					?: continue
				raw += href to title
			}
		}
		// The list is newest-first; reverse to get chapter 1 .. N.
		return raw.asReversed()
			.distinctBy { it.first }
			.mapIndexed { index, (href, title) ->
				MangaChapter(
					id = "${source.name}_$href".longHashCode(),
					title = title,
					number = CHAPTER_NUMBER.find(title)?.groupValues?.get(1)
						?.replace(',', '.')?.toFloatOrNull() ?: (index + 1).toFloat(),
					volume = 0,
					url = href,
					uploadDate = 0L,
					source = source,
					scanlator = null,
					branch = null,
				)
			}
	}

	public override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val url = sanitizeUrl(chapter.url)
		val doc = fetchDocument(url)
		val content = doc.selectFirst("#arrticle, #article, .entry-content") ?: doc.body()
		content.select("script, style, iframe, .ads, .share, .free-support-top, [id^=bg-ssp]").remove()
		return listOf(
			MangaPage(
				id = chapter.id,
				url = content.html(),
				preview = null,
				source = source,
			),
		)
	}

	private companion object {
		const val PAGE_SIZE = 10
		const val MAX_CHAPTER_PAGES = 60
		const val MAX_PARALLELISM = 4
		val NOVEL_PATH = Regex("""(?:https?://ranobes\.(?:com|net|top))?/ranobe/\d+-[^/]+\.html""")
		val CHAPTER_NUMBER = Regex("""(?i)глав[аы]?\s*(\d+(?:[.,]\d+)?)""")
	}
}
