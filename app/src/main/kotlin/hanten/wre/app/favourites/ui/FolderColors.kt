package hanten.wre.app.favourites.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import hanten.wre.app.R
import hanten.wre.app.core.util.ext.getThemeColor
import com.google.android.material.R as materialR

/** Hanten 3.0: colors a library folder can have; 0 = no color (the folder follows the theme accent) */
object FolderColors {

	val palette = intArrayOf(
		0,
		0xFFE5484D.toInt(), // red
		0xFFF76B15.toInt(), // orange
		0xFFFFC53D.toInt(), // yellow
		0xFF46A758.toInt(), // green
		0xFF12A594.toInt(), // teal
		0xFF0090FF.toInt(), // blue
		0xFF8E4EC6.toInt(), // purple
		0xFFD6409F.toInt(), // pink
		0xFF8D8D86.toInt(), // gray
	)
}

/**
 * A row of round color swatches (folder editor and the quick "New folder" dialog).
 * The first swatch is "no color": an empty outlined circle.
 */
class FolderColorPicker(
	private val row: LinearLayout,
	private val onPick: (Int) -> Unit = {},
) {

	var color: Int = 0
		set(value) {
			field = value
			refresh()
		}

	init {
		val context = row.context
		val density = context.resources.displayMetrics.density
		val size = (40 * density).toInt()
		val gap = (10 * density).toInt()
		row.removeAllViews()
		FolderColors.palette.forEach { swatch ->
			val view = View(context)
			view.contentDescription = context.getString(R.string.folder_color)
			view.setOnClickListener {
				color = swatch
				onPick(swatch)
			}
			row.addView(view, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
		}
		refresh()
	}

	private fun refresh() {
		val context = row.context
		val density = context.resources.displayMetrics.density
		val ring = context.getThemeColor(materialR.attr.colorOnSurface)
		val outline = context.getThemeColor(materialR.attr.colorOutline)
		FolderColors.palette.forEachIndexed { index, swatch ->
			val view = row.getChildAt(index) ?: return
			val isSelected = swatch == color
			view.isSelected = isSelected
			view.background = GradientDrawable().apply {
				shape = GradientDrawable.OVAL
				setColor(if (swatch == 0) Color.TRANSPARENT else swatch)
				setStroke(((if (isSelected) 3f else 1f) * density).toInt(), if (isSelected) ring else outline)
			}
		}
	}
}
