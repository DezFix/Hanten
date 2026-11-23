package hanten.wre.app.settings.utils

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import hanten.wre.app.R

/**
 * Hanten 3.0: a ListPreference shown as a row of segmented buttons (mockup "Режим", "Скругление")
 * instead of opening a dialog.
 */
class SegmentedListPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {

	init {
		layoutResource = R.layout.preference_segmented
		isSelectable = false
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		holder.isDividerAllowedBelow = false
		val group = holder.findViewById(R.id.toggle_group) as MaterialButtonToggleGroup
		group.clearOnButtonCheckedListeners()
		group.removeAllViews()
		val entries = entries ?: return
		val values = entryValues ?: return
		val current = findIndexOfValue(value)
		entries.forEachIndexed { index, title ->
			val button = MaterialButton(
				group.context,
				null,
				com.google.android.material.R.attr.materialButtonOutlinedStyle,
			)
			button.id = ViewGroup.generateViewId()
			button.text = title
			button.isAllCaps = false
			button.maxLines = 1
			// default 24dp side padding cuts "Как в системе" on phones
			val pad = (8 * button.resources.displayMetrics.density).toInt()
			button.setPaddingRelative(pad, button.paddingTop, pad, button.paddingBottom)
			button.tag = values[index].toString()
			group.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
			if (index == current) {
				group.check(button.id)
			}
		}
		group.addOnButtonCheckedListener { g, checkedId, isChecked ->
			if (!isChecked) return@addOnButtonCheckedListener
			val newValue = g.findViewById<MaterialButton>(checkedId)?.tag as? String
				?: return@addOnButtonCheckedListener
			if (newValue != value && callChangeListener(newValue)) {
				value = newValue
			}
		}
	}

	override fun onClick() = Unit
}
