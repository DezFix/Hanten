package hanten.wre.app.extras

import android.app.Activity
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import hanten.wre.app.R
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Rotation
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import nl.dionsegijn.konfetti.xml.image.DrawableImage
import java.util.concurrent.TimeUnit

/**
 * Hanten 3.0: one-off particle effects drawn over the whole activity (own art only: petals, paws, sparks).
 * A temporary [KonfettiView] is added on top of android.R.id.content and removed when the effect is over.
 */
object Celebrations {

	private val SAKURA = listOf(0xF8BBD0, 0xF48FB1, 0xFCE4EC, 0xF06292)
	private val PAWS = listOf(0xB39DDB, 0xF48FB1, 0xFFE082, 0x80CBC4)
	private val SPARKS = listOf(0xFCE18A, 0xFF726D, 0xF4306D, 0xB48DEF, 0x80DEEA)
	private val SNOW = listOf(0xFFFFFF, 0xE3F2FD, 0xBBDEFB)
	private val PUMPKIN = listOf(0xFF9800, 0xFFB74D, 0x7E57C2, 0x212121)

	/** Petals falling over the whole screen for a few seconds (About, hanami). */
	fun sakuraRain(activity: Activity, seconds: Int = 4) {
		val petal = shape(activity, R.drawable.ic_fx_petal) ?: return
		play(
			activity,
			listOf(
				Party(
					speed = 0f,
					maxSpeed = 6f,
					damping = 0.95f,
					angle = Angle.BOTTOM,
					spread = Spread.ROUND,
					size = listOf(Size(14), Size(18), Size(22)),
					shapes = listOf(petal),
					colors = SAKURA,
					timeToLive = 6000L,
					rotation = Rotation(enabled = true, speed = 1f, variance = 1f, multiplier3D = 1.5f),
					emitter = Emitter(duration = seconds.toLong(), TimeUnit.SECONDS).perSecond(18),
					position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0)),
				),
			),
			lifetimeMs = seconds * 1000L + 6000L,
		)
	}

	/** A handful of petals from the top edge: pull-to-refresh on Today. */
	fun petalPuff(activity: Activity) {
		val petal = shape(activity, R.drawable.ic_fx_petal) ?: return
		play(
			activity,
			listOf(
				Party(
					speed = 4f,
					maxSpeed = 10f,
					damping = 0.94f,
					angle = Angle.BOTTOM,
					spread = 70,
					size = listOf(Size(14), Size(18)),
					shapes = listOf(petal),
					colors = SAKURA,
					timeToLive = 2600L,
					rotation = Rotation(multiplier3D = 1.5f),
					emitter = Emitter(duration = 120, TimeUnit.MILLISECONDS).max(9),
					position = Position.Relative(0.5, 0.08),
				),
			),
			lifetimeMs = 3500L,
		)
	}

	/** Cat paws hopping up from the bottom: «ня» in search. */
	fun paws(activity: Activity) {
		val paw = shape(activity, R.drawable.ic_fx_paw) ?: return
		play(
			activity,
			listOf(
				Party(
					speed = 30f,
					maxSpeed = 48f,
					damping = 0.9f,
					angle = Angle.TOP,
					spread = 50,
					size = listOf(Size(24), Size(30)),
					shapes = listOf(paw),
					colors = PAWS,
					timeToLive = 2600L,
					rotation = Rotation(multiplier3D = 0f, speed = 0.4f),
					emitter = Emitter(duration = 600, TimeUnit.MILLISECONDS).max(24),
					position = Position.Relative(0.5, 1.0),
				),
			),
			lifetimeMs = 4000L,
		)
	}

	/** Several bursts at different points: 1000th chapter, New Year. */
	fun fireworks(activity: Activity, colors: List<Int> = SPARKS) {
		val base = Party(
			speed = 0f,
			maxSpeed = 32f,
			damping = 0.9f,
			spread = 360,
			size = listOf(Size.SMALL, Size.MEDIUM),
			shapes = listOf(Shape.Circle, Shape.Square),
			colors = colors,
			timeToLive = 2200L,
			emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(70),
			position = Position.Relative(0.5, 0.3),
		)
		play(
			activity,
			listOf(
				base,
				base.copy(position = Position.Relative(0.22, 0.42), delay = 400),
				base.copy(position = Position.Relative(0.78, 0.36), delay = 800),
				base.copy(position = Position.Relative(0.5, 0.55), delay = 1200),
			),
			lifetimeMs = 5000L,
		)
	}

	/** Slow snow for the New Year greeting on Today. */
	fun snow(activity: Activity) {
		play(activity, listOf(fallingDots(SNOW, Shape.Circle)), lifetimeMs = 9000L)
	}

	/** Orange and purple confetti for Halloween. */
	fun halloween(activity: Activity) {
		play(activity, listOf(fallingDots(PUMPKIN, Shape.Square)), lifetimeMs = 9000L)
	}

	/** Golden sparks drifting up for Tanabata (the star festival). */
	fun stars(activity: Activity) {
		play(
			activity,
			listOf(
				Party(
					speed = 2f,
					maxSpeed = 8f,
					damping = 0.97f,
					angle = Angle.TOP,
					spread = Spread.ROUND,
					size = listOf(Size.SMALL, Size.MEDIUM),
					shapes = listOf(Shape.Circle),
					colors = listOf(0xFFF59D, 0xFFE082, 0x90CAF9, 0xFFFFFF),
					timeToLive = 5000L,
					emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(25),
					position = Position.Relative(0.0, 1.0).between(Position.Relative(1.0, 1.0)),
				),
			),
			lifetimeMs = 9000L,
		)
	}

	private fun fallingDots(colors: List<Int>, shape: Shape) = Party(
		speed = 0f,
		maxSpeed = 5f,
		damping = 0.96f,
		angle = Angle.BOTTOM,
		spread = Spread.ROUND,
		size = listOf(Size.SMALL, Size.MEDIUM),
		shapes = listOf(shape),
		colors = colors,
		timeToLive = 6000L,
		emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(30),
		position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0)),
	)

	private fun shape(activity: Activity, @DrawableRes res: Int): Shape.DrawableShape? {
		val drawable = ContextCompat.getDrawable(activity, res) ?: return null
		val image = DrawableImage(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
		return Shape.DrawableShape(image, tint = true, applyAlpha = true)
	}

	private fun play(activity: Activity, parties: List<Party>, lifetimeMs: Long) {
		if (activity.isFinishing || activity.isDestroyed) return
		val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
		val view = KonfettiView(activity).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			)
			isClickable = false
			isFocusable = false
			importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		content.addView(view)
		view.start(parties)
		view.postDelayed({ content.removeView(view) }, lifetimeMs)
	}
}
