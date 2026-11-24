package hanten.wre.app.upcoming.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.list.CardGroupDecoration
import hanten.wre.app.core.util.ext.consumeAllSystemBarsInsets
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.systemBarsInsets
import hanten.wre.app.databinding.ActivityUpcomingBinding
import hanten.wre.app.databinding.ItemUpcomingBinding
import hanten.wre.app.databinding.ItemUpcomingHeaderBinding
import hanten.wre.app.upcoming.domain.UpcomingItem
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** «Скоро выйдет»: library titles grouped by the day their next chapter is expected */
@AndroidEntryPoint
class UpcomingActivity : BaseActivity<ActivityUpcomingBinding>() {

	private val viewModel by viewModels<UpcomingViewModel>()
	private val adapter = UpcomingAdapter { router.openDetails(it.manga) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityUpcomingBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		with(viewBinding.recyclerView) {
			adapter = this@UpcomingActivity.adapter
			addItemDecoration(CardGroupDecoration(context))
		}
		viewBinding.buttonCheck.setOnClickListener { viewModel.checkNow() }
		combine(viewModel.items, viewModel.isChecking, ::Pair).observe(this) { (items, isChecking) ->
			adapter.submit(items?.let(::group).orEmpty())
			val isEmpty = items != null && items.isEmpty()
			viewBinding.layoutEmpty.isVisible = isEmpty
			viewBinding.buttonCheck.isEnabled = !isChecking
			viewBinding.buttonCheck.setText(if (isChecking) R.string.upcoming_checking else R.string.upcoming_check_now)
		}
		viewModel.isLoading.observe(this) { viewBinding.progressBar.isVisible = it }
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.systemBarsInsets
		viewBinding.recyclerView.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
		viewBinding.appbar.updatePadding(left = bars.left, right = bars.right, top = bars.top)
		viewBinding.buttonCheck.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			bottomMargin = bars.bottom + resources.getDimensionPixelOffset(R.dimen.margin_normal) * 2
		}
		return insets.consumeAllSystemBarsInsets()
	}

	/** Any moment (date passed), Today, Tomorrow, the next weekdays, Later, Late */
	private fun group(items: List<UpcomingItem>): List<Any> {
		val zone = ZoneId.systemDefault()
		val today = LocalDate.now(zone)
		val result = ArrayList<Any>(items.size + 8)
		result += getString(R.string.upcoming_hint)
		var lastHeader: String? = null
		for (item in items) {
			val day = Instant.ofEpochMilli(item.forecast.nextDate).atZone(zone).toLocalDate()
			val header = when {
				item.forecast.isLate -> getString(R.string.upcoming_late)
				day.isBefore(today) -> getString(R.string.upcoming_any_moment)
				day == today -> getString(R.string.today)
				day == today.plusDays(1) -> getString(R.string.upcoming_tomorrow)
				day.isBefore(today.plusDays(7)) -> day.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
					.replaceFirstChar { it.titlecase() }
				else -> getString(R.string.upcoming_later)
			}
			if (header != lastHeader) {
				result += Header(header)
				lastHeader = header
			}
			result += item
		}
		return result
	}

	private class Header(val title: String)

	private class UpcomingAdapter(
		private val onClick: (UpcomingItem) -> Unit,
	) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

		private var items: List<Any> = emptyList()

		@Suppress("NotifyDataSetChanged")
		fun submit(list: List<Any>) {
			items = list
			notifyDataSetChanged()
		}

		override fun getItemCount() = items.size

		override fun getItemViewType(position: Int) = when (items[position]) {
			is UpcomingItem -> TYPE_ITEM
			is Header -> TYPE_HEADER
			else -> TYPE_HINT
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val inflater = LayoutInflater.from(parent.context)
			return when (viewType) {
				TYPE_ITEM -> ItemHolder(ItemUpcomingBinding.inflate(inflater, parent, false))
				else -> HeaderHolder(ItemUpcomingHeaderBinding.inflate(inflater, parent, false))
			}
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (val item = items[position]) {
				is UpcomingItem -> (holder as ItemHolder).bind(item)
				is Header -> (holder as HeaderHolder).bind(item.title, isHint = false)
				else -> (holder as HeaderHolder).bind(item.toString(), isHint = true)
			}
		}

		private inner class ItemHolder(val binding: ItemUpcomingBinding) : RecyclerView.ViewHolder(binding.root) {

			fun bind(item: UpcomingItem) = binding.bindUpcoming(item, onClick)
		}

		private class HeaderHolder(val binding: ItemUpcomingHeaderBinding) : RecyclerView.ViewHolder(binding.root) {

			fun bind(text: String, isHint: Boolean) {
				binding.textViewTitle.text = text
				binding.textViewHint.text = text
				binding.textViewTitle.isVisible = !isHint
				binding.textViewHint.isVisible = isHint
			}
		}

		private companion object {
			const val TYPE_ITEM = 0
			const val TYPE_HEADER = 1
			const val TYPE_HINT = 2
		}
	}
}
