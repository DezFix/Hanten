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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Ranobe.me — Russian online library with a plain HTML catalog, book pages and
 * chapter pages. Text is rendered from `div.ReadContent`.
 */
class RanobeMeRepository(
	cache: MemoryContentCache,
	httpClient: OkHttpClient,
) : BaseNovelRepository(cache, httpClient, NovelParserSource.RANOBE_ME) {

	private val baseUrl = source.defaultUrl

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.ALPHABETICAL,
		SortOrder.RELEVANCE,
	)

	override var defaultSortOrder: SortOrder = SortOrder.UPDATED

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isMultipleTagsSupported = false,
		isSearchSupported = true,
		isSearchWithFiltersSupported = false,
	)

	override suspend fun loadFilterOptions(): MangaListFilterOptions {
		val doc = fetchDocument("$baseUrl/catalog")
		val tags = LinkedHashMap<String, MangaTag>()
		for (link in doc.select("a[href^=/catalog/genre/]")) {
			val key = link.attr("href").substringAfterLast('/').trim()
			val title = link.text().trim()
			if (key.isEmpty() || title.isEmpty()) continue
			tags.putIfAbsent(key, MangaTag(title = title, key = key, source = source))
		}
		return MangaListFilterOptions(availableTags = tags.values.toSet())
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val page = (offset / PAGE_SIZE) + 1
		val query = filter?.query?.trim().orEmpty()
		val genre = filter?.tags?.firstOrNull()?.key
		val url = when {
			query.isNotEmpty() -> {
				val encoded = URLEncoder.encode(query, "UTF-8")
				"$baseUrl/index.php?section=search&str=$encoded&page=$page"
			}
			genre != null -> "$baseUrl/catalog/genre/$genre?page=$page"
			else -> {
				val sort = when (order ?: defaultSortOrder) {
					SortOrder.POPULARITY -> "stat"
					SortOrder.ALPHABETICAL -> "title"
					else -> null
				}
				if (sort != null) "$baseUrl/catalog?page=$page&sort=$sort" else "$baseUrl/catalog?page=$page"
			}
		}
		val doc = fetchDocument(url)
		val result = LinkedHashMap<Long, Manga>()
		for (card in doc.select(".FicTable")) {
			val manga = parseCard(card) ?: continue
			result.putIfAbsent(manga.id, manga)
		}
		return result.values.toList()
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val doc = fetchDocument(manga.url)
		val head = doc.selectFirst("div.FicHead") ?: doc
		val title = head.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: manga.title
		val coverElement = doc.selectFirst(".FicCover img")
		val cover = coverElement?.let { img ->
			img.absUrl("data-src").ifBlank { img.absUrl("src") }
		}?.takeIf { it.isNotBlank() }
		val altTitle = head.selectFirst(".FicHeadRight .tr .content")?.childNode(0)?.toString()?.trim()
			?.takeIf { it.isNotBlank() && it != title }
		val description = doc.selectFirst("[id^=summary_]")?.text()?.trim()?.takeIf { it.isNotBlank() }
		val tags = head.select("a[href^=/catalog/genre/]").mapNotNull { link ->
			val key = link.attr("href").substringAfterLast('/').trim()
			val name = link.text().trim()
			if (key.isEmpty() || name.isEmpty()) null else MangaTag(name, key, source)
		}.toSet()
		val authors = head.select("a[href^=/catalog/author/]").mapNotNull { link ->
			link.text().trim().takeIf { it.isNotBlank() }
		}.toSet()
		val chapters = parseChapters(doc)
		return manga.copy(
			title = title,
			altTitles = setOfNotNull(altTitle),
			coverUrl = cover,
			largeCoverUrl = cover,
			description = description,
			tags = tags,
			authors = authors,
			chapters = chapters,
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		val doc = fetchDocument(chapter.url)
		val content = doc.selectFirst("div.ReadContent div.chapter")
			?: doc.selectFirst("div.ReadContent")
			?: return emptyList()
		content.select("script, style, iframe, ins, .ads, .FullWidth, .read_nav_button").remove()
		for (image in content.select("img")) {
			val src = image.absUrl("data-src").ifBlank { image.absUrl("src") }
			if (src.isBlank()) {
				image.remove()
				continue
			}
			image.attr("src", src)
			image.removeAttr("data-src")
		}
		content.select("a[name]").forEach { it.removeAttr("name") }
		val html = content.html().trim()
		if (html.isEmpty()) {
			return emptyList()
		}
		return listOf(MangaPage(id = chapter.id, url = html, preview = null, source = source))
	}

	private fun parseCard(card: Element): Manga? {
		val link = card.selectFirst(".FicTable_Title a[href^=/ranobe]")
			?: card.selectFirst("a[href^=/ranobe]")
			?: return null
		val href = link.absUrl("href").ifBlank { "$baseUrl${link.attr("href")}" }
		val title = link.text().trim()
		if (title.isEmpty()) return null
		val cover = card.selectFirst(".FicTable_Cover img")?.absUrl("src")?.takeIf { it.isNotBlank() }
		return Manga(
			id = "${source.name}_$href".longHashCode(),
			title = title,
			altTitles = emptySet(),
			url = href,
			publicUrl = href,
			rating = 0f,
			contentRating = null,
			coverUrl = cover,
			tags = emptySet(),
			state = parseState(card),
			authors = emptySet(),
			largeCoverUrl = cover,
			source = source,
		)
	}

	private fun parseChapters(doc: Document): List<MangaChapter> {
		val result = ArrayList<MangaChapter>()
		val seen = HashSet<String>()
		for (link in doc.select(".FicContentsChapterName a[href^=/ranobe]")) {
			val href = link.absUrl("href").ifBlank { "$baseUrl${link.attr("href")}" }
			if (!seen.add(href)) continue
			val title = link.text().trim().takeIf { it.isNotBlank() } ?: continue
			result += MangaChapter(
				id = "${source.name}_$href".longHashCode(),
				title = title,
				number = href.substringAfterLast('/').toFloatOrNull()?.plus(1f) ?: (result.size + 1).toFloat(),
				volume = 0,
				url = href,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		}
		return result
	}

	private fun parseState(card: Element): MangaState? = when {
		card.selectFirst(".RanobeCompleted") != null -> MangaState.FINISHED
		card.selectFirst(".RanobeProcess, .RanobeOngoing") != null -> MangaState.ONGOING
		card.selectFirst(".RanobeFrozen, .RanobePaused") != null -> MangaState.PAUSED
		card.selectFirst(".RanobeAbandoned, .RanobeDropped") != null -> MangaState.ABANDONED
		else -> null
	}

	private companion object {
		const val PAGE_SIZE = 20
	}
}
