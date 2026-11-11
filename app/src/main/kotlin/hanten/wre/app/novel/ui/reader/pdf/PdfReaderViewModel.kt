package hanten.wre.app.novel.ui.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.parsers.util.longHashCode
import hanten.wre.app.core.model.LocalNovelSource
import hanten.wre.app.core.model.parcelable.ParcelableManga
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.nav.ReaderIntent
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.call
import hanten.wre.app.core.util.ext.isFileUri
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.toFileOrNull
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.novel.data.pdf.PdfRendererHelper
import hanten.wre.app.reader.ui.ReaderState
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaDataRepository: MangaDataRepository,
	private val historyRepository: HistoryRepository,
	@ApplicationContext private val context: Context,
) : BaseViewModel() {

	private val initialManga: Manga? = savedStateHandle.get<ParcelableManga>(AppRouter.KEY_MANGA)?.manga
	private val mangaId: Long = savedStateHandle.get<Long>(AppRouter.KEY_ID) ?: initialManga?.id ?: 0L
	private val dataUri: Uri? = savedStateHandle.get<Uri>(AppRouter.KEY_DATA)
	private val initialState: ReaderState? = savedStateHandle.get<ReaderState>(ReaderIntent.EXTRA_STATE)
	private val isIncognito: Boolean =
		savedStateHandle.get<Boolean>(ReaderIntent.EXTRA_INCOGNITO) ?: false

	private val _manga = MutableStateFlow<Manga?>(initialManga)
	val manga: StateFlow<Manga?> = _manga.asStateFlow()

	private val _pageCount = MutableStateFlow(0)
	val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

	private val _currentPage = MutableStateFlow(0)
	val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

	val onJumpToPage = MutableEventFlow<Int>()

	private var pdfHelper: PdfRendererHelper? = null

	init {
		loadPdf()
	}

	override fun onCleared() {
		super.onCleared()
		pdfHelper?.close()
		pdfHelper = null
	}

	fun getPdfHelper(): PdfRendererHelper? = pdfHelper

	fun loadPdf() {
		launchLoadingJob(Dispatchers.IO) {
			try {
				var current = _manga.value
				if (current == null && mangaId != 0L) {
					current = mangaDataRepository.findMangaById(mangaId, withChapters = true)
					_manga.value = current
				}

				val helper = when {
					current != null -> {
						val url = current.url
						when {
							url.startsWith("file://") -> PdfRendererHelper(File(url.removePrefix("file://")))
							url.startsWith("content://") -> PdfRendererHelper(context, Uri.parse(url))
							File(url).exists() -> PdfRendererHelper(File(url))
							else -> runCatching { PdfRendererHelper(context, Uri.parse(url)) }.getOrNull()
						}
					}
					dataUri != null -> {
						if (dataUri.isFileUri()) {
							val file = dataUri.toFileOrNull()
							if (file != null && file.exists()) {
								PdfRendererHelper(file)
							} else {
								PdfRendererHelper(context, dataUri)
							}
						} else {
							PdfRendererHelper(context, dataUri)
						}
					}
					else -> null
				}

				if (!isActive) {
					helper?.close()
					return@launchLoadingJob
				}

				if (pdfHelper !== helper) {
					pdfHelper?.close()
					pdfHelper = helper
				}
				val count = helper?.pageCount ?: 0
				_pageCount.value = count

				if (current == null && helper != null) {
					val docTitle = dataUri?.lastPathSegment?.substringBeforeLast('.') ?: "PDF Document"
					val chapters = (0 until count).map { pageIdx ->
						MangaChapter(
							id = pageIdx.toLong(),
							title = "Page ${pageIdx + 1}",
							number = (pageIdx + 1).toFloat(),
							volume = 0,
							url = "pdf://page/$pageIdx",
							uploadDate = 0L,
							source = LocalNovelSource,
							scanlator = null,
							branch = null,
						)
					}
					current = Manga(
						id = dataUri?.toString()?.longHashCode() ?: System.currentTimeMillis(),
						title = docTitle,
						altTitle = null,
						url = dataUri?.toString().orEmpty(),
						publicUrl = dataUri?.toString().orEmpty(),
						rating = -1f,
						isNsfw = false,
						coverUrl = "",
						tags = emptySet<MangaTag>(),
						state = null,
						author = null,
						largeCoverUrl = null,
						description = null,
						chapters = chapters,
						source = LocalNovelSource,
					)
					_manga.value = current
				} else if (current != null && (current.chapters.isNullOrEmpty() || current.chapters?.size != count)) {
					val chapters = (0 until count).map { pageIdx ->
						MangaChapter(
							id = pageIdx.toLong(),
							title = "Page ${pageIdx + 1}",
							number = (pageIdx + 1).toFloat(),
							volume = 0,
							url = "pdf://page/$pageIdx",
							uploadDate = 0L,
							source = current.source,
							scanlator = null,
							branch = null,
						)
					}
					current = current.copy(chapters = chapters)
					_manga.value = current
				}

				val resumePage = when {
					initialState?.page != null && initialState.page in 0 until count -> initialState.page
					initialState?.chapterId != null && initialState.chapterId.toInt() in 0 until count -> initialState.chapterId.toInt()
					current != null -> {
						val history = historyRepository.getOne(current)
						when {
							history?.page != null && history.page in 0 until count -> history.page
							history?.chapterId != null && history.chapterId.toInt() in 0 until count -> history.chapterId.toInt()
							else -> 0
						}
					}
					else -> 0
				}

				_currentPage.value = resumePage
				if (count > 0) {
					onJumpToPage.call(resumePage)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
			}
		}
	}

	fun onPageChanged(page: Int) {
		val count = _pageCount.value
		if (count <= 0 || page !in 0 until count) return
		if (_currentPage.value == page) return
		_currentPage.value = page
		saveReadingProgress(page)
	}

	fun jumpToPage(page: Int) {
		val count = _pageCount.value
		if (count <= 0) return
		val clamped = page.coerceIn(0, count - 1)
		_currentPage.value = clamped
		saveReadingProgress(clamped)
		onJumpToPage.call(clamped)
	}

	fun switchPage(delta: Int) {
		jumpToPage(_currentPage.value + delta)
	}

	private fun saveReadingProgress(page: Int) {
		val cur = _manga.value ?: return
		if (isIncognito || historyRepository.shouldSkip(cur)) return
		val count = _pageCount.value
		val percent = if (count > 0) {
			((page + 1).toFloat() / count.toFloat()).coerceIn(0f, 1f)
		} else {
			0f
		}
		viewModelScope.launch(Dispatchers.IO) {
			val chaps = cur.chapters ?: (0 until count).map { pageIdx ->
				MangaChapter(
					id = pageIdx.toLong(),
					title = "Page ${pageIdx + 1}",
					number = (pageIdx + 1).toFloat(),
					volume = 0,
					url = "pdf://page/$pageIdx",
					uploadDate = 0L,
					source = cur.source,
					scanlator = null,
					branch = null,
				)
			}
			try {
				historyRepository.addOrUpdate(
					manga = cur.copy(chapters = chaps),
					chapterId = page.toLong(),
					page = page,
					scroll = 0,
					percent = percent,
					force = false,
				)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
			}
		}
	}
}