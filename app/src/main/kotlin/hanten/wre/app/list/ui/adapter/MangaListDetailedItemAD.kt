package hanten.wre.app.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.R
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.core.model.titleResId
import hanten.wre.app.core.ui.list.AdapterDelegateClickListenerAdapter
import hanten.wre.app.core.util.ext.textAndVisible
import hanten.wre.app.databinding.ItemMangaListDetailsBinding
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.MangaDetailedListModel

fun mangaListDetailedItemAD(
	clickListener: MangaDetailsClickListener,
) = adapterDelegateViewBinding<MangaDetailedListModel, ListModel, ItemMangaListDetailsBinding>(
	{ inflater, parent -> ItemMangaListDetailsBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener)
		.attach(itemView)

	bind {
		binding.textViewTitle.text = item.title
		// Hanten 3.0: "Type · Source · Status" line like in the mockup
		binding.textViewAuthor.textAndVisible = buildString {
			append(context.getString(item.contentTypeResId))
			append(" · ")
			append(item.manga.source.getTitle(context))
			item.manga.state?.let {
				append(" · ")
				append(context.getString(it.titleResId))
			}
		}
		binding.textViewTags.textAndVisible = item.tags.joinToString(separator = ", ") { it.title ?: "" }
		val progress = item.progress
		binding.progressBar.bindReadingProgress(progress)
		binding.textViewProgress.textAndVisible = if (progress != null && progress.isShownOnCard()) {
			progress.getCardText(context)
		} else {
			null
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
	}
}
