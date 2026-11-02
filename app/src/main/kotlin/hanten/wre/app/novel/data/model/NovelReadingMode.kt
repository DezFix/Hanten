package hanten.wre.app.novel.data.model

import androidx.annotation.StringRes
import hanten.wre.app.R

enum class NovelReadingMode(
	@StringRes val titleRes: Int,
) {
	CONTINUOUS_SCROLL(R.string.novel_scroll_continuous),
	PAGED(R.string.novel_scroll_paged),
}