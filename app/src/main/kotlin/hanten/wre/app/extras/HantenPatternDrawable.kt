package hanten.wre.app.extras

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Hanten 3.0 easter egg: traditional Japanese textile patterns drawn behind the icon on About
 * (tap the icon to change). Drawn in code, so no images or third-party art are involved.
 */
class HantenPatternDrawable(
	private val pattern: Int,
	private val background: Int,
	private val foreground: Int,
) : Drawable() {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val path = Path()
	private val oval = RectF()

	override fun draw(canvas: Canvas) {
		val b = bounds
		val radius = minOf(b.width(), b.height()) / 2f
		val cx = b.exactCenterX()
		val cy = b.exactCenterY()
		canvas.save()
		path.reset()
		path.addCircle(cx, cy, radius, Path.Direction.CW)
		canvas.clipPath(path)
		paint.style = Paint.Style.FILL
		paint.color = background
		canvas.drawCircle(cx, cy, radius, paint)
		paint.color = foreground
		val cell = radius / 3.5f
		when (Math.floorMod(pattern, COUNT)) {
			ICHIMATSU -> drawChecks(canvas, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), cell)
			SEIGAIHA -> drawWaves(canvas, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), cell)
			SAMEKOMON -> drawDots(canvas, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), cell)
			else -> drawStripes(canvas, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), cell)
		}
		canvas.restore()
	}

	/** Ichimatsu: checkerboard. */
	private fun drawChecks(canvas: Canvas, l: Float, t: Float, r: Float, btm: Float, cell: Float) {
		var row = 0
		var y = t
		while (y < btm) {
			var x = l + if (row % 2 == 0) 0f else cell
			while (x < r) {
				canvas.drawRect(x, y, x + cell, y + cell, paint)
				x += cell * 2
			}
			y += cell
			row++
		}
	}

	/** Seigaiha: overlapping wave arcs. */
	private fun drawWaves(canvas: Canvas, l: Float, t: Float, r: Float, btm: Float, cell: Float) {
		paint.style = Paint.Style.STROKE
		paint.strokeWidth = cell / 7f
		val step = cell * 1.6f
		var row = 0
		var y = t
		while (y < btm + step) {
			var x = l + if (row % 2 == 0) 0f else step / 2f
			while (x < r + step) {
				for (k in 1..3) {
					val rr = step / 2f * k / 3f
					oval.set(x - rr, y - rr, x + rr, y + rr)
					canvas.drawArc(oval, 180f, 180f, false, paint)
				}
				x += step
			}
			y += step / 4f
			row++
		}
		paint.style = Paint.Style.FILL
	}

	/** Samekomon: fine dots in offset rows. */
	private fun drawDots(canvas: Canvas, l: Float, t: Float, r: Float, btm: Float, cell: Float) {
		val step = cell * 0.8f
		var row = 0
		var y = t
		while (y < btm) {
			var x = l + if (row % 2 == 0) 0f else step / 2f
			while (x < r) {
				canvas.drawCircle(x, y, step / 5f, paint)
				x += step
			}
			y += step * 0.7f
			row++
		}
	}

	/** Shima: diagonal stripes. */
	private fun drawStripes(canvas: Canvas, l: Float, t: Float, r: Float, btm: Float, cell: Float) {
		paint.style = Paint.Style.STROKE
		paint.strokeWidth = cell / 2.5f
		val h = btm - t
		var x = l - h
		while (x < r) {
			canvas.drawLine(x, btm, x + h, t, paint)
			x += cell
		}
		paint.style = Paint.Style.FILL
	}

	override fun setAlpha(alpha: Int) {
		paint.alpha = alpha
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		paint.colorFilter = colorFilter
	}

	@Deprecated("Deprecated in Java")
	override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

	companion object {

		const val NONE = -1
		const val COUNT = 4
		private const val ICHIMATSU = 0
		private const val SEIGAIHA = 1
		private const val SAMEKOMON = 2
	}
}
