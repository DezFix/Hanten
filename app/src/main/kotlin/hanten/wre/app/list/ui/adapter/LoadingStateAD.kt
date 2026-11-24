package hanten.wre.app.list.ui.adapter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate
import hanten.wre.app.R
import hanten.wre.app.core.prefs.AnimationLevel
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.isAnimationsEnabled
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.LoadingState

/**
 * Hanten 3.0: skeleton rows instead of a spinner (pulsing with FULL animations, static with REDUCED);
 * with animations OFF the old spinner is shown.
 */
fun loadingStateAD() = adapterDelegate<LoadingState, ListModel>(R.layout.item_loading_state) {

	val skeleton = findViewById<View>(R.id.layout_skeleton)
	val progress = findViewById<View>(R.id.progress)
	var pulse: ValueAnimator? = null

	bind {
		val level = if (context.isAnimationsEnabled) {
			val raw = PreferenceManager.getDefaultSharedPreferences(context)
				.getString(AppSettings.KEY_ANIMATIONS, null)
			AnimationLevel.entries.find { it.name == raw } ?: AnimationLevel.FULL
		} else {
			AnimationLevel.OFF
		}
		skeleton.isVisible = level != AnimationLevel.OFF
		progress.isVisible = level == AnimationLevel.OFF
		pulse?.cancel()
		skeleton.alpha = 1f
		pulse = if (level == AnimationLevel.FULL) {
			ObjectAnimator.ofFloat(skeleton, View.ALPHA, 1f, 0.45f).apply {
				duration = 800L
				repeatMode = ValueAnimator.REVERSE
				repeatCount = ValueAnimator.INFINITE
				start()
			}
		} else {
			null
		}
	}

	onViewAttachedToWindow { pulse?.resume() }

	onViewDetachedFromWindow { pulse?.pause() }

	onViewRecycled {
		pulse?.cancel()
		pulse = null
	}
}
