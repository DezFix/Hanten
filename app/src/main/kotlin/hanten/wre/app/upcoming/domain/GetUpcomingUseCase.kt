package hanten.wre.app.upcoming.domain

import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaState
import javax.inject.Inject

class UpcomingItem(
	val manga: Manga,
	val forecast: UpcomingForecast.Result,
)

/** Library titles with an estimated next chapter, soonest first, late ones at the end */
class GetUpcomingUseCase @Inject constructor(
	private val db: MangaDatabase,
	private val favouritesRepository: FavouritesRepository,
) {

	suspend operator fun invoke(now: Long = System.currentTimeMillis()): List<UpcomingItem> {
		val library = favouritesRepository.getAllManga().associateBy { it.id }
		val chapters = db.getChaptersDao().getLibraryChapterDates().groupBy { it.mangaId }
		return chapters.mapNotNull { (mangaId, rows) ->
			val manga = library[mangaId] ?: return@mapNotNull null
			if (manga.state == MangaState.FINISHED || manga.state == MangaState.ABANDONED) {
				return@mapNotNull null
			}
			val forecast = UpcomingForecast.forecast(
				rows.map { UpcomingForecast.Chapter(it.uploadDate, it.number, it.branch) },
				now,
			) ?: return@mapNotNull null
			UpcomingItem(manga, forecast)
		}.sortedWith(compareBy<UpcomingItem> { it.forecast.isLate }.thenBy { it.forecast.nextDate })
	}
}
