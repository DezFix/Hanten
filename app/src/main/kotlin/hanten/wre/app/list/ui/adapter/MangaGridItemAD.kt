package hanten.wre.app.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.R
import hanten.wre.app.core.ui.list.AdapterDelegateClickListenerAdapter
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.util.ext.setTooltipCompat
import hanten.wre.app.core.util.ext.textAndVisible
import hanten.wre.app.databinding.ItemMangaGridBinding
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.MangaGridModel
import hanten.wre.app.list.ui.model.MangaListModel
import hanten.wre.app.list.ui.size.ItemSizeResolver

fun mangaGridItemAD(
	sizeResolver: ItemSizeResolver,
	clickListener: OnListItemClickListener<MangaListModel>,
) = adapterDelegateViewBinding<MangaGridModel, ListModel, ItemMangaGridBinding>(
	{ inflater, parent -> ItemMangaGridBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	sizeResolver.attachToView(itemView, binding.textViewTitle, binding.progressView)

	bind {
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		// Hanten 3.0: the same thin bar on every cover, in every list (mockup); no round indicator
		binding.progressView.isVisible = false
		binding.progressBar.bindReadingProgress(item.progress)
		binding.textViewProgress.textAndVisible = item.progress?.takeIf { it.isShownOnCard() }?.getShortText()
		itemView.contentDescription = item.progress?.takeIf { it.isShownOnCard() }?.let {
			item.title + ", " + it.getCardText(context)
		}
		binding.iconsView.bindCoverBadges(
			manga = item.manga,
			isSaved = item.isSaved,
			isFavorite = item.isFavorite,
			isStale = item.isStale,
			isTracked = item.isTracked,
		)
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
		binding.textViewContentType.text = context.getString(item.contentTypeResId)
	}
}
