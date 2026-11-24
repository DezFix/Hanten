package hanten.wre.app.core.prefs

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.google.android.material.color.DynamicColors
import hanten.wre.app.R
import hanten.wre.app.parsers.util.find

@Keep
enum class ColorScheme(
	@StyleRes val styleResId: Int,
	@StringRes val titleResId: Int,
) {

	HANTEN(R.style.ThemeOverlay_Hanten_Hanten, R.string.theme_name_hanten),
	DEFAULT(R.style.ThemeOverlay_Hanten_Totoro, R.string.theme_name_totoro),
	MONET(R.style.ThemeOverlay_Hanten_Monet, R.string.theme_name_dynamic),
	EXPRESSIVE(R.style.ThemeOverlay_Hanten_Expressive, R.string.theme_name_expressive),
	MIKU(R.style.ThemeOverlay_Hanten_Miku, R.string.theme_name_miku),
	RENA(R.style.ThemeOverlay_Hanten_Asuka, R.string.theme_name_asuka),
	FROG(R.style.ThemeOverlay_Hanten_Mion, R.string.theme_name_mion),
	BLUEBERRY(R.style.ThemeOverlay_Hanten_Rikka, R.string.theme_name_rikka),
	SAKURA(R.style.ThemeOverlay_Hanten_Sakura, R.string.theme_name_sakura),
	MAMIMI(R.style.ThemeOverlay_Hanten_Mamimi, R.string.theme_name_mamimi),
	KANADE(R.style.ThemeOverlay_Hanten_Kanade, R.string.theme_name_kanade),
	ITSUKA(R.style.ThemeOverlay_Hanten_Itsuka, R.string.theme_name_itsuka),

	/** Easter egg: listed only after the volume-key code (AppSettings.isRetroThemeUnlocked). */
	RETRO(R.style.ThemeOverlay_Hanten_Retro, R.string.theme_name_retro),
	CUSTOM(R.style.ThemeOverlay_Hanten_Custom_Light, R.string.theme_name_custom),
	;

	companion object {

		/**
		 * The app's own scheme (icon colors) is the default for new installs on every device;
		 * wallpaper colors (Monet) stay one tap away in Appearance. Existing users keep their saved choice.
		 */
		val default: ColorScheme
			get() = HANTEN

		fun getAvailableList(): List<ColorScheme> {
			val list = ColorScheme.entries.toMutableList()
			list.remove(CUSTOM)
			list.remove(RETRO)
			if (!DynamicColors.isDynamicColorAvailable()) {
				list.remove(MONET)
				list.remove(EXPRESSIVE)
			}
			return list
		}

		fun safeValueOf(name: String): ColorScheme? {
			return ColorScheme.entries.find(name)
		}
	}
}
