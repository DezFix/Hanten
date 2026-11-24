package hanten.wre.app.today.ui

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.text.inSpans
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.model.contentType
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.core.model.titleResId
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.ui.BaseFragment
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.prefs.ProgressIndicatorMode
import hanten.wre.app.list.domain.ReadingProgress
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.list.ui.adapter.getShortText
import hanten.wre.app.list.ui.adapter.mangaGridItemAD
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.MangaListModel
import hanten.wre.app.list.ui.size.StaticItemSizeResolver
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.databinding.FragmentTodayBinding
import hanten.wre.app.history.domain.model.MangaWithHistory
import hanten.wre.app.databinding.ItemUpcomingBinding
import hanten.wre.app.upcoming.domain.UpcomingItem
import hanten.wre.app.upcoming.ui.bindUpcoming
import hanten.wre.app.core.prefs.AnimationLevel
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.LocalState
import hanten.wre.app.extras.Celebrations
import hanten.wre.app.extras.Season
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Home tab (Hanten 3.0 mockup): continue reading, new chapters from the library ("All" opens the updates list),
 * ranobe in progress and recently read. The header gets a round "check for new chapters" button.
 */

@AndroidEntryPoint
class TodayFragment : BaseFragment<FragmentTodayBinding>(), MenuProvider {

	private val viewModel by viewModels<TodayViewModel>()

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var localState: LocalState

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
		FragmentTodayBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentTodayBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val coverWidth = resources.getDimensionPixelSize(R.dimen.today_cover_width)
		val listener = object : OnListItemClickListener<MangaListModel> {
			override fun onItemClick(item: MangaListModel, view: View) {
				router.openDetails(item.toMangaWithOverride(), itemView = view)
			}
		}
		val updatesAdapter = BaseListAdapter<ListModel>()
			.addDelegate(ListItemType.MANGA_GRID, mangaGridItemAD(StaticItemSizeResolver(coverWidth), listener))
		val recentAdapter = BaseListAdapter<ListModel>()
			.addDelegate(ListItemType.MANGA_GRID, mangaGridItemAD(StaticItemSizeResolver(coverWidth), listener))
		binding.recyclerUpdates.adapter = updatesAdapter
		binding.recyclerRecent.adapter = recentAdapter
		binding.buttonUpdatesAll.setOnClickListener { router.openFeed() }
		binding.buttonRecentAll.setOnClickListener { router.openHistory() }
		binding.buttonUpcomingAll.setOnClickListener { router.openUpcoming() }
		// Pull down = look for new chapters in the library (same as the round button in the header)
		binding.swipeRefresh.isEnabled = viewModel.isTrackerEnabled
		binding.swipeRefresh.setOnRefreshListener {
			if (AnimationLevel.effective(requireContext(), settings) == AnimationLevel.FULL) {
				activity?.let(Celebrations::petalPuff)
			}
			viewModel.checkNewChapters()
		}
		bindKofiBanner(binding)
		bindSeason(binding)
		viewModel.isCheckingUpdates.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }
		if (viewModel.isTrackerEnabled) {
			requireActivity().addMenuProvider(this, viewLifecycleOwner)
		}

		viewModel.history.observe(viewLifecycleOwner) { list -> onHistoryChanged(list) }
		viewModel.upcoming.observe(viewLifecycleOwner) { list -> onUpcomingChanged(list) }
		viewModel.recent.observe(viewLifecycleOwner) { list ->
			recentAdapter.emit(list)
			binding.layoutRecentHeader.isVisible = list.isNotEmpty()
			binding.recyclerRecent.isVisible = list.isNotEmpty()
		}
		viewModel.updates.observe(viewLifecycleOwner) { list ->
			updatesAdapter.emit(list)
			binding.recyclerUpdates.isVisible = list.isNotEmpty()
			binding.textUpdatesTitle.text = sectionTitle(getString(R.string.new_chapters), list.size)
			updateEmptyState()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_today, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_check_updates -> {
			viewModel.checkNewChapters()
			viewBinding?.root?.let {
				Snackbar.make(it, R.string.check_new_chapters, Snackbar.LENGTH_SHORT).show()
			}
			true
		}

		else -> false
	}

	private fun onHistoryChanged(list: List<MangaWithHistory>?) {
		val binding = viewBinding ?: return
		val first = list?.firstOrNull()
		binding.cardContinue.isVisible = first != null
		if (first != null) {
			val manga = first.manga
			binding.textContinueTitle.text = manga.title
			binding.textContinueSubtitle.text = progressText(first)
			val percent = first.history.percent
			binding.progressContinue.isVisible = percent >= 0f
			binding.progressContinue.setProgressCompat((percent.coerceIn(0f, 1f) * 100).roundToInt(), false)
			binding.imageContinueCover.setImageAsync(manga.coverUrl, manga)
			binding.cardContinue.setOnClickListener { router.openDetails(manga) }
			binding.buttonContinue.setOnClickListener { router.openReader(manga, it) }
		}
		updateEmptyState()
	}

	private fun progressText(item: MangaWithHistory): String {
		val type = getString(item.manga.contentType.titleResId)
		val source = type + " · " + item.manga.source.getTitle(requireContext())
		val percent = item.history.percent
		// same rounding as the cover pills, so "Continue" and the library never disagree (1% vs 0%)
		return if (percent >= 0f) {
			"$source · " + ReadingProgress(percent, 0, ProgressIndicatorMode.PERCENT_READ).getShortText()
		} else {
			source
		}
	}

	private fun sectionTitle(title: String, count: Int): CharSequence {
		if (count <= 0) return title
		val accent = requireContext().getThemeColor(androidx.appcompat.R.attr.colorPrimary)
		return SpannableStringBuilder(title).append(' ').inSpans(ForegroundColorSpan(accent)) {
			append("· ").append(count.toString())
		}
	}

	private fun onUpcomingChanged(list: List<UpcomingItem>) {
		val binding = viewBinding ?: return
		binding.layoutUpcomingHeader.isVisible = list.isNotEmpty()
		binding.cardUpcoming.isVisible = list.isNotEmpty()
		val rows = binding.layoutUpcomingRows
		rows.removeAllViews()
		for (item in list) {
			val row = ItemUpcomingBinding.inflate(layoutInflater, rows, false)
			row.bindUpcoming(item) { router.openDetails(it.manga) }
			rows.addView(row.root)
		}
	}

	/**
	 * Ko-fi banner: 14 days after the first launch, never in incognito; «Не сейчас» hides it for 60 days,
	 * the cross and «Поддержать» hide it for good. Dates live only on this device ([LocalState]).
	 */
	private fun bindKofiBanner(binding: FragmentTodayBinding) {
		val now = System.currentTimeMillis()
		val showFrom = maxOf(localState.firstLaunchAt + TimeUnit.DAYS.toMillis(KOFI_FIRST_DAYS), localState.kofiNextShowAt)
		binding.cardKofi.isVisible = !localState.isKofiDismissedForever &&
			!settings.isIncognitoModeEnabled &&
			now >= showFrom
		binding.buttonKofiSupport.setOnClickListener {
			localState.isKofiDismissedForever = true
			binding.cardKofi.isVisible = false
			router.openExternalBrowser(getString(R.string.url_donate), getString(R.string.donate_title))
		}
		binding.buttonKofiLater.setOnClickListener {
			localState.kofiNextShowAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(KOFI_LATER_DAYS)
			binding.cardKofi.isVisible = false
		}
		binding.buttonKofiClose.setOnClickListener {
			localState.isKofiDismissedForever = true
			binding.cardKofi.isVisible = false
		}
	}

	private fun bindSeason(binding: FragmentTodayBinding) {
		val season = Season.current().takeIf { settings.isEasterEggsEnabled }
		binding.textSeason.isVisible = season != null
		if (season == null) return
		binding.textSeason.setText(season.greetingResId)
		val today = Season.dayKey()
		if (localState.seasonalEffectDay != today &&
			AnimationLevel.effective(requireContext(), settings) != AnimationLevel.OFF
		) {
			localState.seasonalEffectDay = today
			binding.root.postDelayed({ activity?.let(season::play) }, 600)
		}
		binding.textSeason.setOnClickListener { activity?.let(season::play) }
	}

	private fun updateEmptyState() {
		val binding = viewBinding ?: return
		val history = viewModel.history.value ?: return
		val updates = viewModel.updates.value
		val isEmpty = history.isEmpty() && updates.isEmpty()
		binding.layoutEmpty.isVisible = isEmpty
		// The New chapters block stays on the home screen whenever new chapters are checked
		binding.layoutUpdatesHeader.isVisible = viewModel.isTrackerEnabled && !isEmpty
		binding.textUpdatesEmpty.isVisible = viewModel.isTrackerEnabled && !isEmpty && updates.isEmpty()
	}

	private companion object {

		const val KOFI_FIRST_DAYS = 14L
		const val KOFI_LATER_DAYS = 60L
	}
}
