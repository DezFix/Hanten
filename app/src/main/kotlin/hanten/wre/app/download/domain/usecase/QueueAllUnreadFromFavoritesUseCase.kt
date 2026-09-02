package hanten.wre.app.download.domain.usecase

import androidx.work.WorkManager
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.download.ui.worker.DownloadSchedulerWorker
import hanten.wre.app.favourites.data.toManga
import hanten.wre.app.mihon.parsers.util.runCatchingCancellable
import javax.inject.Inject

class QueueAllUnreadFromFavoritesUseCase @Inject constructor(
    private val db: MangaDatabase,
    private val addUnreadToQueueUseCase: AddUnreadToQueueUseCase,
    private val workManager: WorkManager,
) {
    suspend operator fun invoke(wifiOnly: Boolean, chargingOnly: Boolean, offPeakOnly: Boolean) {
        runCatchingCancellable {
            val favorites = db.getFavouritesDao().findAll()
            favorites.forEach { favorite ->
                addUnreadToQueueUseCase(
                    manga = favorite.toManga(),
                    wifiOnly = wifiOnly,
                    chargingOnly = chargingOnly,
                    offPeakOnly = offPeakOnly,
                )
            }
            DownloadSchedulerWorker.enqueue(workManager)
        }.onFailure {
            it.printStackTraceDebug()
        }
    }
}
