package hanten.wre.app.core.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hanten 3.0: device-only state (a separate prefs file, so backups never carry it to another phone):
 * Ko-fi banner dates and the easter eggs' "already shown" marks.
 */
@Singleton
class LocalState @Inject constructor(@ApplicationContext context: Context) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	/** First time Hanten 3.0 was opened on this device (set on the first read). */
	val firstLaunchAt: Long
		get() {
			val saved = prefs.getLong(KEY_FIRST_LAUNCH, 0L)
			if (saved != 0L) return saved
			val now = System.currentTimeMillis()
			prefs.edit { putLong(KEY_FIRST_LAUNCH, now) }
			return now
		}

	var kofiNextShowAt: Long
		get() = prefs.getLong(KEY_KOFI_NEXT, 0L)
		set(value) = prefs.edit { putLong(KEY_KOFI_NEXT, value) }

	var isKofiDismissedForever: Boolean
		get() = prefs.getBoolean(KEY_KOFI_NEVER, false)
		set(value) = prefs.edit { putBoolean(KEY_KOFI_NEVER, value) }

	/** Day (yyyyMMdd) the 3 a.m. "one more chapter?" reminder was last shown. */
	var nightReminderDay: Int
		get() = prefs.getInt(KEY_NIGHT_DAY, 0)
		set(value) = prefs.edit { putInt(KEY_NIGHT_DAY, value) }

	/** Day (yyyyMMdd) the seasonal greeting effect last ran on Today. */
	var seasonalEffectDay: Int
		get() = prefs.getInt(KEY_SEASON_DAY, 0)
		set(value) = prefs.edit { putInt(KEY_SEASON_DAY, value) }

	/** Chapters finished in the reader on this device (for the 1000th chapter fireworks). */
	var chaptersFinished: Int
		get() = prefs.getInt(KEY_CHAPTERS, 0)
		set(value) = prefs.edit { putInt(KEY_CHAPTERS, value) }

	/** The error reports question (onboarding step or the Today card) was answered on this device. */
	var isErrorReportsAsked: Boolean
		get() = prefs.getBoolean(KEY_REPORTS_ASKED, false)
		set(value) = prefs.edit { putBoolean(KEY_REPORTS_ASKED, value) }

	/** Pattern index behind the icon on About (tap the icon to change it). */
	var aboutPattern: Int
		get() = prefs.getInt(KEY_ABOUT_PATTERN, 0)
		set(value) = prefs.edit { putInt(KEY_ABOUT_PATTERN, value) }

	private companion object {

		const val PREFS_NAME = "hanten_local"
		const val KEY_FIRST_LAUNCH = "first_launch_at"
		const val KEY_KOFI_NEXT = "kofi_next_show_at"
		const val KEY_KOFI_NEVER = "kofi_never"
		const val KEY_NIGHT_DAY = "night_reminder_day"
		const val KEY_SEASON_DAY = "seasonal_effect_day"
		const val KEY_CHAPTERS = "chapters_finished"
		const val KEY_ABOUT_PATTERN = "about_pattern"
		const val KEY_REPORTS_ASKED = "error_reports_asked"
	}
}
