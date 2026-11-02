package hanten.wre.app.novel.data.source

import hanten.wre.app.core.cache.MemoryContentCache
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaListFilterCapabilities
import hanten.wre.app.parsers.model.MangaListFilterOptions
import hanten.wre.app.parsers.model.MangaPage
import hanten.wre.app.parsers.model.MangaState
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.parsers.model.SortOrder
import hanten.wre.app.parsers.util.longHashCode
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

/**
 * Baka.in.ua — Ukrainian fan-translation library. The site has no public API,
 * so this repository uses the same HTML pages as the browser: /fictions for the
 * catalog, /fictions/genres/{slug} for a genre-filtered catalog, /fictions/{slug}
 * for details and /chapters/{slug} for chapter text.
 */
class BakaUaRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.BAKA_UA) {

	private val baseUrl = source.defaultUrl

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
	)

	override var defaultSortOrder: SortOrder = SortOrder.UPDATED

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isSearchWithFiltersSupported = true,
	)

	override suspend fun loadFilterOptions(): MangaListFilterOptions {
		val doc = fetchDocument("$baseUrl/fictions")
		val tags = LinkedHashMap<String, MangaTag>()
		for (link in doc.select("a[href^=/fictions/genres/]")) {
			// Genre cards and tag chips have a name; «more» arrows (no turbo frame, no <p>) do not.
			if (!link.hasAttr("data-turbo-frame") && link.selectFirst("p") == null) continue
			val slug = link.attr("href").substringAfterLast('/').trim()
			if (slug.isEmpty()) continue
			val title = link.selectFirst("p")?.text()?.trim()?.takeIf { it.isNotEmpty() }
				?: link.text().trim().takeIf { it.isNotEmpty() }
				?: continue
			tags.putIfAbsent(slug, MangaTag(title = title, key = slug, source = source))
		}
		return MangaListFilterOptions(availableTags = tags.values.toSet())
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		// Baka renders the whole catalog (or genre) page at once; extra pages are empty.
		if (offset > 0) {
			return emptyList()
		}
		val genre = filter?.tags?.firstOrNull()
		val url = if (genre != null) "$baseUrl/fictions/genres/${genre.key}" else "$baseUrl/fictions"
		val doc = fetchDocument(url)
		val query = filter?.query?.trim()
		val result = LinkedHashMap<Long, Manga>()
		for (article in doc.select("article")) {
			val link = article.selectFirst("p a[href^=/fictions/]")
				?: article.selectFirst("a[href^=/fictions/]")
				?: continue
			val relHref = link.attr("href")
			if (relHref.contains("/fictions/genres/") || relHref.contains("/fictions/calendar")) {
				continue
			}
			val href = link.absUrl("href").ifBlank { "$baseUrl$relHref" }
			val title = link.text().trim().ifBlank {
				article.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty()
			}
			if (title.isBlank()) {
				continue
			}
			if (!query.isNullOrEmpty() && !title.contains(query, ignoreCase = true)) {
				continue
			}
			val cover = article.selectFirst("img[src]")?.let { img ->
				img.absUrl("src").ifBlank { "$baseUrl${img.attr("src")}" }
			}?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
			val manga = Manga(
				id = "${source.name}_$href".longHashCode(),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href,
				rating = 0f,
				contentRating = null,
				coverUrl = cover,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				largeCoverUrl = cover,
				source = source,
			)
			result.putIfAbsent(manga.id, manga)
		}
		return result.values.toList()
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(manga.url)
		val title = doc.selectFirst("h1#fiction-title")?.text()?.trim()
			?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore('|')?.trim()
			?: manga.title
		val description = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
		val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")
			?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
		val author = doc.selectFirst("a[rel=author]")?.text()?.trim()?.takeIf { it.isNotBlank() }
		val chapters = doc.select("a[href^=/chapters/]")
			.mapNotNull { link ->
				val href = link.absUrl("href").ifBlank { "$baseUrl${link.attr("href")}" }
				val title = link.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
				MangaChapter(
					id = "${source.name}_$href".longHashCode(),
					title = title,
					number = 0f,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				)
			}
			.distinctBy { it.id }
			.withIndex()
			.map { (index, chapter) ->
				chapter.copy(number = (index + 1).toFloat())
			}
		return manga.copy(
			title = title,
			altTitles = emptySet(),
			coverUrl = cover,
			largeCoverUrl = cover,
			description = description,
			authors = setOfNotNull(author),
			state = MangaState.ONGOING,
			chapters = chapters,
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val doc = fetchDocument(chapter.url)
		val content: Element = doc.selectFirst("#user-content")
			?: doc.selectFirst("main")
			?: doc.body()
		content.select("script, style, iframe, ins, .ads, nav, header, footer").remove()
		val html = content.html().trim()
		if (html.isEmpty()) {
			return emptyList()
		}
		return listOf(
			MangaPage(
				id = chapter.id,
				url = html,
				preview = null,
				source = source,
			),
		)
	}
}
