package hanten.wre.app.explore.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.list.ui.adapter.ListItemType
import com.google.android.material.R as materialR

/** Hanten 3.0 Catalog: consecutive source rows (list mode) share one rounded card, like the mockup. */
class SourcesGroupDecoration(context: Context) : RecyclerView.ItemDecoration() {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getThemeColor(materialR.attr.colorSurfaceContainer)
	}
	private val radius = context.resources.displayMetrics.density * 20f
	private val padding = context.resources.displayMetrics.density * 4f
	private val rect = RectF()

	override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
		var top = Float.NaN
		var bottom = 0f
		var left = 0f
		var right = 0f
		for (i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			val holder = parent.getChildViewHolder(child) ?: continue
			if (holder.itemViewType == ListItemType.EXPLORE_SOURCE_LIST.ordinal) {
				if (top.isNaN()) {
					top = child.top + child.translationY
					left = child.left.toFloat()
					right = child.right.toFloat()
				}
				bottom = child.bottom + child.translationY
			} else if (!top.isNaN()) {
				drawGroup(c, left, top, right, bottom)
				top = Float.NaN
			}
		}
		if (!top.isNaN()) {
			drawGroup(c, left, top, right, bottom)
		}
	}

	private fun drawGroup(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
		rect.set(left, top - padding, right, bottom + padding)
		c.drawRoundRect(rect, radius, radius, paint)
	}
}
