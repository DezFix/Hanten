package hanten.wre.app.extras

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.createBitmap
import hanten.wre.app.core.prefs.AnimationLevel
import hanten.wre.app.core.prefs.AppSettings
import kotlin.math.hypot

/**
 * Hanten 3.0: circular reveal when the theme changes.
 * Before the activities are recreated a snapshot of the screen is taken; the recreated activity of the same
 * class draws it on top and opens a growing circular hole from the last touch point, so the new theme
 * spreads out from the finger.
 */
object ThemeReveal {

	private var snapshot: Bitmap? = null
	private var targetClass: String? = null
	private var centerX = 0f
	private var centerY = 0f
	private var lastTouchX = -1f
	private var lastTouchY = -1f

	fun onTouch(x: Float, y: Float) {
		lastTouchX = x
		lastTouchY = y
	}

	/** Call right before the theme change recreates the activities. */
	fun capture(activity: Activity, settings: AppSettings) {
		if (AnimationLevel.effective(activity, settings) != AnimationLevel.FULL) return
		val decor = activity.window?.decorView ?: return
		if (decor.width <= 0 || decor.height <= 0) return
		val bitmap = runCatching {
			createBitmap(decor.width, decor.height).also { decor.draw(Canvas(it)) }
		}.getOrNull() ?: return
		snapshot?.recycle()
		snapshot = bitmap
		targetClass = activity.javaClass.name
		centerX = if (lastTouchX >= 0f) lastTouchX else decor.width / 2f
		centerY = if (lastTouchY >= 0f) lastTouchY else decor.height / 3f
	}

	/** Called from BaseActivity.onPostCreate of every activity; plays only in the one that was captured. */
	fun playIfPending(activity: Activity) {
		val bitmap = snapshot ?: return
		if (activity.javaClass.name != targetClass) return
		snapshot = null
		targetClass = null
		val decor = activity.window?.decorView as? ViewGroup ?: return
		val overlay = RevealOverlay(activity, bitmap, centerX, centerY)
		decor.addView(
			overlay,
			ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
		)
		val maxRadius = hypot(
			maxOf(centerX, bitmap.width - centerX),
			maxOf(centerY, bitmap.height - centerY),
		)
		ValueAnimator.ofFloat(0f, maxRadius).apply {
			duration = DURATION_MS
			startDelay = 60L
			interpolator = DecelerateInterpolator()
			addUpdateListener { overlay.radius = it.animatedValue as Float }
			doOnEnd {
				decor.removeView(overlay)
				bitmap.recycle()
			}
			start()
		}
	}

	@SuppressLint("ViewConstructor")
	private class RevealOverlay(
		context: Context,
		private val bitmap: Bitmap,
		private val cx: Float,
		private val cy: Float,
	) : View(context) {

		private val hole = Path()

		var radius = 0f
			set(value) {
				field = value
				invalidate()
			}

		init {
			isClickable = true // swallow taps while the old screen is still visible
		}

		override fun onDraw(canvas: Canvas) {
			hole.reset()
			hole.addCircle(cx, cy, radius, Path.Direction.CW)
			hole.fillType = Path.FillType.INVERSE_WINDING
			canvas.save()
			canvas.clipPath(hole)
			canvas.drawBitmap(bitmap, 0f, 0f, null)
			canvas.restore()
		}
	}

	private const val DURATION_MS = 650L
}
