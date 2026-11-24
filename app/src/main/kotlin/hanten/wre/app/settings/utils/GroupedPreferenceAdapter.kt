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
		if (pref.icon == null) {
			holder.findViewById(android.R.id.icon_frame)?.visibility = View.GONE
			holder.findViewById(android.R.id.icon)?.visibility = View.GONE
		}
		val radius = 20 * density
		val top = if (isFirst) radius else 0f
		val bottom = if (isLast) radius else 0f
		val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
		val context = view.context
		val shape = GradientDrawable().apply {
			cornerRadii = radii
			setColor(context.getThemeColor(materialR.attr.colorSurfaceContainerLow))
		}
		view.background = if (pref.isSelectable) {
			val mask = GradientDrawable().apply {
				cornerRadii = radii
				setColor(0xFFFFFFFF.toInt())
			}
			RippleDrawable(
				ColorStateList.valueOf(context.getThemeColor(android.R.attr.colorControlHighlight)),
				shape,
				mask,
			)
		} else {
			shape
		}
	}

	private fun isCardAt(position: Int): Boolean {
		if (position < 0 || position >= itemCount) return false
		return getItem(position)?.let(isCard) == true
	}
}
