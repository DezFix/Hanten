package hanten.wre.app.today.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.ui.BaseFragment
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.databinding.FragmentTodayBinding
import hanten.wre.app.history.domain.model.MangaWithHistory
import kotlin.math.roundToInt

/** Home tab: continue reading, new chapters from the library, recently read. */
@AndroidEntryPoint
class TodayFragment : BaseFragment<FragmentTodayBinding>() {

	private val viewModel by viewModels<TodayViewModel>()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
		FragmentTodayBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentTodayBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val openDetails: (hanten.wre.app.parsers.model.Manga) -> Unit = { router.openDetails(it) }
		val updatesAdapter = TodayCoversAdapter(openDetails)
		val recentAdapter = TodayCoversAdapter(openDetails)
		binding.recyclerUpdates.adapter = updatesAdapter
		binding.recyclerRecent.adapter = recentAdapter

		viewModel.history.observe(viewLifecycleOwner) { list -> onHistoryChanged(list, recentAdapter) }
		viewModel.updates.observe(viewLifecycleOwner) { list ->
			updatesAdapter.submitList(list)
			binding.textUpdatesTitle.isVisible = list.isNotEmpty()
			binding.recyclerUpdates.isVisible = list.isNotEmpty()
			updateEmptyState()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	private fun onHistoryChanged(list: List<MangaWithHistory>?, recentAdapter: TodayCoversAdapter) {
		val binding = viewBinding ?: return
		val first = list?.firstOrNull()
		binding.cardContinue.isVisible = first != null
		if (first != null) {
			val manga = first.manga
			binding.textContinueTitle.text = manga.title
			val percent = first.history.percent
			val source = manga.source.getTitle(binding.root.context)
			binding.textContinueSubtitle.text = if (percent >= 0f) {
				"$source · ${(percent * 100).roundToInt()}%"
			} else {
				source
			}
			binding.progressContinue.isVisible = percent >= 0f
			binding.progressContinue.setProgressCompat((percent.coerceIn(0f, 1f) * 100).roundToInt(), false)
			binding.imageContinueCover.setImageAsync(manga.coverUrl, manga)
			binding.cardContinue.setOnClickListener { router.openDetails(manga) }
			binding.buttonContinue.setOnClickListener { router.openReader(manga, it) }
		}
		val recent = list.orEmpty().drop(1).map { TodayCover(it.manga) }
		recentAdapter.submitList(recent)
		binding.textRecentTitle.isVisible = recent.isNotEmpty()
		binding.recyclerRecent.isVisible = recent.isNotEmpty()
		updateEmptyState()
	}

	private fun updateEmptyState() {
		val binding = viewBinding ?: return
		val history = viewModel.history.value ?: return
		binding.layoutEmpty.isVisible = history.isEmpty() && viewModel.updates.value.isEmpty()
	}
}
