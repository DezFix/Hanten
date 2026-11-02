package hanten.wre.app.settings.utils

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import hanten.wre.app.R

class HelpSwitchPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = androidx.preference.R.attr.switchPreferenceCompatStyle,
	defStyleRes: Int = 0,
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes) {
	var helpDescription: CharSequence? = null
	var onHelpClick: (() -> Unit)? = null

	init {
		widgetLayoutResource = R.layout.preference_help_switch_widget
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.findViewById(R.id.preference_help)?.run {
			contentDescription = helpDescription
			setOnClickListener { onHelpClick?.invoke() }
			// Help remains available even when the feature's dependent switch is disabled.
			isEnabled = true
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
		}
	}
}
