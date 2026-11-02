package hanten.wre.app.core.parser

import android.net.Uri
import coil3.request.CachePolicy
import dagger.Reusable
import hanten.wre.app.core.model.MangaSource
import hanten.wre.app.core.model.UnknownMangaSource
import hanten.wre.app.core.model.isNsfw
import hanten.wre.app.core.util.ext.isHttpUrl
import hanten.wre.app.parsers.MangaLoaderContext
import hanten.wre.app.parsers.exception.NotFoundException
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.util.almostEquals
import hanten.wre.app.parsers.util.ifNullOrEmpty
import hanten.wre.app.parsers.util.levenshteinDistance
import hanten.wre.app.parsers.util.runCatchingCancellable
import javax.inject.Inject

@Reusable
class MangaLinkResolver @Inject constructor(
	private val repositoryFactory: MangaRepository.Factory,
	private val dataRepository: MangaDataRepository,
	private val context: MangaLoaderContext,
) {

	suspend fun resolve(uri: Uri): Manga {
		return if (uri.scheme == "hanten" || uri.scheme == "futon" || uri.host == "hanten.pages.dev" ||
			uri.host == "dezfix.github.io" || uri.host == "hanten-link.hantenapp.workers.dev" ||
			uri.host == "hanten.wre.app" || uri.host == "futonapp.pages.dev") {
			resolveAppLink(uri)
		} else {
			resolveExternalLink(uri.toString())
		} ?: throw NotFoundException("Cannot resolve link", uri.toString())
	}

	private suspend fun resolveAppLink(uri: Uri): Manga? {
		// https://dezfix.github.io/manga?... has path "manga"; the worker short
		// link uses "/m"; the custom scheme hanten://manga has the host instead.
		val path = uri.pathSegments.lastOrNull() ?: uri.host
		if (path != "manga" && path != "m") {
			return null
		}
		uri.getQueryParameter("id")?.toLongOrNull()?.let { mangaId ->
			// short url
			return dataRepository.findMangaById(mangaId, withChapters = false)
		}
		// short keys (s/u/n), with fallback to legacy full names
		val sourceName = (uri.getQueryParameter("s") ?: uri.getQueryParameter("source"))
			?.takeUnless { it.isBlank() }
			?: return null
		val source = MangaSource(sourceName)
		if (source == UnknownMangaSource) {
			return null
		}
		val repo = repositoryFactory.create(source)
		return repo.findExact(
			url = uri.getQueryParameter("u") ?: uri.getQueryParameter("url"),
			title = uri.getQueryParameter("n") ?: uri.getQueryParameter("name"),
		)
	}

	private suspend fun resolveExternalLink(uri: String): Manga? {
		dataRepository.findMangaByPublicUrl(uri)?.let {
			return it
		}
		return context.newLinkResolver(uri).getManga()
	}

	private suspend fun MangaRepository.findExact(url: String?, title: String?): Manga? {
		if (!title.isNullOrEmpty()) {
			val list = getList(0, null, MangaListFilter(query = title))
			if (url != null) {
				list.find { it.url == url }?.let {
					return it
				}
			}
			list.minByOrNull { it.title.levenshteinDistance(title) }
				?.takeIf { it.title.almostEquals(title, 0.2f) }
				?.let { return it }
		}
		val seed = getDetailsNoCache(
			getSeedManga(source, url ?: return null, title),
		)
		return runCatchingCancellable {
			val seedTitle = seed.title.ifEmpty {
				seed.altTitle
			}.ifNullOrEmpty {
				seed.author
			} ?: return@runCatchingCancellable null
			val seedList = getList(0, null, MangaListFilter(query = seedTitle))
			seedList.first { x -> x.url == url }
		}.getOrThrow()
	}

	private suspend fun MangaRepository.getDetailsNoCache(manga: Manga): Manga = if (this is CachingMangaRepository) {
		getDetails(manga, CachePolicy.READ_ONLY)
	} else {
		getDetails(manga)
	}

	private fun getSeedManga(source: MangaSource, url: String, title: String?) = Manga(
		id = run {
			var h = 1125899906842597L
			source.name.forEach { c ->
				h = 31 * h + c.code
			}
			url.forEach { c ->
				h = 31 * h + c.code
			}
			h
		},
		title = title.orEmpty(),
		altTitle = null,
		url = url,
		publicUrl = "",
		rating = 0.0f,
		isNsfw = source.isNsfw(),
		coverUrl = "",
		tags = emptySet(),
		state = null,
		author = null,
		largeCoverUrl = null,
		description = null,
		chapters = null,
		source = source,
	)

	companion object {

		fun isValidLink(str: String): Boolean {
			return str.isHttpUrl() || str.startsWith("futon://", ignoreCase = true) ||
				str.startsWith("hanten://", ignoreCase = true)
		}
	}
}
