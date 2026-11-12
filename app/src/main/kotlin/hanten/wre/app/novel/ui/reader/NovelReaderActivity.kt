package hanten.wre.app.novel.ui.reader

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.bookmarks.domain.Bookmark
import hanten.wre.app.bookmarks.domain.BookmarksRepository
import hanten.wre.app.core.nav.ReaderIntent
import hanten.wre.app.core.network.MangaHttpClient
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseFullscreenActivity
import hanten.wre.app.core.util.MimeTypes
import hanten.wre.app.core.util.ext.getParcelableExtraCompat
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.core.util.ext.setContentDescriptionAndTooltip
import hanten.wre.app.databinding.ActivityNovelReaderBinding
import hanten.wre.app.novel.data.model.NovelReadingMode
import hanten.wre.app.parsers.model.MangaParserSource
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.util.formatSimple
import hanten.wre.app.parsers.util.longHashCode
import hanten.wre.app.reader.ui.ReaderState
import hanten.wre.app.reader.ui.ScreenOrientationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class NovelReaderActivity :
	BaseFullscreenActivity<ActivityNovelReaderBinding>(),
	NovelReaderConfigSheet.Callback,
	NovelChaptersSheet.Callback,
	Slider.OnSliderTouchListener,
	Slider.OnChangeListener {

	@Inject
	lateinit var appSettings: AppSettings

	@Inject
	@MangaHttpClient
	lateinit var baseOkHttpClient: OkHttpClient

	@Inject
	lateinit var screenOrientationHelper: ScreenOrientationHelper

	@Inject
	lateinit var bookmarksRepository: BookmarksRepository

	private var bookmarks: List<Bookmark> = emptyList()
	private var bookmarkJob: Job? = null

	private val imageOkHttpClient by lazy {
		baseOkHttpClient.newBuilder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.build()
	}

	private val viewModel by viewModels<NovelReaderViewModel>()
	private lateinit var readerSettings: NovelReaderSettings
	private var isUiVisible = true
	private var isSliderTracking = false
	private var isWebViewReady = false
	private var isAutoScrollActive = false
	private var pendingBookmarkRestore: ReaderState? = null
	private var isBookmarkRestoreConsumed = false
	private var safeTopPx = 0
	private var safeBottomPx = 0
	private var systemTopInset = 0
	private var systemBottomInset = 0
	@Volatile
	private var defaultUserAgent: String? = null

	private val initialReaderState: ReaderState? by lazy {
		if (!intent.getBooleanExtra(ReaderIntent.EXTRA_BOOKMARK, false)) {
			return@lazy null
		}
		intent.getParcelableExtraCompat<ReaderState>(ReaderIntent.EXTRA_STATE)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityNovelReaderBinding.inflate(layoutInflater))
		readerSettings = NovelReaderSettings.load(this)
		isAutoScrollActive = savedInstanceState?.getBoolean(STATE_AUTO_SCROLL_ACTIVE) ?: false

		setupWebView()
		setupControls()
		observeViewModel()
		screenOrientationHelper.applySettings()
		applyKeepScreenOn()
		applySystemBarsVisibility()
		updateAutoScrollButton()
	}

	override fun onResume() {
		super.onResume()
		applyKeepScreenOn()
		applySystemBarsVisibility()
		viewModel.refreshDownloadedChapters()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putBoolean(STATE_AUTO_SCROLL_ACTIVE, isAutoScrollActive)
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus) {
			applySystemBarsVisibility()
		}
	}

	override fun onDestroy() {
		bookmarkJob?.cancel()
		viewBinding.webView.destroy()
		super.onDestroy()
	}

	@SuppressLint("SetJavaScriptEnabled")
	private fun setupWebView() {
		with(viewBinding.webView.settings) {
			javaScriptEnabled = true
			domStorageEnabled = true
			allowFileAccess = true
			loadWithOverviewMode = false
			useWideViewPort = false
			textZoom = 100
			displayZoomControls = false
			builtInZoomControls = false
			defaultUserAgent = userAgentString
		}
		with(viewBinding.webView) {
			isVerticalScrollBarEnabled = false
			isHorizontalScrollBarEnabled = false
			overScrollMode = View.OVER_SCROLL_NEVER
		}

		viewBinding.webView.setBackgroundColor(readerSettings.effectiveBackgroundColor)

		val bridge = NovelReaderBridge(
			scope = lifecycleScope,
			onScroll = { percent ->
				if (!isSliderTracking) {
					viewModel.updateScrollProgress(percent)
				}
			},
			onNearBottomReached = {
				viewModel.loadNextChapterForInfiniteScroll()
			},
			onCenterTapped = {
				toggleUiVisibility()
			},
			onPreviousChapter = {
				viewModel.switchChapter(-1)
			},
			onNextChapter = {
				viewModel.switchChapter(1)
			},
			onPageChange = { cur, total ->
				viewBinding.textProgress.text = "$cur / $total"
			},
			onActiveChapter = { index ->
				viewModel.onActiveChapter(index)
			},
		)
		viewBinding.webView.addJavascriptInterface(bridge, "AndroidNovelBridge")

		viewBinding.webView.webViewClient = object : WebViewClient() {
			override fun shouldInterceptRequest(
				view: WebView?,
				request: WebResourceRequest?,
			): WebResourceResponse? {
				return try {
					val url = request?.url ?: return null
					val path = url.path.orEmpty()
					// Offline novel cache rewrites images to file:// URLs.
					// Only files inside the novel cache may be served to the WebView.
					if (url.scheme == "file") {
						val cacheRoot = File(filesDir, "novels").canonicalFile
						val file = File(path).canonicalFile
						if (file.path.startsWith(cacheRoot.path + File.separator) && file.isFile) {
							val ext = file.extension
							val mime = MimeTypes.getMimeTypeFromExtension(ext)?.toString()
								?: "application/octet-stream"
							return WebResourceResponse(mime, null, file.inputStream())
						}
						return null
					}
					val epubParser = viewModel.getEpubParser()
					if (epubParser != null && (url.scheme == "epub" || url.host == "futon.reader")) {
						val cleanPath = path.removePrefix("/")
						val stream: InputStream? = epubParser.readEntry(cleanPath)
						if (stream != null) {
							val ext = cleanPath.substringAfterLast('.', "")
							val mime = MimeTypes.getMimeTypeFromExtension(ext)?.toString() ?: "application/octet-stream"
							return WebResourceResponse(mime, null, stream)
						}
					}
					val host = url.host.orEmpty()
					val isImageRequest = !request.isForMainFrame && (
						request.requestHeaders?.get("Accept")?.startsWith("image/") == true ||
							IMAGE_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }
						)
					if (isImageRequest || host.equals("img.lnori.com", ignoreCase = true) ||
						host.endsWith(".lnori.com", ignoreCase = true)
					) {
						return interceptProtectedImage(request)
					}
					super.shouldInterceptRequest(view, request)
				} catch (e: Throwable) {
					null
				}
			}

			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)
				isWebViewReady = true
				applyPendingBookmarkRestore()
				applyAutoScrollState()
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: androidx.core.view.WindowInsetsCompat): androidx.core.view.WindowInsetsCompat {
		val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
		viewBinding.cardBottom.setPadding(0, 0, 0, systemBars.bottom)
		systemTopInset = systemBars.top
		systemBottomInset = systemBars.bottom
		viewBinding.coordinator.post { updateReaderSafeArea() }
		return insets
	}

	private fun updateReaderSafeArea() {
		val top = systemTopInset
		val bottom = if (isUiVisible) viewBinding.cardBottom.height else systemBottomInset
		if (safeTopPx == top && safeBottomPx == bottom) return
		safeTopPx = top
		safeBottomPx = bottom
		if (isWebViewReady) {
			viewBinding.webView.evaluateJavascript(
				"window.captureAnchor(); window.setSafeArea($top, $bottom); window.restoreAnchor();",
				null,
			)
		}
	}

	private fun setupControls() {
		viewBinding.buttonPrev.setOnClickListener { viewModel.switchChapter(-1) }
		viewBinding.buttonNext.setOnClickListener { viewModel.switchChapter(1) }
		viewBinding.buttonChapters.setOnClickListener {
			showChaptersSheet()
		}
		viewBinding.buttonFontSettings.setOnClickListener {
			NovelReaderConfigSheet.newInstance().show(supportFragmentManager, NovelReaderConfigSheet.TAG)
		}
		viewBinding.buttonBookmark.setOnClickListener { toggleBookmark() }
		viewBinding.buttonAutoScroll.setOnClickListener { toggleAutoScroll() }

		viewBinding.sliderProgress.addOnSliderTouchListener(this)
		viewBinding.sliderProgress.addOnChangeListener(this)
	}

	@SuppressLint("SetTextI18n")
	private fun observeViewModel() {
		viewModel.manga.observe(this) { manga ->
			if (manga != null) {
				title = manga.title
				observeBookmarks(manga)
			}
		}

		viewModel.currentChapter.observe(this) { chapter ->
			if (chapter != null) {
				val idx = viewModel.currentChapterIndex.value + 1
				val total = viewModel.chapters.value.size
				viewBinding.textProgress.text = if (total > 0) "$idx / $total" else chapter.title
			}
			updateBookmarkButton()
		}

		viewModel.isReadingLoading.observe(this) { loading ->
			viewBinding.progressBar.isVisible = loading
		}

		viewModel.chapterContent.observe(this) { content ->
			val html = content?.html
			if (!html.isNullOrEmpty()) {
				renderChapterContent(html)
			}
		}

		viewModel.onChapterAppended.observeEvent(this) { (title, html, index) ->
			val quotedTitle = org.json.JSONObject.quote(title)
			val quotedHtml = org.json.JSONObject.quote(html)
			viewBinding.webView.evaluateJavascript("appendChapter($quotedTitle, $quotedHtml, $index);", null)
		}

		viewModel.scrollProgress.observe(this) { percent ->
			if (!isSliderTracking) {
				viewBinding.sliderProgress.value = (percent * 100f).coerceIn(0f, 100f)
			}
			if (isAutoScrollActive && percent >= 0.999f) {
				isAutoScrollActive = false
				applyAutoScrollState()
			}
		}
	}

	private fun observeBookmarks(manga: Manga) {
		bookmarkJob?.cancel()
		bookmarkJob = lifecycleScope.launch {
			bookmarksRepository.observeBookmarks(manga).collect { items ->
				bookmarks = items
				updateBookmarkButton()
			}
		}
	}

	private fun updateBookmarkButton() {
		val chapterId = viewModel.currentChapter.value?.id
		val isBookmarked = bookmarks.any { it.chapterId == chapterId }
		viewBinding.buttonBookmark.setIconResource(
			if (isBookmarked) R.drawable.ic_bookmark_added else R.drawable.ic_bookmark,
		)
		viewBinding.buttonBookmark.setContentDescriptionAndTooltip(
			if (isBookmarked) R.string.bookmark_remove else R.string.bookmark_add,
		)
	}

	private fun toggleBookmark() {
		val manga = viewModel.manga.value ?: return
		val chapter = viewModel.currentChapter.value ?: return
		val existing = bookmarks.firstOrNull {
			it.chapterId == chapter.id && abs(it.percent - viewModel.scrollProgress.value) < 0.02f
		}
		if (existing != null) {
			lifecycleScope.launch(Dispatchers.IO) {
				bookmarksRepository.removeBookmarks(setOf(existing.pageId))
				launch(Dispatchers.Main) {
					Toast.makeText(this@NovelReaderActivity, R.string.bookmark_remove, Toast.LENGTH_SHORT).show()
				}
			}
			return
		}
		viewBinding.webView.evaluateJavascript(
			"(function(){try{return window.getCurrentParagraph();}catch(e){return -1;}})()",
		) { result ->
			val parsed = result?.trim()?.toIntOrNull()
			saveBookmark(manga, chapter, parsed ?: -1)
		}
	}

	private fun saveBookmark(manga: Manga, chapter: MangaChapter, paragraphIndex: Int) {
		val percent = viewModel.scrollProgress.value.coerceIn(0.01f, 1f)
		lifecycleScope.launch(Dispatchers.IO) {
			bookmarksRepository.addBookmark(
				Bookmark(
					manga = manga.copy(chapters = viewModel.chapters.value),
					pageId = "${chapter.id}:${(percent * 1000).roundToInt()}".longHashCode(),
					chapterId = chapter.id,
					page = (percent * 100).roundToInt().coerceIn(1, 100),
					scroll = paragraphIndex,
					imageUrl = manga.coverUrl.orEmpty(),
					createdAt = Instant.now(),
					percent = percent,
				),
			)
			launch(Dispatchers.Main) {
				Toast.makeText(this@NovelReaderActivity, R.string.bookmark_add, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun queueBookmarkRestore(): ReaderState? {
		if (isBookmarkRestoreConsumed) return null
		val state = initialReaderState ?: return null
		val chapter = viewModel.currentChapter.value ?: return null
		if (state.chapterId != chapter.id) return null
		pendingBookmarkRestore = state
		return state
	}

	private fun applyPendingBookmarkRestore() {
		val state = pendingBookmarkRestore ?: return
		pendingBookmarkRestore = null
		isBookmarkRestoreConsumed = true
		val percent = (state.page / 100f).coerceIn(0f, 1f)
		val paragraph = state.scroll
		viewBinding.webView.evaluateJavascript(
			"window.restoreProgress($percent); window.restoreBookmark($paragraph, $percent);",
			null,
		)
	}

	private fun contentLanguage(): String {
		val locale = (viewModel.manga.value?.source as? MangaParserSource)?.locale
		return locale?.takeIf { it.isNotBlank() } ?: Locale.getDefault().language
	}

	private fun chapterHeading(chapter: MangaChapter?): String {
		if (chapter == null) return ""
		val title = chapter.title?.trim().orEmpty()
		if (title.isNotEmpty()) return title
		val number = chapter.number.takeIf { it > 0f }?.formatSimple()
			?: (viewModel.currentChapterIndex.value + 1).toString()
		return if (chapter.volume > 0) {
			getString(R.string.novel_chapter_heading_volume, chapter.volume, number)
		} else {
			getString(R.string.novel_chapter_heading, number)
		}
	}

	private fun renderChapterContent(html: String) {
		val chapter = viewModel.currentChapter.value
		val title = chapter?.title ?: ""
		val heading = chapterHeading(chapter)
		val content = if (heading.isNotBlank()) {
			"<h1 class=\"chapter-heading\">${TextUtils.htmlEncode(heading)}</h1>$html"
		} else {
			html
		}
		val bookmarkState = queueBookmarkRestore()
		val initialProgress = if (bookmarkState != null) {
			0f
		} else {
			viewModel.scrollProgress.value.coerceIn(0f, 1f)
		}
		val fullDoc = NovelHtmlTemplate.buildDocument(
			title = title,
			initialChapterHtml = content,
			settings = readerSettings,
			initialProgress = initialProgress,
			safeTopPx = safeTopPx,
			safeBottomPx = safeBottomPx,
			density = resources.displayMetrics.density,
			language = contentLanguage(),
			chapterIndex = viewModel.currentChapterIndex.value,
		)
		val chapterUrl = viewModel.currentChapter.value?.url?.substringBefore('#')
		val isOnline = !chapterUrl.isNullOrEmpty() && (chapterUrl.startsWith("http://") || chapterUrl.startsWith("https://"))
		val baseUrl = if (isOnline) chapterUrl else viewModel.contentBaseUrl
		isWebViewReady = false
		viewModel.onReaderDocumentRebuilt()
		viewBinding.webView.loadDataWithBaseURL(baseUrl, fullDoc, "text/html", "UTF-8", null)
	}

	private fun showChaptersSheet() {
		val chapters = viewModel.chapters.value
		if (chapters.isEmpty()) return
		if (supportFragmentManager.findFragmentByTag(NovelChaptersSheet.TAG) != null) return
		val titles = ArrayList<String>(chapters.size)
		for ((index, chapter) in chapters.withIndex()) {
			val chapterTitle = chapter.title?.takeIf { it.isNotBlank() } ?: (index + 1).toString()
			titles.add(chapterTitle)
		}
		val downloadedIds = viewModel.downloadedChapterIds.value
		val downloaded = BooleanArray(chapters.size) { index -> chapters[index].id in downloadedIds }
		NovelChaptersSheet.newInstance(titles, viewModel.currentChapterIndex.value, downloaded)
			.show(supportFragmentManager, NovelChaptersSheet.TAG)
	}

	override fun onChapterSelected(index: Int) {
		viewModel.loadChapterAtIndex(index)
	}

	private fun interceptProtectedImage(request: WebResourceRequest): WebResourceResponse? {
		var response: Response? = null
		return try {
			val urlString = request.url.toString()
			val userAgent = request.requestHeaders?.get("User-Agent")
				?: defaultUserAgent
				?: DEFAULT_USER_AGENT

			val okHttpRequest = Request.Builder()
				.url(urlString)
				.method(request.method, null)
				.header("Referer", viewModel.refererUrl)
				.apply {
					if (!userAgent.isNullOrBlank()) {
						header("User-Agent", userAgent)
					}
				}
				.build()

			response = imageOkHttpClient.newCall(okHttpRequest).execute()
			if (!response.isSuccessful) {
				response.close()
				return null
			}

			val body = response.body ?: run {
				response.close()
				return null
			}

			val rawContentType = response.header("Content-Type")?.substringBefore(';')?.trim()
			val mimeType = if (!rawContentType.isNullOrBlank() && rawContentType != "application/octet-stream") {
				rawContentType
			} else {
				val ext = request.url.path?.substringAfterLast('.', "") ?: "avif"
				MimeTypes.getMimeTypeFromExtension(ext)?.toString() ?: "image/avif"
			}

			val responseHeaders = mutableMapOf<String, String>()
			for (i in 0 until response.headers.size) {
				val name = response.headers.name(i)
				if (name.equals("content-encoding", ignoreCase = true) ||
					name.equals("transfer-encoding", ignoreCase = true)
				) {
					continue
				}
				responseHeaders[name] = response.headers.value(i)
			}

			val wrappedStream = object : FilterInputStream(body.byteStream()) {
				override fun close() {
					try {
						super.close()
					} finally {
						response.close()
					}
				}
			}

			WebResourceResponse(
				mimeType,
				null,
				response.code,
				response.message.ifBlank { "OK" },
				responseHeaders,
				wrappedStream,
			)
		} catch (e: Throwable) {
			response?.close()
			null
		}
	}

	private fun toggleUiVisibility() {
		isUiVisible = !isUiVisible
		viewBinding.cardBottom.isVisible = isUiVisible
		applySystemBarsVisibility()
		viewBinding.coordinator.post { updateReaderSafeArea() }
	}

	private fun applySystemBarsVisibility() {
		val controller = WindowCompat.getInsetsController(window, window.decorView)
		if (!isUiVisible) {
			controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			controller.hide(WindowInsetsCompat.Type.systemBars())
		} else {
			controller.show(WindowInsetsCompat.Type.systemBars())
			controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
		}
	}

	private fun toggleAutoScroll() {
		if (readerSettings.readingMode == NovelReadingMode.PAGED) {
			Toast.makeText(this, R.string.novel_auto_scroll_paged, Toast.LENGTH_SHORT).show()
			return
		}
		if (readerSettings.autoScrollSpeed <= 0f) {
			NovelReaderConfigSheet.newInstance().show(supportFragmentManager, NovelReaderConfigSheet.TAG)
			return
		}
		isAutoScrollActive = !isAutoScrollActive
		applyAutoScrollState()
	}

	private fun applyAutoScrollState() {
		if (isAutoScrollActive && readerSettings.readingMode == NovelReadingMode.PAGED) {
			isAutoScrollActive = false
		}
		updateAutoScrollButton()
		if (!isWebViewReady) return
		val command = if (isAutoScrollActive) "window.startAutoScroll();" else "window.stopAutoScroll();"
		viewBinding.webView.evaluateJavascript(
			"window.setAutoScrollSpeed(${readerSettings.autoScrollSpeed});$command",
			null,
		)
	}

	private fun updateAutoScrollButton() {
		viewBinding.buttonAutoScroll.setIconResource(
			if (isAutoScrollActive) R.drawable.ic_action_pause else R.drawable.ic_action_resume,
		)
		viewBinding.buttonAutoScroll.setContentDescriptionAndTooltip(R.string.automatic_scroll)
	}

	override fun onNovelSettingsChanged(settings: NovelReaderSettings) {
		val modeChanged = settings.readingMode != readerSettings.readingMode
		readerSettings = settings
		viewBinding.webView.setBackgroundColor(settings.effectiveBackgroundColor)
		applySystemBarsVisibility()
		viewBinding.coordinator.post { updateReaderSafeArea() }

		if (modeChanged && settings.readingMode == NovelReadingMode.PAGED && isAutoScrollActive) {
			isAutoScrollActive = false
			Toast.makeText(this, R.string.novel_auto_scroll_paged, Toast.LENGTH_SHORT).show()
		}

		val currentHtml = viewModel.chapterContent.value?.html
		if (modeChanged || !isWebViewReady) {
			if (!currentHtml.isNullOrEmpty()) {
				renderChapterContent(currentHtml)
			}
		} else {
			val cssText = org.json.JSONObject.quote(
				settings.toCssVariables(
					safeTopPx = safeTopPx,
					safeBottomPx = safeBottomPx,
					density = resources.displayMetrics.density,
				),
			)
			viewBinding.webView.evaluateJavascript(
				"window.applySettings($cssText);",
				null,
			)
		}
		applyAutoScrollState()
	}

	override fun onStartTrackingTouch(slider: Slider) {
		isSliderTracking = true
	}

	override fun onStopTrackingTouch(slider: Slider) {
		isSliderTracking = false
		val percent = slider.value / 100f
		viewModel.updateScrollProgress(percent)
		viewBinding.webView.evaluateJavascript("scrollToPercent($percent);", null)
	}

	override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
		if (fromUser) {
			viewBinding.textProgress.text = "${value.toInt()}%"
		}
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		if (appSettings.isReaderVolumeButtonsEnabled) {
			val delta = if (appSettings.isReaderNavigationInverted) 1 else -1
			when (keyCode) {
				KeyEvent.KEYCODE_VOLUME_UP -> {
					turnPageOrChapter(delta)
					return true
				}

				KeyEvent.KEYCODE_VOLUME_DOWN -> {
					turnPageOrChapter(-delta)
					return true
				}
			}
		}
		return super.onKeyDown(keyCode, event)
	}

	private fun turnPageOrChapter(delta: Int) {
		if (readerSettings.readingMode == NovelReadingMode.PAGED) {
			viewBinding.webView.evaluateJavascript("window.turnPage($delta);", null)
		} else {
			viewModel.switchChapter(delta)
		}
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		if (appSettings.isReaderVolumeButtonsEnabled &&
			(keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
		) {
			return true
		}
		return super.onKeyUp(keyCode, event)
	}

	private fun applyKeepScreenOn() {
		val flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
		if (appSettings.isReaderKeepScreenOn) {
			window.addFlags(flags)
		} else {
			window.clearFlags(flags)
		}
	}

	private companion object {
		const val STATE_AUTO_SCROLL_ACTIVE = "novel_auto_scroll_active"
		const val DEFAULT_USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
		val IMAGE_EXTENSIONS = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif", ".bmp")
	}
}
