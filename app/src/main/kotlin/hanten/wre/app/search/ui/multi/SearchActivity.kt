package hanten.wre.app.search.ui.multi

import hanten.wre.app.extras.Celebrations
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.exceptions.resolve.SnackbarErrorObserver
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.list.ListSelectionController
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.ui.widgets.TipView
import hanten.wre.app.core.util.ShareHelper
import hanten.wre.app.core.util.ext.consumeAllSystemBarsInsets
import hanten.wre.app.core.util.ext.invalidateNestedItemDecorations
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.core.util.ext.systemBarsInsets
import hanten.wre.app.databinding.ActivitySearchBinding
import hanten.wre.app.list.domain.ListFilterOption
import hanten.wre.app.list.ui.MangaSelectionDecoration
import hanten.wre.app.list.ui.adapter.MangaListListener
import hanten.wre.app.list.ui.adapter.TypedListSpacingDecoration
import hanten.wre.app.list.ui.model.ListHeader
import hanten.wre.app.list.ui.model.MangaListModel
import hanten.wre.app.list.ui.size.DynamicItemSizeResolver
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaTag
import hanten.wre.app.search.domain.SearchKind
import hanten.wre.app.search.ui.multi.adapter.SearchAdapter
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity :
	BaseActivity<ActivitySearchBinding>(),
	MangaListListener,
	ListSelectionController.Callback {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<SearchViewModel>()
	private lateinit var selectionController: ListSelectionController

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySearchBinding.inflate(layoutInflater))
		title = viewModel.query
		initHeader()

		val itemClickListener = OnListItemClickListener<SearchResultsListModel> { item, view ->
			if (item.listFilter == null) {
				router.openSearch(item.source, viewModel.query)
			} else {
				router.openList(item.source, item.listFilter, item.sortOrder)
			}
		}
		val sizeResolver = DynamicItemSizeResolver(resources, this, settings, adjustWidth = true)
		val selectionDecoration = MangaSelectionDecoration(this)
		selectionController = ListSelectionController(
			appCompatDelegate = delegate,
			decoration = selectionDecoration,
			registryOwner = this,
			callback = this,
		)
		val adapter = SearchAdapter(
			listener = this,
			itemClickListener = itemClickListener,
			sizeResolver = sizeResolver,
			selectionDecoration = selectionDecoration,
		)
		viewBinding.recyclerView.adapter = adapter
		viewBinding.recyclerView.setHasFixedSize(true)
		viewBinding.recyclerView.addItemDecoration(TypedListSpacingDecoration(this, true))

		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.setDisplayShowTitleEnabled(false)

		viewModel.list.observe(this, adapter)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
		// easter egg: «ня» → cat paws
		if (savedInstanceState == null && settings.isEasterEggsEnabled &&
			viewModel.query.trim().trimEnd('~', '!').lowercase() in NYA_WORDS
		) {
			viewBinding.recyclerView.postDelayed({ Celebrations.paws(this) }, 300)
		}
	}

	// region Hanten 3.0 header: editable query, search kind and filters as chips (were a toolbar menu)

	private fun initHeader() {
		with(viewBinding.editQuery) {
			setText(viewModel.query)
			setSelection(text.length)
			setOnEditorActionListener { _, actionId, event ->
				val isSearch = actionId == EditorInfo.IME_ACTION_SEARCH ||
					(event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
				if (isSearch) {
					reopen(text?.toString().orEmpty().trim(), viewModel.kind)
				}
				isSearch
			}
		}
		viewBinding.buttonClear.setOnClickListener {
			viewBinding.editQuery.text = null
			viewBinding.editQuery.requestFocus()
			getSystemService(InputMethodManager::class.java)
				?.showSoftInput(viewBinding.editQuery, InputMethodManager.SHOW_IMPLICIT)
		}
		viewBinding.chipsKind.check(
			when (viewModel.kind) {
				SearchKind.SIMPLE -> R.id.chip_kind_simple
				SearchKind.TITLE -> R.id.chip_kind_title
				SearchKind.AUTHOR -> R.id.chip_kind_author
				SearchKind.TAG -> R.id.chip_kind_tag
			},
		)
		viewBinding.chipsKind.setOnCheckedStateChangeListener { _, checkedIds ->
			val kind = when (checkedIds.firstOrNull()) {
				R.id.chip_kind_title -> SearchKind.TITLE
				R.id.chip_kind_author -> SearchKind.AUTHOR
				R.id.chip_kind_tag -> SearchKind.TAG
				else -> SearchKind.SIMPLE
			}
			if (kind != viewModel.kind) {
				reopen(viewModel.query, kind)
			}
		}
		viewBinding.chipPinnedOnly.setOnCheckedChangeListener { _, isChecked -> viewModel.setPinnedOnly(isChecked) }
		viewBinding.chipHideEmpty.setOnCheckedChangeListener { _, isChecked -> viewModel.setHideEmpty(isChecked) }
	}

	private fun reopen(query: String, kind: SearchKind) {
		if (query.isEmpty() || (query == viewModel.query && kind == viewModel.kind)) {
			return
		}
		router.openSearch(query = query, kind = kind)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
		} else {
			@Suppress("DEPRECATION")
			overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
		}
		finishAfterTransition()
	}

	// endregion

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.layoutHeader.updatePadding(
			top = barsInsets.top,
			left = barsInsets.left,
			right = barsInsets.right,
		)
		viewBinding.recyclerView.setPadding(
			left = barsInsets.left,
			top = 0,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onItemClick(item: MangaListModel, view: View) {
		if (!selectionController.onItemClick(item.id)) {
			val manga = item.toMangaWithOverride()
			val sourceTitle = viewModel.list.value.filterIsInstance<SearchResultsListModel>()
				.find { it.source == manga.source }
				?.getTitle(this)
			router.openDetails(manga, sourceTitle, view)
		}
	}

	override fun onItemLongClick(item: MangaListModel, view: View): Boolean {
		return selectionController.onItemLongClick(view, item.id)
	}

	override fun onItemContextClick(item: MangaListModel, view: View): Boolean {
		return selectionController.onItemContextClick(view, item.id)
	}

	override fun onReadClick(manga: Manga, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			router.openReader(manga)
		}
	}

	override fun onTagClick(manga: Manga, tag: MangaTag, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			router.openList(tag)
		}
	}

	override fun onRetryClick(error: Throwable) {
		viewModel.retry()
	}

	override fun onFilterOptionClick(option: ListFilterOption) = Unit

	override fun onFilterClick(view: View?) = Unit

	override fun onEmptyActionClick() = viewModel.continueSearch()

	override fun onListHeaderClick(item: ListHeader, view: View) = Unit

	override fun onFooterButtonClick() = viewModel.continueSearch()

	override fun onPrimaryButtonClick(tipView: TipView) = Unit

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding.recyclerView.invalidateNestedItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_remote, menu)
		return true
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_share -> {
				ShareHelper(this).shareMangaLinks(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_favourite -> {
				router.showFavoriteDialog(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_save -> {
				router.showDownloadDialog(collectSelectedItems(), viewBinding.recyclerView)
				mode?.finish()
				true
			}

			else -> false
		}
	}

	private fun collectSelectedItems(): Set<Manga> {
		return viewModel.getItems(selectionController.peekCheckedIds())
	}

	private companion object {

		val NYA_WORDS = setOf("ня", "nya", "nyan", "ня-ня", "няя", "няшка")
	}
}
