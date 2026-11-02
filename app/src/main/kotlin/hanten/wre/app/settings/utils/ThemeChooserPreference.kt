package hanten.wre.app.settings.utils

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.customview.view.AbsSavedState
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.DynamicColors
import hanten.wre.app.R
import hanten.wre.app.core.prefs.ColorScheme
import hanten.wre.app.core.prefs.ImportedTheme
import hanten.wre.app.core.prefs.ImportedThemeStore
import hanten.wre.app.databinding.ItemColorSchemeBinding
import hanten.wre.app.databinding.PreferenceThemeBinding
import java.lang.ref.WeakReference
import com.google.android.material.R as materialR

class ThemeChooserPreference @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = R.attr.themeChooserPreferenceStyle,
	defStyleRes: Int = R.style.Preference_ThemeChooser,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

	private val store = ImportedThemeStore(context.applicationContext)
	private var currentValue: ColorScheme = ColorScheme.default
	private val lastScrollPosition = intArrayOf(-1)
	private val itemClickListener = View.OnClickListener {
		val item = it.tag as? ThemeItem ?: return@OnClickListener
		selectItem(item)
	}
	private var scrollPersistListener: ScrollPersistListener? = null

	var value: String
		get() = currentValue.name
		set(value) = setValueInternal(value, notifyChanged = true)

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		val binding = PreferenceThemeBinding.bind(holder.itemView)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			binding.scrollView.suppressLayout(true)
			binding.linear.suppressLayout(true)
		}
		binding.linear.removeAllViews()
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
				binding.linear,
				false,
			)
			if (binding.linear.isEmpty()) {
				itemBinding.root.updatePaddingRelative(start = 0)
			}
			val selected = isSelected(item)
			itemBinding.card.isChecked = selected
			itemBinding.card.strokeWidth = if (selected) themedContext.resources.getDimensionPixelSize(
				materialR.dimen.m3_comp_outlined_card_outline_width,
			) else 0
			itemBinding.textViewTitle.text =
				importedTheme?.displayName ?: themedContext.getString(item.scheme.titleResId)
			if (importedTheme != null) {
				itemBinding.shapePrimary.backgroundTintList =
					ColorStateList.valueOf(importedTheme.primaryColor)
				val onPrimary = importedTheme.onPrimaryColor ?: autoOnColor(importedTheme.primaryColor)
				itemBinding.shape1.backgroundTintList = ColorStateList.valueOf(onPrimary)
				itemBinding.shape2.backgroundTintList = ColorStateList.valueOf(onPrimary)
			}
			itemBinding.root.tag = item
			itemBinding.card.tag = item
			itemBinding.imageViewCheck.isVisible = selected
			itemBinding.root.setOnClickListener(itemClickListener)
			itemBinding.card.setOnClickListener(itemClickListener)
			binding.linear.addView(itemBinding.root)
			if (selected) {
				itemBinding.root.requestFocus()
			}
		}
		if (lastScrollPosition[0] >= 0) {
			val scroller = Scroller(binding.scrollView, lastScrollPosition[0])
			scroller.run()
			binding.scrollView.post(scroller)
		}
		binding.scrollView.viewTreeObserver.run {
			scrollPersistListener?.let { removeOnScrollChangedListener(it) }
			scrollPersistListener =
				ScrollPersistListener(WeakReference(binding.scrollView), lastScrollPosition)
			addOnScrollChangedListener(scrollPersistListener)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			binding.linear.suppressLayout(false)
			binding.scrollView.suppressLayout(false)
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

	override fun onSaveInstanceState(): Parcelable? {
		val superState = super.onSaveInstanceState() ?: return null
		return SavedState(
			superState = superState,
			scrollPosition = lastScrollPosition[0],
		)
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)
		lastScrollPosition[0] = state.scrollPosition
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

	private class SavedState : AbsSavedState {

		val scrollPosition: Int

		constructor(
			superState: Parcelable,
			scrollPosition: Int,
		) : super(superState) {
			this.scrollPosition = scrollPosition
		}

		constructor(source: Parcel, classLoader: ClassLoader?) : super(source, classLoader) {
			scrollPosition = source.readInt()
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeInt(scrollPosition)
		}

		companion object {
			@Suppress("unused")
			@JvmField
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
				override fun createFromParcel(`in`: Parcel) =
					SavedState(`in`, SavedState::class.java.classLoader)

				override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
			}
		}
	}

	private class ScrollPersistListener(
		private val scrollViewRef: WeakReference<HorizontalScrollView>,
		private val lastScrollPosition: IntArray,
	) : ViewTreeObserver.OnScrollChangedListener {

		override fun onScrollChanged() {
			val scrollView = scrollViewRef.get() ?: return
			lastScrollPosition[0] = scrollView.scrollX
		}
	}

	private class Scroller(
		private val scrollView: HorizontalScrollView,
		private val position: Int,
	) : Runnable {

		override fun run() {
			scrollView.scrollTo(position, 0)
		}
	}
}
