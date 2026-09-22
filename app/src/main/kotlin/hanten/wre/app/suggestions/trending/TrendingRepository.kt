package hanten.wre.app.suggestions.trending

import hanten.wre.app.core.model.isNsfw
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.model.SortOrder
import hanten.wre.app.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Trending manga compiled from our own enabled sources, so the trends
 * automatically match the user's languages: RU sources give RU trends.
 */
class TrendingRepository @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val settings: AppSettings,
) {

	private var cache: List<Manga>? = null
	private var cachedAt: Long = 0L

	suspend fun getTrending(forceRefresh: Boolean): List<Manga> = withContext(Dispatchers.IO) {
		val now = System.currentTimeMillis()
		if (!forceRefresh) {
			cache?.takeIf { now - cachedAt < CACHE_TTL_MILLIS }?.let { return@withContext it }
		}
		val sources = sourcesRepository.getEnabledSources()
			.filterNot { it.isNsfw() && (settings.isSuggestionsExcludeNsfw || settings.isNsfwContentDisabled) }
			.shuffled()
			.take(MAX_SOURCES)
		if (sources.isEmpty()) {
			return@withContext emptyList()
		}
		val semaphore = Semaphore(MAX_PARALLELISM)
		val lists = supervisorScope {
			sources.map { source ->
				async {
					semaphore.withPermit { fetchTop(source) }
				}
			}.awaitAll()
		}
		val merged = lists.flatten()
			.groupBy { it.title.normalizeKey() }
			.map { (_, variants) -> variants.maxBy { it.rating } }
			.sortedByDescending { it.rating }
			.take(MAX_RESULTS)
		cache = merged
		cachedAt = now
		merged
	}

	private suspend fun fetchTop(source: MangaSource): List<Manga> = runCatchingCancellable {
		val repository = mangaRepositoryFactory.create(source)
		val order = TRENDING_ORDERS.firstOrNull { it in repository.sortOrders } ?: return@runCatchingCancellable emptyList()
		repository.getList(
			offset = 0,
			order = order,
			filter = MangaListFilter(),
		).take(MAX_PER_SOURCE)
	}.getOrDefault(emptyList())

	private fun String.normalizeKey() = lowercase().filter { it.isLetterOrDigit() }

	private companion object {

		const val CACHE_TTL_MILLIS = 30 * 60 * 1000L
		const val MAX_SOURCES = 10
		const val MAX_PER_SOURCE = 8
		const val MAX_RESULTS = 60
		const val MAX_PARALLELISM = 3

		val TRENDING_ORDERS = listOf(
			SortOrder.POPULARITY,
			SortOrder.RATING,
			SortOrder.NEWEST,
			SortOrder.UPDATED,
		)
	}
}
