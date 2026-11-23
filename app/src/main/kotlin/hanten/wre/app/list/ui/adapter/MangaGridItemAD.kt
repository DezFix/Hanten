package hanten.wre.app.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import hanten.wre.app.core.prefs.ProgressIndicatorMode
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
		// Hanten 3.0: a thin bar on the cover shows the percent; the round badge stays only for chapter counts
		val progress = item.progress
		val percent = progress?.percent ?: 0f
		val isChaptersMode = progress?.mode == ProgressIndicatorMode.CHAPTERS_READ ||
			progress?.mode == ProgressIndicatorMode.CHAPTERS_LEFT
		binding.progressBar.isVisible = progress != null && progress.mode != ProgressIndicatorMode.NONE &&
			percent > 0f && !isChaptersMode
		if (binding.progressBar.isVisible) {
			binding.progressBar.setProgressCompat((percent.coerceIn(0f, 1f) * 1000f).toInt(), false)
		}
		binding.progressView.isVisible = isChaptersMode
		with(binding.iconsView) {
			clearIcons()
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			val mangaState = item.manga.state
			when (mangaState) {
				MangaState.FINISHED, MangaState.ABANDONED, MangaState.PAUSED ->
					addIcon(mangaState.iconResId)

				else -> if (item.isStale) addIcon(R.drawable.ic_updated)
			}
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
		binding.textViewContentType.text = context.getString(item.contentTypeResId)
	}
}
