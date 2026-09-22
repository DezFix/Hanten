package hanten.wre.app.scrobbling.shikimori.domain

import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.list.domain.ListSortOrder
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.util.runCatchingCancellable
import hanten.wre.app.scrobbling.common.data.ScrobblingEntity
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import hanten.wre.app.scrobbling.shikimori.data.ShikimoriRepository
import hanten.wre.app.scrobbling.shikimori.data.ShikimoriUserRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One-time import of the user's Shikimori manga library:
 * matches every title against enabled sources, puts matches into
 * a favourites category and stores tracking links (status, score,
 * chapters) so two-way sync works from day one.
 */
class ShikimoriImportUseCase @Inject constructor(
	private val repository: ShikimoriRepository,
	private val db: MangaDatabase,
	private val favouritesRepository: FavouritesRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	data class ImportResult(
		val imported: Int,
		val skipped: List<String>,
	)

	suspend fun importLibrary(categoryTitle: String): ImportResult = withContext(Dispatchers.IO) {
		val rates = repository.getUserRates()
		if (rates.isEmpty()) {
			return@withContext ImportResult(0, emptyList())
		}
		val sources = sourcesRepository.getEnabledSources().take(MAX_SOURCES)
		if (sources.isEmpty()) {
			return@withContext ImportResult(0, rates.map { "rate#${it.targetId}" })
		}
		val semaphore = Semaphore(MAX_PARALLELISM)
		val resolved = supervisorScope {
			rates.take(MAX_RATES).map { rate ->
				async {
					semaphore.withPermit {
						val titles = runCatchingCancellable {
							repository.getMangaTitleVariants(rate.targetId)
						}.getOrNull().orEmpty()
						if (titles.isEmpty()) {
							return@withPermit null to rate
						}
						val match = findMatch(titles, sources)
						if (match == null) {
							null to rate
						} else {
							match to null
						}
					}
				}
			}.awaitAll()
		}
		val matched = resolved.mapNotNull { (manga, rate) ->
			if (manga != null && rate != null) manga to rate else null
		}
		val skipped = resolved.mapNotNull { (manga, rate) ->
			if (manga == null) rate else null
		}
		if (matched.isNotEmpty()) {
			val categoryId = findOrCreateCategory(categoryTitle)
			favouritesRepository.addToCategory(categoryId, matched.map { it.first })
			val dao = db.getScrobblingDao()
			matched.forEach { (manga, rate) ->
				dao.upsert(
					ScrobblingEntity(
						scrobbler = ScrobblerService.SHIKIMORI.id,
						id = rate.rateId,
						mangaId = manga.id,
						targetId = rate.targetId,
						status = rate.status,
						chapter = rate.chapters,
						comment = rate.comment,
						rating = (rate.score.toFloat() / 10f).coerceIn(0f, 1f),
					),
				)
			}
		}
		val skippedTitles = supervisorScope {
			skipped.map { rate ->
				async {
					runCatchingCancellable {
						repository.getMangaTitleVariants(rate.targetId).firstOrNull()
					}.getOrNull() ?: "id:${rate.targetId}"
				}
			}.awaitAll()
		}
		ImportResult(matched.size, skippedTitles)
	}

	private suspend fun findMatch(titles: List<String>, sources: List<MangaSource>): Manga? {
		val keys = titles.mapTo(HashSet()) { it.normalizeKey() }
		for (source in sources) {
			val found = runCatchingCancellable {
				val repository = mangaRepositoryFactory.create(source)
				if (!repository.filterCapabilities.isSearchSupported) {
					return@runCatchingCancellable null
				}
				for (query in titles) {
					val result = repository.getList(0, null, MangaListFilter(query = query))
						.firstOrNull { it.title.normalizeKey() in keys }
					if (result != null) {
						return@runCatchingCancellable result
					}
				}
				null
			}.getOrNull() ?: continue
			return found
		}
		return null
	}

	private suspend fun findOrCreateCategory(title: String): Long {
		favouritesRepository.observeCategories().first()
			.firstOrNull { it.title.equals(title, ignoreCase = true) }?.let { return it.id }
		return favouritesRepository.createCategory(
			title = title,
			sortOrder = ListSortOrder.NEWEST,
			isTrackerEnabled = true,
			isVisibleOnShelf = true,
		).id
	}

	private fun String.normalizeKey() = lowercase().filter { it.isLetterOrDigit() }

	private companion object {

		const val MAX_SOURCES = 12
		const val MAX_RATES = 500
		const val MAX_PARALLELISM = 4
	}
}
