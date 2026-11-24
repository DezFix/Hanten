package hanten.wre.app.core.ui.list

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import hanten.wre.app.R
import hanten.wre.app.core.util.ext.getThemeColor
import com.google.android.material.R as materialR

/**
 * Hanten 3.0: consecutive rows whose root has id [R.id.card_row] share one rounded card
 * with side margins (same look as the source list on the Catalog tab and the settings cards).
 */
class CardGroupDecoration(context: Context) : RecyclerView.ItemDecoration() {

	private val density = context.resources.displayMetrics.density
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getThemeColor(materialR.attr.colorSurfaceContainerLow)
	}
	private val radius = 20f * density
	private val inset = (16f * density).toInt()
	private val padding = 6f * density
	private val rect = RectF()

	override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
		if (!view.isCardRow()) return
		outRect.left = inset
		outRect.right = inset
		val position = parent.getChildAdapterPosition(view)
		val adapter = parent.adapter ?: return
		if (position == RecyclerView.NO_POSITION) return
		val prev = position - 1
		val next = position + 1
		val prevIsCard = prev >= 0 && isCardType(parent, adapter.getItemViewType(prev), adapter.getItemViewType(position))
		val nextIsCard = next < adapter.itemCount &&
			isCardType(parent, adapter.getItemViewType(next), adapter.getItemViewType(position))
		if (!prevIsCard) outRect.top = (padding * 2).toInt()
		if (!nextIsCard) outRect.bottom = (padding * 2).toInt()
	}

	override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
		var top = Float.NaN
		var bottom = 0f
		var left = 0f
		var right = 0f
		for (i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			if (child.isCardRow()) {
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

	/** Rows of one adapter share a view type, so a neighbour of the same type is a card row too */
	private fun isCardType(parent: RecyclerView, type: Int, cardType: Int): Boolean = type == cardType

	private fun View.isCardRow() = id == R.id.card_row

	private fun drawGroup(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
		rect.set(left, top - padding, right, bottom + padding)
		c.drawRoundRect(rect, radius, radius, paint)
	}
}
