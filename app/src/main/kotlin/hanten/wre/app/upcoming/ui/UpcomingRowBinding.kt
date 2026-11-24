package hanten.wre.app.upcoming.ui

import android.text.format.DateUtils
import hanten.wre.app.R
import hanten.wre.app.databinding.ItemUpcomingBinding
import hanten.wre.app.upcoming.domain.UpcomingForecast.DAY_MS
import hanten.wre.app.upcoming.domain.UpcomingItem

/** One «Скоро выйдет» row: used on its screen and in the block on Today */
fun ItemUpcomingBinding.bindUpcoming(item: UpcomingItem, onClick: (UpcomingItem) -> Unit) {
	val context = root.context
	val f = item.forecast
	root.setOnClickListener { onClick(item) }
	imageViewCover.setImageAsync(item.manga.coverUrl, item.manga)
	textViewTitle.text = item.manga.title
	val every = context.resources.getQuantityString(R.plurals.upcoming_every_days, f.intervalDays, f.intervalDays)
	textViewSummary.text = f.nextNumber?.let {
		context.getString(R.string.upcoming_summary_chapter, it, every)
	} ?: every
	textViewDate.text = if (f.isLate) {
		val days = ((System.currentTimeMillis() - f.nextDate) / DAY_MS).toInt().coerceAtLeast(1)
		context.resources.getQuantityString(R.plurals.upcoming_late_days, days, days)
	} else {
		val date = DateUtils.formatDateTime(
			context,
			f.nextDate,
			DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
		)
		// the expected day has passed, but not by a whole gap yet: "since 19 Nov"
		if (DateUtils.isToday(f.nextDate) || f.nextDate > System.currentTimeMillis()) {
			date
		} else {
			context.getString(R.string.upcoming_since, date)
		}
	}
}
