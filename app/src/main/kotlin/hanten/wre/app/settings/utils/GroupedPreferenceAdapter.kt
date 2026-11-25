package hanten.wre.app.settings.utils

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import hanten.wre.app.core.util.ext.getThemeColor
import com.google.android.material.R as materialR

/**
 * Hanten 3.0: settings rows drawn as rounded cards, one card per run of rows between
 * category headers (same look as the settings list on the More tab).
 * Rows without an icon lose the reserved icon space so text lines up inside the card.
 */
@SuppressLint("RestrictedApi")
class GroupedPreferenceAdapter(
	group: PreferenceGroup,
	private val isCard: (Preference) -> Boolean,
) : PreferenceGroupAdapter(group) {

	override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
		super.onBindViewHolder(holder, position)
		val pref = getItem(position) ?: return
		val view = holder.itemView
		val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
		if (!isCard(pref)) {
			// own layouts (headers, toggle card) keep their margins; they never share a view type with rows
			return
		}
		holder.isDividerAllowedAbove = false
		holder.isDividerAllowedBelow = false
		val density = view.resources.displayMetrics.density
		val isFirst = !isCardAt(position - 1)
		val isLast = !isCardAt(position + 1)
		lp?.setMargins(
			(16 * density).toInt(),
			if (isFirst) (4 * density).toInt() else 0,
			(16 * density).toInt(),
			if (isLast) (8 * density).toInt() else 0,
		)
		view.layoutParams = lp
		// info hints (no title, not clickable) drop the "i" icon too, so their text lines up with the rows
		val isHint = !pref.isSelectable && pref.title.isNullOrEmpty()
		// recycled rows: restore the icon space when this row has an icon (it was hidden for a previous row)
		val iconVisibility = if (pref.icon == null || isHint) View.GONE else View.VISIBLE
		holder.findViewById(android.R.id.icon_frame)?.visibility = iconVisibility
		holder.findViewById(androidx.preference.R.id.icon_frame)?.visibility = iconVisibility
		holder.findViewById(android.R.id.icon)?.visibility = iconVisibility
		val radius = 20 * density
		val top = if (isFirst) radius else 0f
		val bottom = if (isLast) radius else 0f
		val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
		val context = view.context
		val colors = themeColors ?: ThemeColors(
			surface = context.getThemeColor(materialR.attr.colorSurfaceContainerLow),
			highlight = context.getThemeColor(android.R.attr.colorControlHighlight),
		).also { themeColors = it }
		val shape = GradientDrawable().apply {
			cornerRadii = radii
			setColor(colors.surface)
		}
		view.background = if (pref.isSelectable) {
			val mask = GradientDrawable().apply {
				cornerRadii = radii
				setColor(0xFFFFFFFF.toInt())
			}
			RippleDrawable(
				ColorStateList.valueOf(colors.highlight),
				shape,
				mask,
			)
		} else {
			shape
		}
	}

	/** Theme colors read once per adapter instead of on every bind (fewer styled-attribute lookups while scrolling). */
	private class ThemeColors(val surface: Int, val highlight: Int)

	private var themeColors: ThemeColors? = null

	private fun isCardAt(position: Int): Boolean {
		if (position < 0 || position >= itemCount) return false
		return getItem(position)?.let(isCard) == true
	}
}
