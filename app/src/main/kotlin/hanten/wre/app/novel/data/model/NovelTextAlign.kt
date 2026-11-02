package hanten.wre.app.novel.data.model

import androidx.annotation.StringRes
import hanten.wre.app.R

enum class NovelTextAlign(
	@StringRes val titleRes: Int,
	val cssValue: String,
) {
	LEFT(R.string.novel_align_left, "left"),
	JUSTIFY(R.string.novel_align_justify, "justify"),
}