package hanten.wre.app.stats.ui

import android.content.res.ColorStateList
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.R
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.util.FutonColors
import hanten.wre.app.databinding.ItemStatsBinding
import hanten.wre.app.stats.domain.StatsRecord
import hanten.wre.app.parsers.model.Manga

fun statsAD(
	listener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<StatsRecord, StatsRecord, ItemStatsBinding>(
	{ layoutInflater, parent -> ItemStatsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		item.manga?.let { listener.onItemClick(it, v) }
	}

	bind {
		binding.textViewTitle.text = item.manga?.title ?: item.tagName ?: getString(R.string.other_manga)
		binding.textViewSummary.text = item.time.format(context.resources)
		binding.imageViewBadge.imageTintList = ColorStateList.valueOf(FutonColors.ofManga(context, item.manga))
		binding.root.isClickable = item.manga != null
	}
}
