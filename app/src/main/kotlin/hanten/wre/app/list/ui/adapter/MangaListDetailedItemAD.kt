package hanten.wre.app.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.R
import hanten.wre.app.core.model.iconResId
import hanten.wre.app.core.ui.list.AdapterDelegateClickListenerAdapter
import hanten.wre.app.core.util.ext.textAndVisible
import hanten.wre.app.databinding.ItemMangaListDetailsBinding
import hanten.wre.app.list.ui.ListModelDiffCallback
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.MangaDetailedListModel
import hanten.wre.app.parsers.model.MangaState

fun mangaListDetailedItemAD(
	clickListener: MangaDetailsClickListener,
) = adapterDelegateViewBinding<MangaDetailedListModel, ListModel, ItemMangaListDetailsBinding>(
	{ inflater, parent -> ItemMangaListDetailsBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener)
		.attach(itemView)

	bind { payloads ->
		binding.textViewTitle.text = item.title
		binding.textViewAuthor.textAndVisible = item.manga.authors.joinToString(", ")
		binding.progressView.setProgress(
			value = item.progress,
			animate = ListModelDiffCallback.PAYLOAD_PROGRESS_CHANGED in payloads,
		)
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
		binding.textViewTags.text = item.tags.joinToString(separator = ", ") { it.title ?: "" }
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
