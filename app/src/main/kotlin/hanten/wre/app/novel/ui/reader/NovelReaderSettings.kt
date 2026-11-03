package hanten.wre.app.novel.ui.reader

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import hanten.wre.app.core.util.ext.getEnumValue
import hanten.wre.app.core.util.ext.putEnumValue
import hanten.wre.app.novel.data.model.NovelReadingMode
import hanten.wre.app.novel.data.model.NovelTextAlign
import hanten.wre.app.novel.data.model.NovelTheme
import kotlin.math.roundToInt

data class NovelReaderSettings(
	val fontSizeSp: Float = 18f,
	val theme: NovelTheme = NovelTheme.DARK,
	val customBackgroundColor: Int = Color.parseColor("#141414"),
	val customTextColor: Int = Color.parseColor("#E0E0E0"),
	val fontFamily: String = "serif",
	val lineSpacingMultiplier: Float = 1.6f,
	val paragraphMarginEm: Float = 0f,
	val textIndentEm: Float = 1.25f,
	val hyphens: Boolean = true,
	val pageMarginDp: Int = 18,
	val textAlignment: NovelTextAlign = NovelTextAlign.JUSTIFY,
	val readingMode: NovelReadingMode = NovelReadingMode.CONTINUOUS_SCROLL,
	val fullscreen: Boolean = false,
	val autoScrollSpeed: Float = 0f,
) {
	val effectiveBackgroundColor: Int
		get() = if (theme == NovelTheme.CUSTOM) customBackgroundColor else theme.backgroundColor

	val effectiveTextColor: Int
		get() = if (theme == NovelTheme.CUSTOM) customTextColor else theme.textColor

	fun toCssVariables(
		safeTopPx: Int = 0,
		safeBottomPx: Int = 0,
		density: Float = 1f,
	): String {
		val bgHex = String.format("#%06X", 0xFFFFFF and effectiveBackgroundColor)
		val textHex = String.format("#%06X", 0xFFFFFF and effectiveTextColor)
		val pageMarginPx = (pageMarginDp * density).roundToInt().coerceAtLeast(0)
		return """
			:root {
				--reader-bg: $bgHex;
				--reader-text: $textHex;
				--reader-font-size: ${fontSizeSp.toInt()}px;
				--reader-font-family: $fontFamily, serif;
				--reader-line-height: $lineSpacingMultiplier;
				--reader-paragraph-margin: ${paragraphMarginEm}em;
				--reader-indent: ${textIndentEm}em;
				--reader-hyphens: ${if (hyphens) "auto" else "manual"};
				--reader-page-margin: ${pageMarginPx}px;
				--reader-safe-top: ${safeTopPx.coerceAtLeast(0)}px;
				--reader-safe-bottom: ${safeBottomPx.coerceAtLeast(0)}px;
				--reader-text-align: ${textAlignment.cssValue};
			}
		""".trimIndent()
	}

	companion object {
		private const val KEY_FONT_SIZE = "novel_font_size"
		private const val KEY_THEME = "novel_theme"
		private const val KEY_BG_COLOR = "novel_bg_color"
		private const val KEY_TEXT_COLOR = "novel_text_color"
		private const val KEY_FONT_FAMILY = "novel_font_family"
		private const val KEY_LINE_SPACING = "novel_line_spacing"
		private const val KEY_PARA_MARGIN = "novel_para_margin"
		private const val KEY_TEXT_INDENT = "novel_text_indent"
		private const val KEY_HYPHENS = "novel_hyphens"
		private const val KEY_PAGE_MARGIN = "novel_page_margin"
		private const val KEY_TEXT_ALIGN = "novel_text_align"
		private const val KEY_READING_MODE = "novel_reading_mode"
		private const val KEY_FULLSCREEN = "novel_fullscreen"
		private const val KEY_AUTO_SCROLL_SPEED = "novel_auto_scroll_speed"

		fun load(context: Context): NovelReaderSettings {
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			return NovelReaderSettings(
				fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 18f),
				theme = prefs.getEnumValue(KEY_THEME, NovelTheme.DARK),
				customBackgroundColor = prefs.getInt(KEY_BG_COLOR, Color.parseColor("#141414")),
				customTextColor = prefs.getInt(KEY_TEXT_COLOR, Color.parseColor("#E0E0E0")),
				fontFamily = prefs.getString(KEY_FONT_FAMILY, "serif") ?: "serif",
				lineSpacingMultiplier = prefs.getFloat(KEY_LINE_SPACING, 1.6f),
				paragraphMarginEm = prefs.getFloat(KEY_PARA_MARGIN, 0f),
				textIndentEm = prefs.getFloat(KEY_TEXT_INDENT, 1.25f),
				hyphens = prefs.getBoolean(KEY_HYPHENS, true),
				pageMarginDp = prefs.getInt(KEY_PAGE_MARGIN, 18),
				textAlignment = prefs.getEnumValue(KEY_TEXT_ALIGN, NovelTextAlign.JUSTIFY),
				readingMode = prefs.getEnumValue(KEY_READING_MODE, NovelReadingMode.CONTINUOUS_SCROLL),
				fullscreen = prefs.getBoolean(KEY_FULLSCREEN, false),
				autoScrollSpeed = prefs.getFloat(KEY_AUTO_SCROLL_SPEED, 0f),
			)
		}

		fun save(context: Context, settings: NovelReaderSettings) {
			val prefs = PreferenceManager.getDefaultSharedPreferences(context)
			prefs.edit {
				putFloat(KEY_FONT_SIZE, settings.fontSizeSp)
				putEnumValue(KEY_THEME, settings.theme)
				putInt(KEY_BG_COLOR, settings.customBackgroundColor)
				putInt(KEY_TEXT_COLOR, settings.customTextColor)
				putString(KEY_FONT_FAMILY, settings.fontFamily)
				putFloat(KEY_LINE_SPACING, settings.lineSpacingMultiplier)
				putFloat(KEY_PARA_MARGIN, settings.paragraphMarginEm)
				putFloat(KEY_TEXT_INDENT, settings.textIndentEm)
				putBoolean(KEY_HYPHENS, settings.hyphens)
				putInt(KEY_PAGE_MARGIN, settings.pageMarginDp)
				putEnumValue(KEY_TEXT_ALIGN, settings.textAlignment)
				putEnumValue(KEY_READING_MODE, settings.readingMode)
				putBoolean(KEY_FULLSCREEN, settings.fullscreen)
				putFloat(KEY_AUTO_SCROLL_SPEED, settings.autoScrollSpeed)
			}
		}
	}
}
