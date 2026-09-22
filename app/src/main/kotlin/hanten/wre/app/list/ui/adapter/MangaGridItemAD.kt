package hanten.wre.app.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.R
import hanten.wre.app.core.model.iconResId
import hanten.wre.app.core.ui.list.AdapterDelegateClickListenerAdapter
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.util.ext.setTooltipCompat
import hanten.wre.app.databinding.ItemMangaGridBinding
import hanten.wre.app.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.MangaGridModel
import hanten.wre.app.list.ui.model.MangaListModel
import hanten.wre.app.list.ui.size.ItemSizeResolver
import hanten.wre.app.parsers.model.MangaState

fun mangaGridItemAD(
	sizeResolver: ItemSizeResolver,
	clickListener: OnListItemClickListener<MangaListModel>,
) = adapterDelegateViewBinding<MangaGridModel, ListModel, ItemMangaGridBinding>(
	{ inflater, parent -> ItemMangaGridBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	sizeResolver.attachToView(itemView, binding.textViewTitle, binding.progressView)

	bind { payloads ->
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.progressView.setProgress(item.progress, PAYLOAD_PROGRESS_CHANGED in payloads)
		with(binding.iconsView) {
			clearIcons()
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			val mangaState = item.manga.state
			when (mangaState) {
				MangaState.FINISHED, MangaState.ABANDONED ->
					addIcon(mangaState.iconResId)

				else -> if (item.isStale) addIcon(R.drawable.ic_updated)
			}
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
