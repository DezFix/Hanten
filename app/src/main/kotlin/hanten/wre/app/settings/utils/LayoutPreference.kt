package hanten.wre.app.settings.utils

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * Hanten 3.0: a non-selectable preference that shows a custom layout (android:layout) and reports
 * clicks on its buttons, so screens like Backup and About can have big buttons and cards per mockup.
 */
class LayoutPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : Preference(context, attrs) {

	/** Ids of views inside the layout that should report clicks to [onViewClick]. */
	var clickableIds: IntArray = IntArray(0)
	var onViewClick: ((View) -> Unit)? = null
	var onBind: ((View) -> Unit)? = null

	init {
		isSelectable = false
		isPersistent = false
	}

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		holder.isDividerAllowedBelow = false
		for (id in clickableIds) {
			holder.findViewById(id)?.setOnClickListener { v -> onViewClick?.invoke(v) }
		}
		onBind?.invoke(holder.itemView)
	}

	fun refresh() = notifyChanged()
}
