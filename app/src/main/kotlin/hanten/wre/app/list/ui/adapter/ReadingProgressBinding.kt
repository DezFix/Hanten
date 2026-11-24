package hanten.wre.app.list.ui.adapter

import android.content.Context
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import hanten.wre.app.R
import hanten.wre.app.core.prefs.ProgressIndicatorMode
import hanten.wre.app.list.domain.ReadingProgress

/**
 * Hanten 3.0: every manga card shows reading progress the same way — a thin bar,
 * plus a "Read 72%" line where the card has room for text.
 */
fun ReadingProgress?.isShownOnCard(): Boolean =
	this != null && mode != ProgressIndicatorMode.NONE && percent > 0f

fun LinearProgressIndicator.bindReadingProgress(progress: ReadingProgress?) {
	isVisible = progress.isShownOnCard()
	if (progress != null && isVisible) {
		setProgressCompat((progress.percent.coerceIn(0f, 1f) * 1000f).toInt(), false)
	}
}

fun ReadingProgress.getCardText(context: Context): String = when (mode) {
	ProgressIndicatorMode.CHAPTERS_READ -> context.getString(R.string.card_chapters_read, chapters, totalChapters)
	ProgressIndicatorMode.CHAPTERS_LEFT -> context.getString(R.string.card_chapters_left, chaptersLeft)
	ProgressIndicatorMode.PERCENT_LEFT -> context.getString(R.string.card_percent_left, cardPercent(percentLeft))

	else -> context.getString(R.string.card_percent_read, cardPercent(percent))
}

/** Short form for the cover pill: "72%", "12/40" in chapter modes, "✓" when finished. */
fun ReadingProgress.getShortText(): String = when {
	isCompleted() -> "\u2713"
	mode == ProgressIndicatorMode.CHAPTERS_READ -> "$chapters/$totalChapters"
	mode == ProgressIndicatorMode.CHAPTERS_LEFT -> "-$chaptersLeft"
	mode == ProgressIndicatorMode.PERCENT_LEFT -> "-" + cardPercent(percentLeft) + "%"
	else -> cardPercent(percent) + "%"
}

/** Like [ReadingProgress.percentToString] (also used in the title card), but a started title never reads as "0%" and an unfinished one never as "100%". */
fun cardPercent(value: Float): String {
	if (ReadingProgress.isCompleted(value)) return "100"
	val whole = (value.coerceIn(0f, 1f) * 100f).toInt()
	return when {
		value > 0f && whole == 0 -> "1"
		whole >= 100 -> "99"
		else -> whole.toString()
	}
}
