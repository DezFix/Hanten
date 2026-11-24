package hanten.wre.app.settings.utils

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import hanten.wre.app.R
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.NavItem
import hanten.wre.app.main.ui.MainNavigationDelegate

/**
 * Hanten 3.0: bottom menu tabs as toggle chips (mockup "Нижнее меню").
 * The saved order is kept; a new tab goes before "More". Reordering lives in the nav config screen.
 */
class NavTabsPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : Preference(context, attrs) {

	var settings: AppSettings? = null

	init {
		layoutResource = R.layout.preference_nav_tabs
		isSelectable = false
		isPersistent = false
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		holder.isDividerAllowedBelow = false
		val settings = settings ?: return
		val selected = settings.mainNavItems
		val max = MainNavigationDelegate.MAX_ITEM_COUNT
		(holder.findViewById(android.R.id.summary) as TextView).text =
			context.getString(R.string.appearance_nav_hint, selected.size, max)
		val group = holder.findViewById(R.id.chip_group) as ChipGroup
		group.removeAllViews()
		for (item in NavItem.entries) {
			if (!item.isAvailable(settings)) continue
			val isOn = item in selected
			val chip = Chip(group.context)
			chip.setText(item.title)
			chip.isCheckable = true
			chip.isChecked = isOn
			chip.isCheckedIconVisible = false
			// More holds the settings: it can't be switched off
			chip.isEnabled = item != NavItem.MORE && (isOn || selected.size < max)
			chip.setOnClickListener { toggle(settings, item) }
			group.addView(chip)
		}
	}

	fun refresh() = notifyChanged()

	private fun toggle(settings: AppSettings, item: NavItem) {
		val current = settings.mainNavItems.toMutableList()
		if (item == NavItem.MORE) return
		if (item in current) {
			if (current.size > 1) current.remove(item)
		} else if (current.size < MainNavigationDelegate.MAX_ITEM_COUNT) {
			val moreIndex = current.indexOf(NavItem.MORE)
			if (moreIndex >= 0) current.add(moreIndex, item) else current.add(item)
		}
		settings.mainNavItems = current
		notifyChanged()
	}
}
