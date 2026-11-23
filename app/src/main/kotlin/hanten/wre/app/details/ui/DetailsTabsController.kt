package hanten.wre.app.details.ui

import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import hanten.wre.app.R
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.util.RecyclerViewOwner
import hanten.wre.app.core.util.ext.smoothScrollToTop
import hanten.wre.app.details.ui.pager.ChapterPagesMenuProvider
import hanten.wre.app.details.ui.pager.ChaptersPagesSheet.Companion.TAB_BOOKMARKS
import hanten.wre.app.details.ui.pager.ChaptersPagesSheet.Companion.TAB_CHAPTERS
import hanten.wre.app.details.ui.pager.ChaptersPagesSheet.Companion.TAB_PAGES
import hanten.wre.app.details.ui.pager.ChaptersPagesViewModel
import hanten.wre.app.details.ui.pager.bookmarks.BookmarksFragment
import hanten.wre.app.details.ui.pager.chapters.ChaptersFragment
import hanten.wre.app.details.ui.pager.pages.PagesFragment

/**
 * Hanten 3.0 title card tabs: "Chapters · N / Info / Pages / Bookmarks / Similar".
 * Chapters, pages and bookmarks are the same fragments the old bottom sheet showed, now placed on the page;
 * the sheet's menu (chapter search, order, grid, downloaded only, page size) lives next to the tabs.
 */
class DetailsTabsController(
	private val activity: FragmentActivity,
	private val tabs: TabLayout,
	private val appBar: AppBarLayout,
	private val infoScroll: NestedScrollView,
	private val infoContainer: View,
	private val similarTarget: View,
	private val containers: Map<Int, View>,
	private val menuToolbar: Toolbar,
	viewModel: ChaptersPagesViewModel,
	settings: AppSettings,
	isPagesTabEnabled: Boolean,
) : TabLayout.OnTabSelectedListener {

	private val chaptersTab = tabs.newTab().setText(R.string.chapters).setTag(TAB_CHAPTERS)
	private val infoTab = tabs.newTab().setText(R.string.details_tab_info).setTag(TAB_INFO)
	private val pagesTab = if (isPagesTabEnabled) tabs.newTab().setText(R.string.pages).setTag(TAB_PAGES) else null
	private val bookmarksTab = tabs.newTab().setText(R.string.bookmarks).setTag(TAB_BOOKMARKS)

	// A removed tab is recycled by TabLayout, so the Similar tab is created anew each time it is shown
	private var similarTab: TabLayout.Tab? = null
	private var currentTab = TAB_CHAPTERS
	private val menuProvider = ChapterPagesMenuProvider(
		viewModel = viewModel,
		settings = settings,
		currentTab = { currentTab },
		onSearchExpanded = { isExpanded -> if (isExpanded) appBar.setExpanded(false, true) },
	)

	init {
		tabs.addTab(chaptersTab, false)
		tabs.addTab(infoTab, false)
		pagesTab?.let { tabs.addTab(it, false) }
		tabs.addTab(bookmarksTab, false)
		menuToolbar.addMenuProvider(menuProvider)
		activity.onBackPressedDispatcher.addCallback(activity, menuProvider)
		val initial = when (settings.defaultDetailsTab) {
			TAB_PAGES -> pagesTab ?: chaptersTab
			TAB_BOOKMARKS -> bookmarksTab
			else -> chaptersTab
		}
		initial.select()
		show(initial.tag as Int)
		tabs.addOnTabSelectedListener(this)
	}

	fun setChaptersCount(count: Int) {
		chaptersTab.text = if (count > 0) {
			tabs.context.getString(R.string.details_tab_chapters_count, count)
		} else {
			tabs.context.getString(R.string.chapters)
		}
	}

	fun setSimilarVisible(isVisible: Boolean) {
		val current = similarTab
		if (isVisible == (current != null)) return
		if (current == null) {
			similarTab = tabs.newTab().setText(R.string.details_tab_similar).setTag(TAB_SIMILAR).also {
				tabs.addTab(it, false)
			}
		} else {
			if (current.isSelected) infoTab.select()
			tabs.removeTab(current)
			similarTab = null
		}
	}

	fun invalidateMenu() = menuToolbar.invalidateMenu()

	override fun onTabSelected(tab: TabLayout.Tab) = show(tab.tag as Int)

	override fun onTabUnselected(tab: TabLayout.Tab) = Unit

	override fun onTabReselected(tab: TabLayout.Tab) {
		when (val page = tab.tag as Int) {
			TAB_INFO -> infoScroll.smoothScrollTo(0, 0)
			TAB_SIMILAR -> scrollInfoTo(similarTarget)
			else -> (activity.supportFragmentManager.findFragmentByTag(tagOf(page)) as? RecyclerViewOwner)
				?.recyclerView?.smoothScrollToTop()
		}
	}

	private fun show(page: Int) {
		currentTab = page
		val isInfo = page == TAB_INFO || page == TAB_SIMILAR
		infoContainer.isVisible = isInfo
		for ((key, container) in containers) {
			container.isVisible = key == page
		}
		if (!isInfo) {
			ensureFragment(page)
		}
		if (page == TAB_SIMILAR) {
			scrollInfoTo(similarTarget)
		}
		menuToolbar.isVisible = !isInfo
		menuToolbar.invalidateMenu()
	}

	private fun ensureFragment(page: Int) {
		val fm = activity.supportFragmentManager
		val tag = tagOf(page)
		if (fm.findFragmentByTag(tag) != null) return
		val container = containers[page] ?: return
		val fragment: Fragment = when (page) {
			TAB_PAGES -> PagesFragment()
			TAB_BOOKMARKS -> BookmarksFragment()
			else -> ChaptersFragment()
		}
		fm.commit {
			setReorderingAllowed(true)
			add(container.id, fragment, tag)
		}
	}

	private fun scrollInfoTo(target: View) {
		appBar.setExpanded(false, true)
		target.post { infoScroll.smoothScrollTo(0, target.top) }
	}

	private fun tagOf(page: Int) = "details_tab_$page"

	companion object {

		const val TAB_INFO = 10
		const val TAB_SIMILAR = 11
	}
}
