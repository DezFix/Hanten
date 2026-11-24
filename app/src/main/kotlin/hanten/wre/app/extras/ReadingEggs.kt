package hanten.wre.app.extras

import android.app.Activity
import hanten.wre.app.core.prefs.AnimationLevel
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.LocalState
import java.util.Calendar

/** Hanten 3.0 reader easter eggs: the night reminder and the 1000th chapter fireworks. */
object ReadingEggs {

	private const val MILESTONE = 1000

	/** True once per night when reading between 2:00 and 4:59 («Ещё одну главу — и спать?»). */
	fun shouldShowNightReminder(settings: AppSettings, localState: LocalState): Boolean {
		if (!settings.isEasterEggsEnabled) return false
		val now = Calendar.getInstance()
		if (now.get(Calendar.HOUR_OF_DAY) !in 2..4) return false
		val day = Season.dayKey(now)
		if (localState.nightReminderDay == day) return false
		localState.nightReminderDay = day
		return true
	}

	/**
	 * Call when the reader moves on to the next chapter. Returns true exactly when the 1000th chapter
	 * on this device is finished; plays the fireworks in that case.
	 */
	fun onChapterFinished(activity: Activity, settings: AppSettings, localState: LocalState): Boolean {
		val count = localState.chaptersFinished + 1
		localState.chaptersFinished = count
		if (count != MILESTONE || !settings.isEasterEggsEnabled) return false
		if (AnimationLevel.effective(activity, settings) != AnimationLevel.OFF) {
			Celebrations.fireworks(activity)
		}
		return true
	}
}
