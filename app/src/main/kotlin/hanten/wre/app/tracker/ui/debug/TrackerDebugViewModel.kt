package hanten.wre.app.tracker.ui.debug

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.db.entity.toManga
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.ext.toInstantOrNull
import hanten.wre.app.tracker.data.TrackWithManga
import javax.inject.Inject

@HiltViewModel
class TrackerDebugViewModel @Inject constructor(
	db: MangaDatabase
) : BaseViewModel() {

	val content = db.getTracksDao().observeAll()
		.map { it.toUiList() }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	private fun List<TrackWithManga>.toUiList(): List<TrackDebugItem> = map {
		TrackDebugItem(
			manga = it.manga.toManga(emptySet(), null),
			lastChapterId = it.track.lastChapterId,
			newChapters = it.track.newChapters,
			lastCheckTime = it.track.lastCheckTime.toInstantOrNull(),
			lastChapterDate = it.track.lastChapterDate.toInstantOrNull(),
			lastResult = it.track.lastResult,
			lastError = it.track.lastError,
		)
	}
}
