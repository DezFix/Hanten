package hanten.wre.app.settings.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.MaterialColors
import hanten.wre.app.R
import hanten.wre.app.core.prefs.ColorScheme

/**
 * Hanten 3.0: accent color as a row of round swatches (mockup "Акцент").
 * The first swatch keeps the color scheme's own accent.
 */
class AccentColorPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {

	var colorScheme: ColorScheme = ColorScheme.default

	init {
		layoutResource = R.layout.preference_accent
		isSelectable = false
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		holder.isDividerAllowedBelow = false
		val container = holder.findViewById(R.id.layout_colors) as LinearLayout
		container.removeAllViews()
		val entries = entries ?: return
		val values = entryValues ?: return
		val res = context.resources
		val size = res.getDimensionPixelSize(R.dimen.accent_swatch_size)
		val gap = res.getDimensionPixelSize(R.dimen.accent_swatch_gap)
		val stroke = res.getDimensionPixelSize(R.dimen.accent_swatch_stroke)
		val ringColor = MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurface)
		values.forEachIndexed { index, rawValue ->
			val key = rawValue.toString()
			val swatchContext = ContextThemeWrapper(context, overlayFor(key))
			val color = MaterialColors.getColor(swatchContext, androidx.appcompat.R.attr.colorPrimary, ringColor)
			val selected = key == value
			val swatch = ImageView(container.context)
			swatch.background = GradientDrawable().apply {
				shape = GradientDrawable.OVAL
				setColor(color)
				if (selected) setStroke(stroke, ringColor)
			}
			if (key == VALUE_THEME) {
				// "From theme" swatch carries a small mark so it is not mistaken for a fixed color
				swatch.setImageResource(R.drawable.ic_color_theme_auto)
				swatch.imageTintList = ColorStateList.valueOf(
					MaterialColors.getColor(swatchContext, com.google.android.material.R.attr.colorOnPrimary, ringColor),
				)
				swatch.scaleType = ImageView.ScaleType.CENTER
			}
			swatch.contentDescription = entries[index]
			swatch.isSelected = selected
			swatch.setOnClickListener(OnSwatchClick(key))
			container.addView(
				swatch,
				LinearLayout.LayoutParams(size, size).apply { if (index > 0) marginStart = gap },
			)
		}
	}

	override fun onClick() = Unit

	@StyleRes
	private fun overlayFor(key: String): Int = when (key) {
		"red" -> R.style.ThemeOverlay_Hanten_Accent_Red
		"blue" -> R.style.ThemeOverlay_Hanten_Accent_Blue
		"gold" -> R.style.ThemeOverlay_Hanten_Accent_Gold
		"green" -> R.style.ThemeOverlay_Hanten_Accent_Green
		"plum" -> R.style.ThemeOverlay_Hanten_Accent_Plum
		"teal" -> R.style.ThemeOverlay_Hanten_Accent_Teal
		else -> colorScheme.styleResId
	}

	private inner class OnSwatchClick(private val key: String) : View.OnClickListener {
		override fun onClick(v: View?) {
			if (key != value && callChangeListener(key)) {
				value = key
			}
		}
	}

	private companion object {
		const val VALUE_THEME = "theme"
	}
}
