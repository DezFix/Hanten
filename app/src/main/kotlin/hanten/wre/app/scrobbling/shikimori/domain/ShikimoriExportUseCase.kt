package hanten.wre.app.scrobbling.shikimori.domain

import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.list.domain.ReadingProgress
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.util.runCatchingCancellable
import hanten.wre.app.scrobbling.common.data.ScrobblingEntity
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import hanten.wre.app.scrobbling.shikimori.data.ShikimoriRepository
import hanten.wre.app.scrobbling.shikimori.data.ShikimoriUserRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One-way export of the local favourites library to Shikimori:
 * finds every title there, creates user rates and pushes reading progress.
 * Titles that are already linked are skipped.
 */
class ShikimoriExportUseCase @Inject constructor(
	private val repository: ShikimoriRepository,
	private val db: MangaDatabase,
	private val favouritesRepository: FavouritesRepository,
	private val historyRepository: HistoryRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	data class ExportResult(
		val exported: Int,
		val skipped: List<String>,
	)

	suspend fun exportLibrary(): ExportResult = withContext(Dispatchers.IO) {
		val favourites = favouritesRepository.getAllManga()
		if (favourites.isEmpty()) {
			return@withContext ExportResult(0, emptyList())
		}
		// Must not degrade to an empty map: Shikimori's create is an upsert, so treating every title
		// as new would push a zero score over an existing one on the website
		val existingRates = runCatchingCancellable {
			repository.getUserRates()
		}.getOrThrow().associateBy { it.targetId }
		val semaphore = Semaphore(MAX_PARALLELISM)
		val results = supervisorScope {
			favourites.take(MAX_TITLES).map { manga ->
				async {
					semaphore.withPermit {
						exportOne(manga, existingRates)
					}
				}
			}.awaitAll()
		}
		ExportResult(
			exported = results.count { it == null },
			skipped = results.filterNotNull(),
		)
	}

	/**
	 * @return title to report as skipped, or null on success
	 */
	private suspend fun exportOne(
		manga: Manga,
		existingRates: Map<Long, ShikimoriUserRate>,
	): String? {
		if (db.getScrobblingDao().find(ScrobblerService.SHIKIMORI.id, manga.id) != null) {
			return null // already linked, nothing to do (counts as exported)
		}
		val ourKeys = (setOf(manga.title) + manga.altTitles).mapTo(HashSet()) { it.normalizeKey() }
		val target = runCatchingCancellable {
			repository.findManga(manga.title, 0)
		}.getOrNull().orEmpty().firstOrNull { candidate ->
			val theirKeys = setOfNotNull(
				candidate.name.normalizeKey(),
				candidate.altName?.normalizeKey(),
			)
			ourKeys.any { it in theirKeys }
		} ?: return manga.title
		existingRates[target.id]?.let { rate ->
			linkExisting(manga, rate)
			return null
		}
		runCatchingCancellable {
			repository.createRate(manga.id, target.id)
		}.getOrNull() ?: return manga.title
		// Report a failed progress push instead of counting it as exported: the link exists now, so
		// a later run would skip this title and the remote progress would stay missing for good
		val pushed = runCatchingCancellable {
			pushProgress(manga)
		}.isSuccess
		return if (pushed) null else manga.title
	}

	private suspend fun linkExisting(manga: Manga, rate: ShikimoriUserRate) {
		db.getScrobblingDao().upsert(
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

	private suspend fun pushProgress(manga: Manga): Boolean {
		val history = historyRepository.getOne(manga) ?: return true
		val chapters = runCatchingCancellable {
			mangaRepositoryFactory.create(manga.source).getDetails(manga).chapters
		}.getOrNull() ?: return false
		if (chapters.isNullOrEmpty()) {
			return false
		}
		val index = chapters.indexOfFirst { it.id == history.chapterId }
		if (index < 0) {
			return false
		}
		val isLast = index == chapters.size - 1
		val completed = isLast && ReadingProgress.isCompleted(history.percent)
		val entity = db.getScrobblingDao().find(ScrobblerService.SHIKIMORI.id, manga.id) ?: return false
		// Chapter count and status go in one PATCH: the rating/status overload carries no chapters
		repository.updateRateWithProgress(
			rateId = entity.id,
			mangaId = manga.id,
			chapter = index + 1,
			status = if (completed) STATUS_COMPLETED else STATUS_WATCHING,
		)
		return true
	}

	private fun String.normalizeKey() = lowercase().filter { it.isLetterOrDigit() }

	private companion object {

		const val MAX_TITLES = 500
		const val MAX_PARALLELISM = 3
		const val STATUS_WATCHING = "watching"
		const val STATUS_COMPLETED = "completed"
	}
}
