package hanten.wre.app.today.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import hanten.wre.app.databinding.ItemTodayCoverBinding
import hanten.wre.app.parsers.model.Manga

/** A cover in one of the horizontal rows; [badge] is the number of new chapters, 0 hides it. */
data class TodayCover(val manga: Manga, val badge: Int = 0)

class TodayCoversAdapter(
	private val onClick: (Manga) -> Unit,
) : ListAdapter<TodayCover, TodayCoversAdapter.Holder>(Diff) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
		val binding = ItemTodayCoverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return Holder(binding)
	}

	override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

	inner class Holder(private val binding: ItemTodayCoverBinding) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: TodayCover) {
			binding.root.setOnClickListener { onClick(item.manga) }
			binding.textTitle.text = item.manga.title
			binding.textBadge.isVisible = item.badge > 0
			binding.textBadge.text = "+${item.badge}"
			binding.imageCover.setImageAsync(item.manga.coverUrl, item.manga)
		}
	}

	private object Diff : DiffUtil.ItemCallback<TodayCover>() {

		override fun areItemsTheSame(oldItem: TodayCover, newItem: TodayCover) = oldItem.manga.id == newItem.manga.id

		override fun areContentsTheSame(oldItem: TodayCover, newItem: TodayCover) = oldItem == newItem
	}
}
