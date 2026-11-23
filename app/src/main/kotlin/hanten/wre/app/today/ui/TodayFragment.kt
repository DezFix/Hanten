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
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.core.model.isNovelSource
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.ui.BaseFragment
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.databinding.FragmentTodayBinding
import hanten.wre.app.databinding.ItemTodayRanobeBinding
import hanten.wre.app.history.domain.model.MangaWithHistory
import hanten.wre.app.parsers.model.Manga
import kotlin.math.roundToInt

/**
 * Home tab (Hanten 3.0 mockup): continue reading, new chapters from the library ("All" opens the updates list),
 * ranobe in progress and recently read. The header gets a round "check for new chapters" button.
 */
@AndroidEntryPoint
class TodayFragment : BaseFragment<FragmentTodayBinding>(), MenuProvider {

	private val viewModel by viewModels<TodayViewModel>()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
		FragmentTodayBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentTodayBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val openDetails: (Manga) -> Unit = { router.openDetails(it) }
		val updatesAdapter = TodayCoversAdapter(openDetails)
		val recentAdapter = TodayCoversAdapter(openDetails)
		binding.recyclerUpdates.adapter = updatesAdapter
		binding.recyclerRecent.adapter = recentAdapter
		binding.buttonUpdatesAll.setOnClickListener { router.openFeed() }
		// Pull down = look for new chapters in the library (same as the round button in the header)
		binding.swipeRefresh.isEnabled = viewModel.isTrackerEnabled
		binding.swipeRefresh.setOnRefreshListener { viewModel.checkNewChapters() }
		viewModel.isCheckingUpdates.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }
		if (viewModel.isTrackerEnabled) {
			requireActivity().addMenuProvider(this, viewLifecycleOwner)
		}

		viewModel.history.observe(viewLifecycleOwner) { list -> onHistoryChanged(list, recentAdapter) }
		viewModel.updates.observe(viewLifecycleOwner) { list ->
			updatesAdapter.submitList(list)
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

	private fun onHistoryChanged(list: List<MangaWithHistory>?, recentAdapter: TodayCoversAdapter) {
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
		val others = list.orEmpty().drop(1)
		val (ranobe, comics) = others.partition { it.manga.source.isNovelSource }
		bindRanobe(ranobe.take(RANOBE_LIMIT))
		val recent = comics.map { TodayCover(it.manga) }
		recentAdapter.submitList(recent)
		binding.textRecentTitle.isVisible = recent.isNotEmpty()
		binding.recyclerRecent.isVisible = recent.isNotEmpty()
		updateEmptyState()
	}

	private fun bindRanobe(items: List<MangaWithHistory>) {
		val binding = viewBinding ?: return
		val container = binding.layoutRanobe
		container.removeAllViews()
		binding.textRanobeTitle.isVisible = items.isNotEmpty()
		for (item in items) {
			val row = ItemTodayRanobeBinding.inflate(layoutInflater, container, false)
			val manga = item.manga
			row.textTitle.text = manga.title
			row.textSubtitle.text = progressText(item)
			row.imageCover.setImageAsync(manga.coverUrl, manga)
			row.root.setOnClickListener { router.openDetails(manga) }
			row.buttonRead.setOnClickListener { router.openReader(manga, it) }
			container.addView(row.root)
		}
	}

	private fun progressText(item: MangaWithHistory): String {
		val source = item.manga.source.getTitle(requireContext())
		val percent = item.history.percent
		return if (percent >= 0f) "$source · ${(percent * 100).roundToInt()}%" else source
	}

	private fun sectionTitle(title: String, count: Int): CharSequence {
		if (count <= 0) return title
		val accent = requireContext().getThemeColor(androidx.appcompat.R.attr.colorPrimary)
		return SpannableStringBuilder(title).append(' ').inSpans(ForegroundColorSpan(accent)) {
			append("· ").append(count.toString())
		}
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

		const val RANOBE_LIMIT = 3
	}
}
