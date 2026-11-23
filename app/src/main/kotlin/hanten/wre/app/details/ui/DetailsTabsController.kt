package hanten.wre.app.details.ui

import android.view.View
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import hanten.wre.app.R

/**
 * Tabs row under the title header: "Chapters · N / Info / Similar".
 * Chapters opens the existing chapters sheet, Info and Similar scroll the page.
 */
class DetailsTabsController(
	private val tabs: TabLayout,
	sheet: View,
	private val scrollView: NestedScrollView,
	private val infoTarget: View,
	private val similarTarget: View,
) : BottomSheetBehavior.BottomSheetCallback(), TabLayout.OnTabSelectedListener {

	private val behavior = BottomSheetBehavior.from(sheet)
	private val chaptersTab = tabs.newTab().setText(R.string.chapters)
	private val infoTab = tabs.newTab().setText(R.string.details_tab_info)
	// A removed tab is recycled by TabLayout, so the Similar tab is created anew each time it is shown
	private var similarTab: TabLayout.Tab? = null
	private var isSyncing = false

	init {
		tabs.addTab(chaptersTab, false)
		tabs.addTab(infoTab, true)
		tabs.addOnTabSelectedListener(this)
		behavior.addBottomSheetCallback(this)
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
		isSyncing = true
		if (current == null) {
			val tab = tabs.newTab().setText(R.string.details_tab_similar)
			tabs.addTab(tab, false)
			similarTab = tab
		} else {
			if (current.isSelected) infoTab.select()
			tabs.removeTab(current)
			similarTab = null
		}
		isSyncing = false
	}

	override fun onTabSelected(tab: TabLayout.Tab) {
		if (!isSyncing) onTabClick(tab)
	}

	override fun onTabReselected(tab: TabLayout.Tab) {
		if (!isSyncing) onTabClick(tab)
	}

	override fun onTabUnselected(tab: TabLayout.Tab) = Unit

	override fun onStateChanged(bottomSheet: View, newState: Int) {
		val target = when (newState) {
			BottomSheetBehavior.STATE_EXPANDED -> chaptersTab
			BottomSheetBehavior.STATE_COLLAPSED -> if (chaptersTab.isSelected) infoTab else return
			else -> return
		}
		if (target.isSelected) return
		isSyncing = true
		target.select()
		isSyncing = false
	}

	override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit

	private fun onTabClick(tab: TabLayout.Tab) {
		when (tab) {
			chaptersTab -> behavior.state = BottomSheetBehavior.STATE_EXPANDED
			infoTab -> scrollToView(infoTarget)
			similarTab -> scrollToView(similarTarget)
		}
	}

	private fun scrollToView(target: View) {
		behavior.state = BottomSheetBehavior.STATE_COLLAPSED
		scrollView.smoothScrollTo(0, (target.top - tabs.height).coerceAtLeast(0))
	}
}
