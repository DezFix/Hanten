package hanten.wre.app.scrobbling.shikimori.domain

import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.list.domain.ReadingProgress
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.util.runCatchingCancellable
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import hanten.wre.app.scrobbling.shikimori.data.ShikimoriRepository
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
		val semaphore = Semaphore(MAX_PARALLELISM)
		val results = supervisorScope {
			favourites.take(MAX_TITLES).map { manga ->
				async {
					semaphore.withPermit {
						exportOne(manga)
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
	private suspend fun exportOne(manga: Manga): String? {
		if (db.getScrobblingDao().find(ScrobblerService.SHIKIMORI.id, manga.id) != null) {
			return null // already linked, nothing to do (counts as exported)
		}
		val target = runCatchingCancellable {
			repository.findManga(manga.title, 0)
		}.getOrNull().orEmpty().firstOrNull {
			it.name.normalizeKey() == manga.title.normalizeKey()
		} ?: return manga.title
		runCatchingCancellable {
			repository.createRate(manga.id, target.id)
		}.getOrNull() ?: return manga.title
		runCatchingCancellable {
			pushProgress(manga)
		}
		return null
	}

	private suspend fun pushProgress(manga: Manga) {
		val history = historyRepository.getOne(manga) ?: return
		val chapters = runCatchingCancellable {
			mangaRepositoryFactory.create(manga.source).getDetails(manga).chapters
		}.getOrNull() ?: return
		if (chapters.isNullOrEmpty()) {
			return
		}
		val index = chapters.indexOfFirst { it.id == history.chapterId }
		if (index < 0) {
			return
		}
		val isLast = index == chapters.size - 1
		val completed = isLast && ReadingProgress.isCompleted(history.percent)
		val entity = db.getScrobblingDao().find(ScrobblerService.SHIKIMORI.id, manga.id) ?: return
		repository.updateRate(
			rateId = entity.id,
			mangaId = manga.id,
			rating = 0f,
			status = if (completed) STATUS_COMPLETED else STATUS_WATCHING,
			comment = null,
		)
	}

	private fun String.normalizeKey() = lowercase().filter { it.isLetterOrDigit() }

	private companion object {

		const val MAX_TITLES = 500
		const val MAX_PARALLELISM = 3
		const val STATUS_WATCHING = "watching"
		const val STATUS_COMPLETED = "completed"
	}
}
