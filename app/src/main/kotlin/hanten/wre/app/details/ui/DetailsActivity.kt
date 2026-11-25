package hanten.wre.app.details.ui

import androidx.core.view.ViewCompat
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.model.isNovel
import hanten.wre.app.details.ui.pager.ChaptersPagesSheet
import android.content.res.ColorStateList
import android.app.assist.AssistContent
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.text.style.ClickableSpan
import android.text.SpannedString
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.TransitionManager
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.request.lifecycle
import coil3.request.transformations
import coil3.size.Precision
import coil3.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import hanten.wre.app.R
import hanten.wre.app.bookmarks.domain.Bookmark
import hanten.wre.app.core.image.CoilMemoryCacheKey
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.model.LocalMangaSource
import hanten.wre.app.core.model.UnknownMangaSource
import hanten.wre.app.core.model.contentType
import hanten.wre.app.core.model.getSummary
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.core.model.titleResId
import hanten.wre.app.core.nav.ReaderIntent
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.os.AppShortcutManager
import hanten.wre.app.core.parser.favicon.faviconUri
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.dialog.buildAlertDialog
import hanten.wre.app.core.ui.image.FaviconDrawable
import hanten.wre.app.core.ui.image.TextDrawable
import hanten.wre.app.core.ui.image.TextViewTarget
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.ui.sheet.BottomSheetCollapseCallback
import hanten.wre.app.core.ui.util.MenuInvalidator
import hanten.wre.app.core.ui.util.ReversibleActionObserver
import hanten.wre.app.core.ui.widgets.ChipsView
import hanten.wre.app.core.util.FileSize
import hanten.wre.app.core.util.LocaleUtils
import hanten.wre.app.core.util.ext.consume
import hanten.wre.app.core.util.ext.copyToClipboard
import hanten.wre.app.core.util.ext.drawableStart
import hanten.wre.app.core.util.ext.end
import hanten.wre.app.core.util.ext.enqueueWith
import hanten.wre.app.core.util.ext.getQuantityStringSafe
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.util.ext.isAnimationsEnabled
import hanten.wre.app.core.util.ext.isTextTruncated
import hanten.wre.app.core.util.ext.joinToStringWithLimit
import hanten.wre.app.core.util.ext.mangaSourceExtra
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.core.util.ext.parentView
import hanten.wre.app.core.util.ext.setTooltipCompat
import hanten.wre.app.core.util.ext.start
import hanten.wre.app.core.util.ext.textAndVisible
import hanten.wre.app.core.util.ext.toUriOrNull
import hanten.wre.app.databinding.ActivityDetailsBinding
import hanten.wre.app.databinding.LayoutDetailsTableBinding
import hanten.wre.app.details.data.MangaDetails
import hanten.wre.app.details.data.ReadingTime
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerRelated
import hanten.wre.app.details.service.MangaPrefetchService
import hanten.wre.app.details.ui.model.ChapterListItem
import hanten.wre.app.details.ui.model.HistoryInfo
import hanten.wre.app.details.ui.scrobbling.ScrobblingItemDecoration
import hanten.wre.app.details.ui.scrobbling.ScrollingInfoAdapter
import hanten.wre.app.download.ui.worker.DownloadStartedObserver
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.adapter.cardPercent
import hanten.wre.app.list.ui.adapter.mangaGridItemAD
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.MangaListModel
import hanten.wre.app.list.ui.size.StaticItemSizeResolver
import hanten.wre.app.main.ui.owners.BottomSheetOwner
import hanten.wre.app.parsers.model.ContentRating
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.parsers.util.ifNullOrEmpty
import hanten.wre.app.parsers.util.nullIfEmpty
import hanten.wre.app.parsers.util.toTitleCase
import hanten.wre.app.scrobbling.common.domain.model.ScrobblingInfo
import javax.inject.Inject
import kotlin.math.roundToInt
import com.google.android.material.R as materialR

@AndroidEntryPoint
class DetailsActivity :
	BaseActivity<ActivityDetailsBinding>(),
	View.OnClickListener,
	View.OnLayoutChangeListener,
	ViewTreeObserver.OnDrawListener,
	ChipsView.OnChipClickListener,
	OnListItemClickListener<Bookmark>,
	SwipeRefreshLayout.OnRefreshListener,
	AuthorSpan.OnAuthorClickListener,
	BottomSheetOwner {

	@Inject
	lateinit var shortcutManager: AppShortcutManager

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var settings: AppSettings

	private val viewModel: DetailsViewModel by viewModels()
	private lateinit var menuProvider: DetailsMenuProvider
	private lateinit var infoBinding: LayoutDetailsTableBinding
	private var tabsController: DetailsTabsController? = null

	// Hanten 3.0: chapters are on the page now (phone); tablets keep their side panel
	override val bottomSheet: View?
		get() = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityDetailsBinding.inflate(layoutInflater))
		infoBinding = LayoutDetailsTableBinding.bind(viewBinding.root)
		setupCoverTransition()
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.setDisplayShowTitleEnabled(false)
		viewBinding.chipFavorite?.setOnClickListener(this)
		viewBinding.buttonFavorite?.setOnClickListener(this)
		viewBinding.buttonDownload?.setOnClickListener(this)
		viewBinding.splitButtonRead?.let { ReadButtonDelegate(it, viewModel, router).attach(this) }
		infoBinding.textViewLocal.setOnClickListener(this)
		infoBinding.textViewSource.setOnClickListener(this)
		viewBinding.imageViewCover.setOnClickListener(this)
		viewBinding.textViewTitle.setOnClickListener(this)
		viewBinding.buttonDescriptionMore.setOnClickListener(this)
		viewBinding.buttonScrobblingMore.setOnClickListener(this)
		viewBinding.textViewDescription.addOnLayoutChangeListener(this)
		viewBinding.swipeRefreshLayout.setOnRefreshListener(this)
		viewBinding.textViewDescription.viewTreeObserver.addOnDrawListener(this)
		infoBinding.textViewAuthor.movementMethod = LinkMovementMethodCompat.getInstance()
		viewBinding.textViewDescription.movementMethod = LinkMovementMethodCompat.getInstance()
		viewBinding.chipsTags.onChipClickListener = this
		if (viewBinding.layoutHeader == null) {
			// tablets: the toolbar shows the title once it scrolls away; on phones the big header title stays visible
			TitleScrollCoordinator(viewBinding.textViewTitle).attach(viewBinding.scrollView)
		}
		if (settings.isDescriptionExpanded) {
			viewBinding.textViewDescription.maxLines = Int.MAX_VALUE - 1
		}
		val tabs = viewBinding.tabsDetails
		val menuToolbar = viewBinding.toolbarChapters
		if (tabs != null && menuToolbar != null) {
			val isNovel = viewModel.manga.value?.isNovel == true
			tabsController = DetailsTabsController(
				activity = this,
				tabs = tabs,
				appBar = viewBinding.appbar,
				infoScroll = viewBinding.scrollView,
				infoContainer = viewBinding.swipeRefreshLayout,
				similarTarget = viewBinding.layoutSimilar,
				containers = buildMap {
					viewBinding.containerChapters?.let { put(ChaptersPagesSheet.TAB_CHAPTERS, it) }
					viewBinding.containerPages?.let { put(ChaptersPagesSheet.TAB_PAGES, it) }
					viewBinding.containerBookmarks?.let { put(ChaptersPagesSheet.TAB_BOOKMARKS, it) }
				},
				menuToolbar = menuToolbar,
				viewModel = viewModel,
				settings = settings,
				isPagesTabEnabled = settings.isPagesTabEnabled && !isNovel,
			)
			val menuInvalidator = MenuInvalidator(menuToolbar)
			viewModel.isChaptersReversed.observe(this, menuInvalidator)
			viewModel.isChaptersInGridView.observe(this, menuInvalidator)
			viewModel.isDownloadedOnly.observe(this, menuInvalidator)
			viewModel.emptyReason.observe(this, menuInvalidator)
		}

		val appRouter = router
		viewModel.mangaDetails.filterNotNull().observe(this, ::onMangaUpdated)
		viewModel.coverUrl.observe(this, ::loadCover)
		viewModel.onMangaRemoved.observeEvent(this, ::onMangaRemoved)
		viewModel.onError
			.filterNot { appRouter.isChapterPagesSheetShown() }
			.observeEvent(this, DetailsErrorObserver(this, viewModel, exceptionResolver))
		viewModel.onActionDone
			.filterNot { appRouter.isChapterPagesSheetShown() }
			.observeEvent(this, ReversibleActionObserver(viewBinding.scrollView))
		combine(viewModel.historyInfo, viewModel.isLoading, ::Pair).observe(this) {
			onHistoryChanged(it.first, it.second)
		}
		viewModel.isLoading.observe(this, ::onLoadingStateChanged)
		viewModel.scrobblingInfo.observe(this, ::onScrobblingInfoChanged)
		viewModel.localSize.observe(this, ::onLocalSizeChanged)
		viewModel.relatedWorks.observe(this, ::onRelatedWorksChanged)
		val similarAdapter = BaseListAdapter<ListModel>().addDelegate(
			ListItemType.MANGA_GRID,
			mangaGridItemAD(
				StaticItemSizeResolver(resources.getDimensionPixelSize(R.dimen.today_cover_width)),
				object : OnListItemClickListener<MangaListModel> {
					override fun onItemClick(item: MangaListModel, view: View) {
						router.openDetails(item.toMangaWithOverride(), itemView = view)
					}
				},
			),
		)
		viewBinding.recyclerViewSimilar.adapter = similarAdapter
		viewModel.similarManga.observe(this) { list ->
			similarAdapter.emit(list)
			viewBinding.textViewSimilarTitle.isVisible = list.isNotEmpty()
			viewBinding.recyclerViewSimilar.isVisible = list.isNotEmpty()
			updateSimilarTab()
		}
		viewModel.favouriteCategories.observe(this, ::onFavoritesChanged)
		val menuInvalidator = MenuInvalidator(this)
		viewModel.isStatsAvailable.observe(this, menuInvalidator)
		viewModel.remoteManga.observe(this, menuInvalidator)
		viewModel.tags.observe(this, ::onTagsChanged)
		viewModel.chapters.observe(this, PrefetchObserver(this))
		viewModel.onDownloadStarted
			.filterNot { appRouter.isChapterPagesSheetShown() }
			.observeEvent(this, DownloadStartedObserver(viewBinding.scrollView))
		menuProvider = DetailsMenuProvider(
			activity = this,
			viewModel = viewModel,
			snackbarHost = viewBinding.scrollView,
			appShortcutManager = shortcutManager,
			settings = settings,
		)
		addMenuProvider(menuProvider)
	}

	override fun onProvideAssistContent(outContent: AssistContent) {
		super.onProvideAssistContent(outContent)
		viewModel.getMangaOrNull()?.publicUrl?.toUriOrNull()?.let { outContent.webUri = it }
	}

	override fun isNsfwContent(): Flow<Boolean> = viewModel.manga.map { it?.contentRating == ContentRating.ADULT }

	override fun onClick(v: View) {
		when (v.id) {
			R.id.textView_source -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.openList(manga.source, null, null)
			}

			R.id.textView_local -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.showLocalInfoDialog(manga)
			}

			R.id.chip_favorite, R.id.button_favorite -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.showFavoriteDialog(manga)
			}

			R.id.button_download -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.showDownloadDialog(manga, viewBinding.scrollView)
			}

			R.id.imageView_cover -> {
				val manga = viewModel.getMangaOrNull() ?: return
				router.openImage(
					url = viewModel.coverUrl.value ?: return,
					source = manga.source,
					preview = CoilMemoryCacheKey.from(viewBinding.imageViewCover),
					anchor = v,
				)
			}

			R.id.button_description_more -> {
				val tv = viewBinding.textViewDescription
                val defaultMaxLines = resources.getInteger(R.integer.details_description_lines)

                if (tv.context.isAnimationsEnabled) {
					tv.parentView?.let {
						TransitionManager.beginDelayedTransition(it)
					}
				}
				if (tv.maxLines in 1 until Integer.MAX_VALUE) {
					tv.maxLines = Integer.MAX_VALUE
				} else {
					tv.maxLines = defaultMaxLines
				}

                val btn = viewBinding.buttonDescriptionMore
                if (tv.maxLines == defaultMaxLines) {
                    btn.setText(R.string.more)
                } else {
                    btn.setText(R.string.collapse)
                }
			}

			R.id.button_scrobbling_more -> {
				router.showScrobblingSelectorSheet(
					manga = viewModel.getMangaOrNull() ?: return,
					scrobblerService = viewModel.scrobblingInfo.value.firstOrNull()?.scrobbler,
				)
			}

			R.id.textView_title -> showTitlesDialog()
		}
	}

	/** All known titles of the work; a tap copies one, the button copies them all. */
	private fun showTitlesDialog() {
		val manga = viewModel.getMangaOrNull() ?: return
		val titles = (listOf(manga.title) + manga.altTitles)
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.distinct()
		if (titles.isEmpty()) return
		val label = getString(R.string.details_titles)
		buildAlertDialog(this) {
			setTitle(label)
			setItems(titles.toTypedArray()) { _, which ->
				copyToClipboard(label, titles[which])
				Toast.makeText(this@DetailsActivity, R.string.details_title_copied, Toast.LENGTH_SHORT).show()
			}
			setNegativeButton(R.string.close, null)
			if (titles.size > 1) {
				setPositiveButton(R.string.details_titles_copy_all) { _, _ ->
					copyToClipboard(label, titles.joinToString("\n"))
					Toast.makeText(this@DetailsActivity, R.string.details_title_copied, Toast.LENGTH_SHORT).show()
				}
			}
		}.show()
	}

	/**
	 * Long titles: shrink the header title (22sp down to 14sp) until it fits in [TITLE_MAX_LINES]
	 * and no single word has to be broken across lines.
	 */
	private fun fitTitle(textView: TextView) {
		val text = textView.text?.toString().orEmpty()
		val width = textView.width - textView.paddingLeft - textView.paddingRight
		if (text.isEmpty() || width <= 0) return
		val paint = TextPaint(textView.paint)
		val dm = resources.displayMetrics
		val words = text.split(' ', '\n').filter { it.isNotEmpty() }
		var sizeSp = TITLE_MAX_SP
		var lines = TITLE_MAX_LINES
		while (true) {
			paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm)
			val longestWord = words.maxOfOrNull { paint.measureText(it) } ?: 0f
			val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
				.setLineSpacing(0f, textView.lineSpacingMultiplier)
				.setIncludePad(textView.includeFontPadding)
				.build()
			if (longestWord <= width && layout.lineCount <= TITLE_MAX_LINES) break
			if (sizeSp <= TITLE_MIN_SP) {
				// Light-novel-length titles: even the smallest size needs more lines, so let the header
				// grow a bit; anything longer is ellipsized (the full title is in the titles dialog).
				lines = layout.lineCount.coerceIn(TITLE_MAX_LINES, TITLE_MAX_LINES_LONG)
				break
			}
			sizeSp -= 1f
		}
		if (textView.maxLines != lines) {
			textView.maxLines = lines
		}
		if (textView.textSize != TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm)) {
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
		}
	}

	override fun onAuthorClick(author: String) {
		router.showAuthorDialog(author, viewModel.getMangaOrNull()?.source ?: return)
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		val tag = data as? MangaTag ?: return
		router.showTagDialog(tag)
	}

	override fun onItemClick(item: Bookmark, view: View) {
		router.openReader(ReaderIntent.Builder(view.context).bookmark(item).build())
	}

	override fun onRefresh() {
		viewModel.reload()
	}

	override fun onDraw() {
		viewBinding.run {
			buttonDescriptionMore.isVisible = textViewDescription.maxLines == Int.MAX_VALUE ||
				textViewDescription.isTextTruncated
		}
	}

	override fun onLayoutChange(
		v: View?,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		oldLeft: Int,
		oldTop: Int,
		oldRight: Int,
		oldBottom: Int
	) {
		// No bottom sheet anymore: nothing to keep in sync with its peek height
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		if (viewBinding.cardChapters != null) {
			// landscape
			viewBinding.cardChapters?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				topMargin = barsInsets.top + resources.getDimensionPixelOffset(R.dimen.grid_spacing_outer)
				marginEnd = barsInsets.end(v) + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
				bottomMargin = barsInsets.bottom + resources.getDimensionPixelOffset(R.dimen.side_card_offset)
			}
			viewBinding.scrollView.updatePaddingRelative(
				bottom = barsInsets.bottom,
				start = barsInsets.start(v),
			)
			viewBinding.appbar.updatePaddingRelative(
				start = barsInsets.start(v),
			)
			return insets.consume(v, typeMask, bottom = true, end = true)
		} else {
			viewBinding.layoutContent?.updatePadding(bottom = barsInsets.bottom)
			return insets
		}
	}

	private fun onFavoritesChanged(categories: Set<FavouriteCategory>) {
		val label = if (categories.isEmpty()) {
			getString(R.string.add_to_favourites)
		} else {
			categories.joinToStringWithLimit(this, FAV_LABEL_LIMIT) { it.title }
		}
		val icon = if (categories.isEmpty()) R.drawable.ic_heart_outline else R.drawable.ic_heart
		viewBinding.chipFavorite?.let { chip ->
			chip.setChipIconResource(icon)
			chip.text = label
		}
		viewBinding.buttonFavorite?.let { button ->
			button.setIconResource(icon)
			button.contentDescription = label
			button.setTooltipCompat(label)
			button.iconTint = ColorStateList.valueOf(
				getThemeColor(
					if (categories.isEmpty()) {
						com.google.android.material.R.attr.colorOnSurface
					} else {
						androidx.appcompat.R.attr.colorPrimary
					},
				),
			)
		}
	}

	private fun onLocalSizeChanged(size: Long) {
		if (size == 0L) {
			infoBinding.textViewLocal.isVisible = false
			infoBinding.textViewLocalLabel.isVisible = false
		} else {
			infoBinding.textViewLocal.text = FileSize.BYTES.format(this, size)
			infoBinding.textViewLocal.isVisible = true
			infoBinding.textViewLocalLabel.isVisible = true
		}
	}

	private fun onRelatedWorksChanged(related: List<ScrobblerRelated>) {
		val textView = viewBinding.textViewRelatedWorks
		if (related.isEmpty()) {
			textView.isGone = true
			updateSimilarTab()
			return
		}
		val linkColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary, Color.LTGRAY)
		val text = SpannableStringBuilder()
		text.append(getString(R.string.related_works_tracker))
		for ((relation, items) in related.groupBy { it.relation }) {
			text.append('\n')
			if (relation.isNotBlank()) {
				text.append(relation).append(": ")
			}
			items.forEachIndexed { index, item ->
				if (index > 0) text.append(", ")
				val start = text.length
				text.append('«').append(item.title).append('»')
				// The related work may live on another source: search all of them by title.
				text.setSpan(
					object : ClickableSpan() {
						override fun onClick(widget: View) {
							router.openSearch(item.title)
						}

						override fun updateDrawState(ds: TextPaint) {
							ds.color = linkColor
							ds.isUnderlineText = false
						}
					},
					start,
					text.length,
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
				)
			}
		}
		textView.text = text
		textView.movementMethod = LinkMovementMethodCompat.getInstance()
		textView.isVisible = true
		updateSimilarTab()
	}

	private fun updateSimilarTab() {
		tabsController?.setSimilarVisible(
			viewBinding.textViewRelatedWorks.isVisible || viewBinding.recyclerViewSimilar.isVisible,
		)
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		viewBinding.swipeRefreshLayout.isRefreshing = isLoading
	}

	private fun onScrobblingInfoChanged(scrobblings: List<ScrobblingInfo>) {
		var adapter = viewBinding.recyclerViewScrobbling.adapter as? ScrollingInfoAdapter
		viewBinding.groupScrobbling.isGone = scrobblings.isEmpty()
		if (adapter != null) {
			adapter.items = scrobblings
		} else {
			adapter = ScrollingInfoAdapter(router)
			adapter.items = scrobblings
			viewBinding.recyclerViewScrobbling.adapter = adapter
			viewBinding.recyclerViewScrobbling.addItemDecoration(ScrobblingItemDecoration())
		}
	}

	private fun onMangaUpdated(details: MangaDetails) {
		val manga = details.toManga()
		with(viewBinding) {
			textViewTitle.text = manga.title
			if (textHeaderType != null) {
				textViewTitle.doOnLayout { fitTitle(it as TextView) }
			}
			if (textHeaderType == null) {
				// tablet layout: the old subtitle (type + alternative titles)
				textViewSubtitle.textAndVisible = buildString {
					append(getString(manga.contentType.titleResId))
					if (manga.altTitles.isNotEmpty()) {
						append('\n')
						append(manga.altTitles.joinToString("\n"))
					}
				}
			} else {
				// Hanten 3.0 header: "Author · Status", alternative titles below; type/source/rating as pills
				textViewSubtitle.textAndVisible = buildString {
					val authors = manga.authors.take(2).joinToString(", ")
					append(authors)
					manga.state?.let { state ->
						if (isNotEmpty()) append(" · ")
						append(getString(state.titleResId))
					}
					if (manga.altTitles.isNotEmpty()) {
						if (isNotEmpty()) append('\n')
						append(manga.altTitles.joinToString("\n"))
					}
				}
				textHeaderType?.text = getString(manga.contentType.titleResId)
				textHeaderSource?.textAndVisible = if (
					manga.source == LocalMangaSource || manga.source == UnknownMangaSource
				) {
					null
				} else {
					manga.source.getTitle(this@DetailsActivity)
				}
				textHeaderRating?.textAndVisible = if (manga.hasRating) {
					"★ " + String.format("%.1f", manga.rating * 10)
				} else {
					null
				}
			}
			textViewNsfw16.isVisible = manga.contentRating == ContentRating.SUGGESTIVE
			textViewNsfw18.isVisible = manga.contentRating == ContentRating.ADULT
			textViewDescription.text = details.description.ifNullOrEmpty { getString(R.string.no_description) }
		}
		with(infoBinding) {
			val translation = details.getLocale()
			infoBinding.textViewTranslation.textAndVisible = translation?.getDisplayLanguage(translation)
				?.toTitleCase(translation)
			infoBinding.textViewTranslation.drawableStart = translation?.let {
				LocaleUtils.getEmojiFlag(it)
			}?.let {
				TextDrawable.compound(infoBinding.textViewTranslation, it)
			}
			infoBinding.textViewTranslationLabel.isVisible = infoBinding.textViewTranslation.isVisible
			textViewAuthor.textAndVisible = manga.getAuthorsString()
			textViewAuthorLabel.isVisible = textViewAuthor.isVisible
			if (manga.hasRating) {
				ratingBarRating.rating = manga.rating * ratingBarRating.numStars
				ratingBarRating.isVisible = true
				textViewRatingLabel.isVisible = true
				textViewRatingValue.text = String.format("%.1f", manga.rating * 10)
				textViewRatingValue.isVisible = true
			} else {
				ratingBarRating.isVisible = false
				textViewRatingLabel.isVisible = false
				textViewRatingValue.isVisible = false
			}
			manga.state?.let { state ->
				textViewState.textAndVisible = resources.getString(state.titleResId)
				textViewStateLabel.isVisible = textViewState.isVisible
			} ?: run {
				textViewState.isVisible = false
				textViewStateLabel.isVisible = false
			}

			if (manga.source == LocalMangaSource || manga.source == UnknownMangaSource) {
				textViewSource.isVisible = false
				textViewSourceLabel.isVisible = false
			} else {
				val sourceTitle = manga.source.getTitle(this@DetailsActivity)
				textViewSource.textAndVisible = if (sourceTitle == getString(R.string.unknown)) {
					viewModel.sourceTitle ?: sourceTitle
				} else {
					sourceTitle
				}
				textViewSource.setTooltipCompat(manga.source.getSummary(this@DetailsActivity))
				textViewSourceLabel.isVisible = textViewSource.isVisible == true
			}
			val faviconPlaceholderFactory = FaviconDrawable.Factory(R.style.FaviconDrawable_Chip)
			ImageRequest.Builder(this@DetailsActivity)
				.data(manga.source.faviconUri())
				.lifecycle(this@DetailsActivity)
				.crossfade(false)
				.precision(Precision.EXACT)
				.size(resources.getDimensionPixelSize(materialR.dimen.m3_chip_icon_size))
				.target(TextViewTarget(textViewSource, Gravity.START))
				.placeholder(faviconPlaceholderFactory)
				.error(faviconPlaceholderFactory)
				.fallback(faviconPlaceholderFactory)
				.mangaSourceExtra(manga.source)
				.transformations(RoundedCornersTransformation(resources.getDimension(R.dimen.chip_icon_corner)))
				.allowRgb565(true)
				.enqueueWith(coil)
		}
		title = manga.title
		invalidateOptionsMenu()
	}

	private fun onMangaRemoved(manga: Manga) {
		Toast.makeText(
			this,
			getString(R.string._s_deleted_from_local_storage, manga.title),
			Toast.LENGTH_SHORT,
		).show()
		finishAfterTransition()
	}

	private fun onHistoryChanged(info: HistoryInfo, isLoading: Boolean) = with(infoBinding) {
		tabsController?.setChaptersCount(if (isLoading) 0 else info.totalChapters)
		textViewChapters.text = when {
			isLoading -> getString(R.string.loading_)
			info.currentChapter >= 0 -> getString(
				R.string.chapter_d_of_d,
				info.currentChapter + 1,
				info.totalChapters,
			).withEstimatedTime(info.estimatedTime)

			info.totalChapters == 0 -> getString(R.string.no_chapters)
			info.totalChapters == -1 -> getString(R.string.error_occurred)
			else -> resources.getQuantityStringSafe(R.plurals.chapters, info.totalChapters, info.totalChapters)
				.withEstimatedTime(info.estimatedTime)
		}
		textViewProgress.textAndVisible = if (info.percent <= 0f) {
			null
		} else {
			getString(R.string.percent_string_pattern, cardPercent(info.percent))
		}

		progress.setProgressCompat(
			(progress.max * info.percent.coerceIn(0f, 1f)).roundToInt(),
			true,
		)
		textViewProgressLabel.isVisible = info.history != null
		textViewProgress.isVisible = info.history != null
		progress.isVisible = info.history != null
	}

	private fun onTagsChanged(tags: Collection<ChipsView.ChipModel>) {
		viewBinding.chipsTags.isVisible = tags.isNotEmpty()
		viewBinding.chipsTags.setChips(tags)
	}

	/** Cover flight from the list (AppRouter.openDetails): wait until the cover is drawn, at most ~400 ms. */
	private fun setupCoverTransition() {
		val name = intent.getStringExtra(AppRouter.KEY_COVER_TRANSITION) ?: return
		val cover = viewBinding.imageViewCover
		ViewCompat.setTransitionName(cover, name)
		supportPostponeEnterTransition()
		var attempts = 0
		val starter = object : Runnable {
			override fun run() {
				if (cover.drawable != null || ++attempts >= 13 || isFinishing) {
					supportStartPostponedEnterTransition()
				} else {
					cover.postDelayed(this, 30L)
				}
			}
		}
		cover.post(starter)
	}

	private fun loadCover(imageUrl: String?) {
		viewBinding.imageViewCover.setImageAsync(imageUrl, viewModel.getMangaOrNull())
        viewBinding.imageViewBlurBackground?.setImageAsync(imageUrl, viewModel.getMangaOrNull())
	}

	private fun String.withEstimatedTime(time: ReadingTime?): String {
		if (time == null) {
			return this
		}
		val timeFormatted = time.formatShort(resources)
		return getString(R.string.chapters_time_multiline, this, timeFormatted)
	}

	private fun Manga.getAuthorsString(): SpannedString? {
		if (authors.isEmpty()) {
			return null
		}
		return buildSpannedString {
			authors.forEach { a ->
				if (a.isNotEmpty()) {
					if (isNotEmpty()) {
						append(", ")
					}
					inSpans(AuthorSpan(this@DetailsActivity)) {
						append(a)
					}
				}
			}
		}.nullIfEmpty()
	}

	private class PrefetchObserver(
		private val context: Context,
	) : FlowCollector<List<ChapterListItem>?> {

		private var isCalled = false

		override suspend fun emit(value: List<ChapterListItem>?) {
			if (value.isNullOrEmpty()) {
				return
			}
			if (!isCalled) {
				isCalled = true
				val item = value.find { it.isCurrent } ?: value.first()
				MangaPrefetchService.prefetchPages(context, item.chapter)
			}
		}
	}

	companion object {

		private const val TITLE_MAX_SP = 22f
		private const val TITLE_MIN_SP = 14f
		private const val TITLE_MAX_LINES = 4
		private const val TITLE_MAX_LINES_LONG = 7

		private const val FAV_LABEL_LIMIT = 16
	}
}
