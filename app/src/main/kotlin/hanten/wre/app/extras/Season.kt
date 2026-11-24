package hanten.wre.app.extras

import android.app.Activity
import androidx.annotation.StringRes
import hanten.wre.app.R
import java.util.Calendar

/** Hanten 3.0: seasonal greetings on Today (once a day a small effect plays, the line stays all period). */
enum class Season(@StringRes val greetingResId: Int) {

	HANAMI(R.string.season_hanami),
	TANABATA(R.string.season_tanabata),
	HALLOWEEN(R.string.season_halloween),
	NEW_YEAR(R.string.season_new_year),
	;

	fun play(activity: Activity) = when (this) {
		HANAMI -> Celebrations.sakuraRain(activity, seconds = 3)
		TANABATA -> Celebrations.stars(activity)
		HALLOWEEN -> Celebrations.halloween(activity)
		NEW_YEAR -> Celebrations.snow(activity)
	}

	companion object {

		fun current(calendar: Calendar = Calendar.getInstance()): Season? {
			val month = calendar.get(Calendar.MONTH)
			val day = calendar.get(Calendar.DAY_OF_MONTH)
			return when {
				month == Calendar.MARCH && day >= 25 || month == Calendar.APRIL && day <= 15 -> HANAMI
				month == Calendar.JULY && day in 6..8 -> TANABATA
				month == Calendar.OCTOBER && day >= 29 || month == Calendar.NOVEMBER && day == 1 -> HALLOWEEN
				month == Calendar.DECEMBER && day >= 25 || month == Calendar.JANUARY && day <= 7 -> NEW_YEAR
				else -> null
			}
		}

		fun dayKey(calendar: Calendar = Calendar.getInstance()): Int =
			calendar.get(Calendar.YEAR) * 10000 + (calendar.get(Calendar.MONTH) + 1) * 100 +
				calendar.get(Calendar.DAY_OF_MONTH)
	}
}
