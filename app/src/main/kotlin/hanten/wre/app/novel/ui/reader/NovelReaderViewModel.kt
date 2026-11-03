package hanten.wre.app.novel.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hanten.wre.app.core.model.isNsfw
import hanten.wre.app.core.model.parcelable.ParcelableManga
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.call
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.novel.data.epub.EpubParser
import hanten.wre.app.novel.data.pdf.PdfRendererHelper
import hanten.wre.app.novel.data.source.NovelParserSource
import hanten.wre.app.core.nav.ReaderIntent
import hanten.wre.app.reader.ui.ReaderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class NovelChapterContent(
	val html: String,
	val version: Long,
)

@HiltViewModel
class NovelReaderViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaDataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: hanten.wre.app.core.parser.MangaRepository.Factory,
	private val historyRepository: HistoryRepository,
	val settings: AppSettings,
) : BaseViewModel() {

	private val initialManga: Manga? = savedStateHandle.get<ParcelableManga>(AppRouter.KEY_MANGA)?.manga
	private val mangaId: Long = savedStateHandle.get<Long>(AppRouter.KEY_ID) ?: initialManga?.id ?: 0L
	private val initialState: ReaderState? = savedStateHandle.get<ReaderState>(ReaderIntent.EXTRA_STATE)

	private val _manga = MutableStateFlow<Manga?>(initialManga)
	val manga: StateFlow<Manga?> = _manga.asStateFlow()

	private val _chapters = MutableStateFlow<List<MangaChapter>>(emptyList())
	val chapters: StateFlow<List<MangaChapter>> = _chapters.asStateFlow()

	private val _currentChapterIndex = MutableStateFlow(0)
	val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

	private val _currentChapter = MutableStateFlow<MangaChapter?>(null)
	val currentChapter: StateFlow<MangaChapter?> = _currentChapter.asStateFlow()

	private val _chapterContent = MutableStateFlow<NovelChapterContent?>(null)
	val chapterContent: StateFlow<NovelChapterContent?> = _chapterContent.asStateFlow()

	private val chapterContentVersion = AtomicLong(0)

	private val _isReadingLoading = MutableStateFlow(false)
	val isReadingLoading: StateFlow<Boolean> = _isReadingLoading.asStateFlow()

	private val _scrollProgress = MutableStateFlow(0f)
	val scrollProgress: StateFlow<Float> = _scrollProgress.asStateFlow()

	private val _isPdf = MutableStateFlow(false)
	val isPdf: StateFlow<Boolean> = _isPdf.asStateFlow()

	val onChapterAppended = MutableEventFlow<Pair<String, String>>() // title, html

	private var epubParser: EpubParser? = null
	private var pdfHelper: PdfRendererHelper? = null

	private val prefetchedChapters = ConcurrentHashMap<Long, String>()

	@Volatile
	private var isChapterContentReady = false

	private val isIncognito: Boolean =
		savedStateHandle.get<Boolean>(ReaderIntent.EXTRA_INCOGNITO) ?: false

	val contentBaseUrl: String
		get() {
			val source = _manga.value?.source
			val novelSource = source as? NovelParserSource ?: NovelParserSource.fromName(source?.name)
			return if (novelSource != null) "https://${novelSource.domain}/" else "https://futon.reader/"
		}

	val refererUrl: String
		get() {
			val manga = _manga.value
			val source = manga?.source
			val novelSource = source as? NovelParserSource ?: NovelParserSource.fromName(source?.name)
			if (novelSource != null) {
				return "https://${novelSource.domain}/"
			}
			val publicUrl = manga?.publicUrl.orEmpty()
			if (publicUrl.isNotEmpty()) {
				try {
					val uri = URI(publicUrl)
					val host = uri.host
					if (!host.isNullOrBlank()) {
						return "${uri.scheme ?: "https"}://$host/"
					}
				} catch (e: Exception) {
					e.printStackTraceDebug()
				}
			}
			return "https://lnori.com/"
		}

	init {
		loadNovel()
	}

	override fun onCleared() {
		super.onCleared()
		pdfHelper?.close()
		pdfHelper = null
	}

	fun loadNovel() {
		prefetchedChapters.clear()
		launchJob(Dispatchers.IO) {
			_isReadingLoading.value = true
			try {
				var current = _manga.value
				if (current == null && mangaId != 0L) {
					current = mangaDataRepository.findMangaById(mangaId, withChapters = true)
					_manga.value = current
				}

				if (current == null) {
					_isReadingLoading.value = false
					return@launchJob
				}

				val mangaFile = current.url.let { url ->
					if (url.startsWith("file://")) File(url.removePrefix("file://"))
					else File(url)
				}

				val isPdfFile = mangaFile.exists() && mangaFile.extension.equals("pdf", ignoreCase = true)
				val isEpubFile = mangaFile.exists() && mangaFile.extension.equals("epub", ignoreCase = true)
				_isPdf.value = isPdfFile

				val chapterList = if (isEpubFile) {
					epubParser = EpubParser(mangaFile)
					val book = epubParser?.parseBook()
					book?.chapters?.map { ec ->
						MangaChapter(
							id = ec.id,
							title = ec.title,
							number = ec.index.toFloat(),
							volume = 0,
							url = ec.href,
							uploadDate = 0L,
							source = current.source,
							scanlator = null,
							branch = null,
						)
					} ?: emptyList()
				} else if (isPdfFile) {
					pdfHelper = PdfRendererHelper(mangaFile)
					val count = pdfHelper?.pageCount ?: 0
					(0 until count).map { pageIdx ->
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
				} else {
					// Online novel or database chapters
					val repo = mangaRepositoryFactory.create(current.source)
					val fullDetails = repo.getDetails(current)
					fullDetails.chapters.orEmpty()
				}

				_chapters.value = chapterList

				// Determine start chapter
				val targetChapterId = initialState?.chapterId
				val startIdx = if (targetChapterId != null) {
					chapterList.indexOfFirst { it.id == targetChapterId }.coerceAtLeast(0)
				} else {
					val lastHistory = historyRepository.getOne(current)
					if (lastHistory != null) {
						chapterList.indexOfFirst { it.id == lastHistory.chapterId }.coerceAtLeast(0)
					} else {
						0
					}
				}

				if (chapterList.isNotEmpty()) {
					loadChapterAtIndex(startIdx)
				} else {
					setChapterContent("<div style='padding: 32px; text-align: center;'><p>No chapters found for this novel.</p></div>")
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
				setChapterContent("<div style='padding: 32px; text-align: center;'><p>Error loading novel details.</p></div>")
			} finally {
				_isReadingLoading.value = false
			}
		}
	}

	fun loadChapterAtIndex(index: Int) {
		val list = _chapters.value
		if (index !in list.indices) return
		val target = list[index]
		_currentChapterIndex.value = index
		_currentChapter.value = target
		isChapterContentReady = false

		launchJob(Dispatchers.IO) {
			_isReadingLoading.value = true
			try {
				restoreScrollProgress(target)
				applyInitialProgress(target)
				val content = prefetchedChapters.remove(target.id) ?: fetchChapterHtml(target)
				if (content.isNullOrBlank()) {
					setChapterContent("<div style='padding: 32px; text-align: center;'><p>Unable to load chapter content.</p><p style='opacity: 0.7; font-size: 0.9em;'>Please check your network connection and try again.</p></div>")
				} else {
					setChapterContent(content)
					isChapterContentReady = true
					prefetchNextChapter(index)
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
				setChapterContent("<div style='padding: 32px; text-align: center;'><p>Error loading chapter.</p></div>")
			} finally {
				_isReadingLoading.value = false
			}
		}
	}

	fun switchChapter(delta: Int) {
		val nextIdx = _currentChapterIndex.value + delta
		loadChapterAtIndex(nextIdx)
	}

	fun loadNextChapterForInfiniteScroll() {
		val list = _chapters.value
		val nextIdx = _currentChapterIndex.value + 1
		if (nextIdx !in list.indices) return
		val nextChapter = list[nextIdx]

		launchJob(Dispatchers.IO) {
			try {
				val html = prefetchedChapters.remove(nextChapter.id) ?: fetchChapterHtml(nextChapter)
				if (!html.isNullOrBlank()) {
					_currentChapterIndex.value = nextIdx
					_currentChapter.value = nextChapter
					onChapterAppended.call(Pair(nextChapter.title.orEmpty(), html))
					saveReadingProgress(0f)
					prefetchNextChapter(nextIdx)
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
			}
		}
	}

	private fun applyInitialProgress(chapter: MangaChapter) {
		val state = initialState ?: return
		if (state.chapterId != chapter.id) return
		val percent = when {
			state.page in 1..100 -> state.page / 100f
			state.scroll in 1..100 -> state.scroll / 100f
			else -> return
		}
		_scrollProgress.value = percent
	}

	private fun isIncognitoMode(manga: Manga): Boolean {
		return isIncognito || settings.isIncognitoModeEnabled(manga.isNsfw())
	}

	private suspend fun restoreScrollProgress(chapter: MangaChapter) {
		val cur = _manga.value ?: return
		if (isIncognitoMode(cur)) {
			_scrollProgress.value = 0f
			return
		}
		val history = try {
			historyRepository.getOne(cur)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
		// Only reuse history progress when it points at the chapter being opened, otherwise start from the top
		val percent = if (history?.chapterId == chapter.id) history.percent else 0f
		_scrollProgress.value = percent.coerceIn(0f, 1f)
	}

	private fun prefetchNextChapter(currentIndex: Int) {
		if (_isPdf.value) return
		val list = _chapters.value
		val nextIdx = currentIndex + 1
		if (nextIdx !in list.indices) return
		val nextChapter = list[nextIdx]
		if (prefetchedChapters.containsKey(nextChapter.id)) return
		if (prefetchedChapters.size >= MAX_PREFETCHED_CHAPTERS) {
			prefetchedChapters.clear()
		}
		launchJob(Dispatchers.IO) {
			try {
				val html = fetchChapterHtml(nextChapter)
				if (!html.isNullOrBlank()) {
					prefetchedChapters[nextChapter.id] = html
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
			}
		}
	}

	private fun setChapterContent(html: String) {
		// Version keeps state emissions unique even when the same chapter HTML is loaded again
		_chapterContent.value = NovelChapterContent(html, chapterContentVersion.incrementAndGet())
	}

	private suspend fun fetchChapterHtml(chapter: MangaChapter): String? = withContext(Dispatchers.IO) {
		val parser = epubParser
		if (parser != null) {
			return@withContext parser.getChapterHtml(chapter.url)
		}

		val curManga = _manga.value ?: return@withContext null
		try {
			// Online novel source: fetch pages / chapter content
			val repo = mangaRepositoryFactory.create(curManga.source)
			val pages = repo.getPages(chapter)
			// For text sources, pages may contain formatted text in url or html content
			val textBuilder = StringBuilder()
			for (page in pages) {
				val trimmed = page.url.trim()
				if (trimmed.startsWith("<")) {
					textBuilder.append(trimmed)
				} else if (trimmed.isNotEmpty()) {
					textBuilder.append("<p>${trimmed}</p>")
				}
			}
			textBuilder.toString()
		} catch (e: Exception) {
			e.printStackTraceDebug()
			null
		}
	}

	fun updateScrollProgress(percent: Float) {
		_scrollProgress.value = percent
		// Do not write progress while a new chapter is still loading, otherwise the saved position is clobbered
		if (isChapterContentReady) {
			saveReadingProgress(percent)
		}
	}

	private fun saveReadingProgress(percent: Float) {
		val cur = _manga.value ?: return
		val chap = _currentChapter.value ?: return
		if (isIncognitoMode(cur)) return
		viewModelScope.launch(Dispatchers.IO) {
			val chaps = _chapters.value
			historyRepository.addOrUpdate(
				manga = cur.copy(chapters = chaps),
				chapterId = chap.id,
				page = _currentChapterIndex.value,
				scroll = (percent * 100).toInt(),
				percent = percent,
				force = false,
			)
		}
	}

	fun getPdfHelper(): PdfRendererHelper? = pdfHelper

	fun getEpubParser(): EpubParser? = epubParser

	companion object {
		private const val MAX_PREFETCHED_CHAPTERS = 4
	}
}