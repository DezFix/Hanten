package hanten.wre.app.details.ui.related

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import hanten.wre.app.R
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.databinding.ItemReadingOrderBinding
import hanten.wre.app.details.domain.ReadingOrderItem
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

class ReadingOrderAdapter(
	private val onClick: (ReadingOrderItem) -> Unit,
	private val onSearch: (ReadingOrderItem) -> Unit,
) : ListAdapter<ReadingOrderItem, ReadingOrderAdapter.ViewHolder>(DiffCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemReadingOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	inner class ViewHolder(
		private val binding: ItemReadingOrderBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ReadingOrderItem) {
			val context = binding.root.context
			val sourceTitle = item.manga?.source?.getTitle(context) ?: item.sourceName
			binding.textOrder.text = item.order.toString()
			binding.textTitle.text = item.title.ifBlank { context.getString(R.string.unknown) }
			binding.textSubtitle.text = buildList {
				if (item.isCurrent) {
					add(context.getString(R.string.reading_order_current))
				} else {
					item.type?.let { add(context.getString(it.titleResId)) }
					sourceTitle?.takeIf { it.isNotBlank() }?.let(::add)
					if (item.manga == null) {
						add(context.getString(R.string.reading_order_not_in_library))
					}
				}
			}.joinToString(separator = " · ")

			val containerColor = MaterialColors.getColor(
				binding.root,
				if (item.isCurrent) materialR.attr.colorPrimaryContainer else materialR.attr.colorSurfaceVariant,
			)
			val textColor = MaterialColors.getColor(
				binding.root,
				if (item.isCurrent) materialR.attr.colorOnPrimaryContainer else materialR.attr.colorOnSurfaceVariant,
			)
			binding.cardOrder.setCardBackgroundColor(containerColor)
			binding.textOrder.setTextColor(textColor)
			binding.textTitle.setTextColor(
				MaterialColors.getColor(
					binding.root,
					if (item.isCurrent) appcompatR.attr.colorPrimary else materialR.attr.colorOnSurface,
				),
			)

			binding.root.setOnClickListener { onClick(item) }
			binding.buttonSearch.setOnClickListener { onSearch(item) }
		}
	}

	private object DiffCallback : DiffUtil.ItemCallback<ReadingOrderItem>() {
		override fun areItemsTheSame(oldItem: ReadingOrderItem, newItem: ReadingOrderItem): Boolean {
			return oldItem.targetId == newItem.targetId
		}

		override fun areContentsTheSame(oldItem: ReadingOrderItem, newItem: ReadingOrderItem): Boolean {
			return oldItem == newItem
		}
	}
}
