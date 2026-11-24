package hanten.wre.app.favourites.ui.categories.edit

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.util.DefaultTextWatcher
import hanten.wre.app.core.util.ext.consumeAllSystemBarsInsets
import hanten.wre.app.core.util.ext.getDisplayMessage
import hanten.wre.app.core.util.ext.getSerializableCompat
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.core.util.ext.setChecked
import hanten.wre.app.core.util.ext.sortedByOrdinal
import hanten.wre.app.core.util.ext.systemBarsInsets
import hanten.wre.app.databinding.ActivityCategoryEditBinding
import hanten.wre.app.favourites.ui.FolderColors
import hanten.wre.app.list.domain.ListSortOrder
import com.google.android.material.R as materialR

@AndroidEntryPoint
class FavouritesCategoryEditActivity :
	BaseActivity<ActivityCategoryEditBinding>(),
	AdapterView.OnItemClickListener,
	View.OnClickListener,
	DefaultTextWatcher {

	private val viewModel by viewModels<FavouritesCategoryEditViewModel>()
	private var selectedSortOrder: ListSortOrder? = null
	private var selectedColor: Int? = null
	private val sortOrders = ListSortOrder.FAVORITES.sortedByOrdinal()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityCategoryEditBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		initSortSpinner()
		initColors()
		viewBinding.buttonDone.setOnClickListener(this)
		viewBinding.editName.addTextChangedListener(this)
		afterTextChanged(viewBinding.editName.text)

		viewModel.onSaved.observeEvent(this) { finishAfterTransition() }
		viewModel.category.observe(this, ::onCategoryChanged)
		viewModel.isLoading.observe(this, ::onLoadingStateChanged)
		viewModel.onError.observeEvent(this, ::onError)
		viewModel.isTrackerEnabled.observe(this) {
			viewBinding.switchTracker.isVisible = it
		}
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.root.setPadding(
			barsInsets.left,
			barsInsets.top,
			barsInsets.right,
			barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putSerializable(KEY_SORT_ORDER, selectedSortOrder)
		selectedColor?.let { outState.putInt(KEY_COLOR, it) }
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		savedInstanceState.getSerializableCompat<ListSortOrder>(KEY_SORT_ORDER)?.let {
			selectedSortOrder = it
		}
		if (savedInstanceState.containsKey(KEY_COLOR)) {
			selectedColor = savedInstanceState.getInt(KEY_COLOR)
			updateColors()
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_done -> viewModel.save(
				title = viewBinding.editName.text?.toString()?.trim().orEmpty(),
				sortOrder = getSelectedSortOrder(),
				isTrackerEnabled = viewBinding.switchTracker.isChecked,
				isVisibleOnShelf = viewBinding.switchShelf.isChecked,
				color = selectedColor ?: 0,
			)
		}
	}

	override fun afterTextChanged(s: Editable?) {
		viewBinding.buttonDone.isEnabled = !s.isNullOrBlank() && !viewModel.isLoading.value
	}

	override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
		selectedSortOrder = sortOrders.getOrNull(position)
	}

	private fun onCategoryChanged(category: FavouriteCategory?) {
		setTitle(if (category == null) R.string.create_category else R.string.edit_category)
		// the first emission is null (still loading): take the color only from a real folder
		if (selectedColor == null && category != null) {
			selectedColor = category.color
			updateColors()
		}
		if (selectedSortOrder != null) {
			return
		}
		viewBinding.editName.setText(category?.title)
		selectedSortOrder = category?.order
		val sortText = getString((category?.order ?: ListSortOrder.NEWEST).titleResId)
		viewBinding.editSort.setText(sortText, false)
		viewBinding.switchTracker.setChecked(category?.isTrackingEnabled != false, false)
		viewBinding.switchShelf.setChecked(category?.isVisibleInLibrary != false, false)
	}

	private fun onError(e: Throwable) {
		viewBinding.textViewError.text = e.getDisplayMessage(resources)
		viewBinding.textViewError.isVisible = true
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		viewBinding.buttonDone.isEnabled = !isLoading && !viewBinding.editName.text.isNullOrBlank()
		viewBinding.editSort.isEnabled = !isLoading
		viewBinding.editName.isEnabled = !isLoading
		viewBinding.switchTracker.isEnabled = !isLoading
		viewBinding.switchShelf.isEnabled = !isLoading
		if (isLoading) {
			viewBinding.textViewError.isVisible = false
		}
	}

	/** Color swatches: the first one is "no color" (an empty outlined circle) */
	private fun initColors() {
		val density = resources.displayMetrics.density
		val size = (40 * density).toInt()
		val gap = (10 * density).toInt()
		FolderColors.palette.forEach { color ->
			val view = View(this)
			view.contentDescription = getString(R.string.folder_color)
			view.setOnClickListener {
				selectedColor = color
				updateColors()
			}
			viewBinding.rowColors.addView(view, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
		}
		updateColors()
	}

	private fun updateColors() {
		val density = resources.displayMetrics.density
		val ring = getThemeColor(materialR.attr.colorOnSurface)
		val outline = getThemeColor(materialR.attr.colorOutline)
		val current = selectedColor ?: 0
		FolderColors.palette.forEachIndexed { index, color ->
			val view = viewBinding.rowColors.getChildAt(index) ?: return
			val isSelected = color == current
			view.isSelected = isSelected
			view.background = GradientDrawable().apply {
				shape = GradientDrawable.OVAL
				if (color == 0) {
					setColor(Color.TRANSPARENT)
				} else {
					setColor(color)
				}
				setStroke(((if (isSelected) 3f else 1f) * density).toInt(), if (isSelected) ring else outline)
			}
		}
	}

	private fun initSortSpinner() {
		val entries = sortOrders.map { getString(it.titleResId) }
		val adapter = SortAdapter(this, entries)
		viewBinding.editSort.setAdapter(adapter)
		viewBinding.editSort.onItemClickListener = this
	}

	private fun getSelectedSortOrder(): ListSortOrder {
		selectedSortOrder?.let { return it }
		val entries = sortOrders.map { getString(it.titleResId) }
		val index = entries.indexOf(viewBinding.editSort.text.toString())
		return sortOrders.getOrNull(index) ?: ListSortOrder.NEWEST
	}

	private class SortAdapter(
		context: Context,
		entries: List<String>,
	) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, entries) {

		override fun getFilter(): Filter = EmptyFilter

		private object EmptyFilter : Filter() {
			override fun performFiltering(constraint: CharSequence?) = FilterResults()
			override fun publishResults(constraint: CharSequence?, results: FilterResults?) = Unit
		}
	}

	companion object {

		const val NO_ID = -1L
		private const val KEY_SORT_ORDER = "sort"
		private const val KEY_COLOR = "color"
	}
}
