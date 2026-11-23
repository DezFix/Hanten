package hanten.wre.app.settings.utils

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.DynamicColors
import hanten.wre.app.R
import hanten.wre.app.core.prefs.ColorScheme
import hanten.wre.app.core.prefs.ImportedTheme
import hanten.wre.app.core.prefs.ImportedThemeStore
import hanten.wre.app.databinding.ItemColorSchemeBinding
import hanten.wre.app.databinding.PreferenceThemeBinding

class ThemeChooserPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = R.attr.themeChooserPreferenceStyle,
	defStyleRes: Int = R.style.Preference_ThemeChooser,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

	private val store = ImportedThemeStore(context.applicationContext)
	private var currentValue: ColorScheme = ColorScheme.default
	private val itemClickListener = View.OnClickListener {
		val item = it.tag as? ThemeItem ?: return@OnClickListener
		selectItem(item)
	}

	var value: String
		get() = currentValue.name
		set(value) = setValueInternal(value, notifyChanged = true)

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		holder.isDividerAllowedAbove = false
		holder.isDividerAllowedBelow = false
		holder.itemView.isClickable = false
		val binding = PreferenceThemeBinding.bind(holder.itemView)
		binding.grid.removeAllViews()
		val strokeWidth = context.resources.getDimensionPixelSize(R.dimen.theme_tile_stroke)
		val tileMargin = context.resources.getDimensionPixelSize(R.dimen.theme_tile_margin)
		for (item in buildItems()) {
			val importedTheme = item.importedTheme
			val styleResId = when {
				importedTheme == null -> item.scheme.styleResId
				importedTheme.isDark -> R.style.ThemeOverlay_Hanten_Custom_Dark
				else -> R.style.ThemeOverlay_Hanten_Custom_Light
			}
			val themedContext = ContextThemeWrapper(context, styleResId)
			val itemBinding = ItemColorSchemeBinding.inflate(
				LayoutInflater.from(themedContext),
				binding.grid,
				false,
			)
			val selected = isSelected(item)
			itemBinding.card.isChecked = selected
			itemBinding.card.strokeWidth = if (selected) strokeWidth else 0
			itemBinding.textViewTitle.text =
				importedTheme?.displayName ?: themedContext.getString(item.scheme.titleResId)
			if (importedTheme != null) {
				itemBinding.shapePrimary.backgroundTintList =
					ColorStateList.valueOf(importedTheme.primaryColor)
				val onPrimary = importedTheme.onPrimaryColor ?: autoOnColor(importedTheme.primaryColor)
				itemBinding.shape1.backgroundTintList = ColorStateList.valueOf(onPrimary)
				itemBinding.shape2.backgroundTintList = ColorStateList.valueOf(onPrimary)
			}
			itemBinding.card.tag = item
			itemBinding.card.contentDescription = itemBinding.textViewTitle.text
			itemBinding.imageViewCheck.isVisible = selected
			itemBinding.card.setOnClickListener(itemClickListener)
			// Hanten 3.0: equal columns (mockup grid of theme tiles)
			itemBinding.root.layoutParams = GridLayout.LayoutParams(
				GridLayout.spec(GridLayout.UNDEFINED),
				GridLayout.spec(GridLayout.UNDEFINED, 1f),
			).apply {
				width = 0
				setMargins(tileMargin, tileMargin, tileMargin, tileMargin)
			}
			binding.grid.addView(itemBinding.root)
		}
	}

	override fun onSetInitialValue(defaultValue: Any?) {
		value = getPersistedString(
			when (defaultValue) {
				is String -> ColorScheme.safeValueOf(defaultValue) ?: ColorScheme.default
				is ColorScheme -> defaultValue
				else -> ColorScheme.default
			}.name,
		)
	}

	override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
		return a.getInt(index, 0)
	}

	/** Externally refresh this preference's view when custom-theme data has changed. */
	fun refresh() = notifyChanged()

	private fun selectItem(item: ThemeItem) {
		val importedTheme = item.importedTheme
		if (importedTheme != null) {
			store.selectedThemeId = importedTheme.id
			setValueInternal(ColorScheme.CUSTOM.name, notifyChanged = false)
			notifyChanged()
		} else {
			setValueInternal(item.scheme.name, notifyChanged = true)
		}
	}

	private fun buildItems(): List<ThemeItem> {
		val result = ArrayList<ThemeItem>()
		ColorScheme.getAvailableList().mapTo(result) { ThemeItem(it, null) }
		if (DynamicColors.isDynamicColorAvailable()) {
			store.themes.mapTo(result) { ThemeItem(ColorScheme.CUSTOM, it) }
		}
		return result
	}

	private fun isSelected(item: ThemeItem): Boolean {
		val importedTheme = item.importedTheme
		return if (importedTheme != null) {
			currentValue == ColorScheme.CUSTOM && store.selectedThemeId == importedTheme.id
		} else {
			currentValue == item.scheme
		}
	}

	private fun autoOnColor(background: Int): Int {
		return if (ColorUtils.calculateLuminance(background) > 0.5) Color.BLACK else Color.WHITE
	}

	private fun setValueInternal(enumName: String, notifyChanged: Boolean) {
		val newValue = ColorScheme.safeValueOf(enumName) ?: return
		if (newValue != currentValue) {
			currentValue = newValue
			persistString(newValue.name)
			if (notifyChanged) {
				notifyChanged()
			}
		}
	}

	private data class ThemeItem(
		val scheme: ColorScheme,
		val importedTheme: ImportedTheme?,
	)
}
