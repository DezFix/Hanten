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
import hanten.wre.app.novel.data.NovelContentParser
import hanten.wre.app.novel.data.epub.EpubParser
import hanten.wre.app.novel.data.offline.NovelOfflineRepository
import hanten.wre.app.novel.data.pdf.PdfRendererHelper
import hanten.wre.app.novel.domain.NovelSmartResume
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
	private val offlineRepository: NovelOfflineRepository,
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

	val onChapterAppended = MutableEventFlow<Triple<String, String, Int>>() // title, html, chapter index

	private var epubParser: EpubParser? = null
	private var pdfHelper: PdfRendererHelper? = null

	private val prefetchedChapters = ConcurrentHashMap<Long, String>()

	@Volatile
	private var isChapterContentReady = false

	// Index of the last chapter appended to the WebView document; `_currentChapterIndex`
	// follows the viewport and may move backwards when the user scrolls up.
	@Volatile
	private var appendedChapterIndex = 0

	@Volatile
	private var isAppendingChapter = false

	private val isIncognito: Boolean =
		savedStateHandle.get<Boolean>(ReaderIntent.EXTRA_INCOGNITO) ?: false

	// isSmartResumeWaitForUpdates is irrelevant here: novels have no update pass, so saved progress is final
	private val smartResume = NovelSmartResume(
		enabled = settings.isSmartResumeEnabled,
		mode = settings.smartResumeCompletionMode,
		percentage = settings.smartResumePercentage,
		pagesRemaining = settings.smartResumePagesRemaining,
	)

	val contentBaseUrl: String
		get() {
			if (epubParser != null) {
				val dir = _currentChapter.value?.url?.substringBeforeLast('/', "")
				return EPUB_BASE_URL + if (dir.isNullOrEmpty()) "" else "$dir/"
			}
			val manga = _manga.value
			return originOf(_currentChapter.value?.url)
				?: originOf(manga?.publicUrl)
				?: originOf(manga?.url)
				?: EPUB_BASE_URL
		}

	val refererUrl: String
		get() {
			val manga = _manga.value
			return originOf(manga?.publicUrl) ?: originOf(manga?.url) ?: "https://lnori.com/"
		}

	private fun originOf(url: String?): String? {
		if (url.isNullOrBlank()) return null
		return try {
			val uri = URI(url)
			val host = uri.host
			if (host.isNullOrBlank()) null else "${uri.scheme ?: "https"}://$host/"
		} catch (e: Exception) {
			null
		}
	}

	init {
		loadNovel()
	}

	override fun onCleared() {
		super.onCleared()
		pdfHelper?.close()
		pdfHelper = null
		epubParser?.close()
		epubParser = null
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
					// Online novel or database chapters. Offline cache is the last fallback.
					val repo = mangaRepositoryFactory.create(current.source)
					val onlineChapters = try {
						repo.getDetails(current).chapters.orEmpty()
					} catch (e: CancellationException) {
						throw e
					} catch (e: Exception) {
						e.printStackTraceDebug()
						emptyList()
					}
					onlineChapters.ifEmpty {
						current.chapters.orEmpty().ifEmpty {
							offlineRepository.listCachedChapters(current.id).mapIndexed { index, cached ->
								MangaChapter(
									id = cached.chapterId,
									title = cached.title,
									number = (index + 1).toFloat(),
									volume = 0,
									url = "",
									uploadDate = 0L,
									source = current.source,
									scanlator = null,
									branch = null,
								)
							}
						}
					}
				}

				_chapters.value = chapterList

				// Determine start chapter. Explicit chapter and bookmark openings keep their target.
				val targetChapterId = initialState?.chapterId
				val startIdx = if (targetChapterId != null) {
					chapterList.indexOfFirst { it.id == targetChapterId }.coerceAtLeast(0)
				} else {
					val lastHistory = historyRepository.getOne(current)
					if (lastHistory == null) {
						0
					} else {
						val savedIdx = chapterList.indexOfFirst { it.id == lastHistory.chapterId }
						if (savedIdx < 0) {
							0
						} else {
							smartResume.resolveStartIndex(savedIdx, chapterList.size, lastHistory.percent)
						}
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
		appendedChapterIndex = index
		isChapterContentReady = false

		launchJob(Dispatchers.IO) {
			_isReadingLoading.value = true
			try {
				restoreScrollProgress(target)
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
		if (isAppendingChapter) return
		val list = _chapters.value
		val nextIdx = appendedChapterIndex + 1
		if (nextIdx !in list.indices) return
		val nextChapter = list[nextIdx]
		isAppendingChapter = true

		launchJob(Dispatchers.IO) {
			try {
				val html = prefetchedChapters.remove(nextChapter.id) ?: fetchChapterHtml(nextChapter)
				if (!html.isNullOrBlank()) {
					appendedChapterIndex = nextIdx
					onChapterAppended.call(Triple(nextChapter.title.orEmpty(), html, nextIdx))
					prefetchNextChapter(nextIdx)
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
			} finally {
				isAppendingChapter = false
			}
		}
	}

	/** Called from the WebView when the viewport enters another appended chapter. */
	fun onActiveChapter(index: Int) {
		val list = _chapters.value
		if (index !in list.indices) return
		if (index == _currentChapterIndex.value) return
		_currentChapterIndex.value = index
		_currentChapter.value = list[index]
	}

	/** The activity rebuilt the document (settings/mode change): only one chapter is present. */
	fun onReaderDocumentRebuilt() {
		appendedChapterIndex = _currentChapterIndex.value
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
			offlineRepository.readChapter(curManga.id, chapter.id)?.takeIf { it.isNotBlank() }?.let {
				return@withContext it
			}

			// Online novel source: fetch pages / chapter content
			val repo = mangaRepositoryFactory.create(curManga.source)
			val pages = repo.getPages(chapter)
			// Novel parsers may return HTML, plain text or a JSON document (RanobeLib).
			val baseUrl = contentBaseUrl
			val html = buildString {
				for (page in pages) {
					val part = NovelContentParser.normalize(page.url, baseUrl)
					if (part.isNotBlank()) append(part)
				}
			}
			if (html.isNotBlank() && !isIncognitoMode(curManga)) {
				launchJob(Dispatchers.IO) {
					offlineRepository.cacheChapter(curManga, chapter, html)
				}
			}
			html
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
		private const val EPUB_BASE_URL = "https://futon.reader/"
	}
}