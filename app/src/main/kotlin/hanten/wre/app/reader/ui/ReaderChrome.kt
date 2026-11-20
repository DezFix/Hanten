package hanten.wre.app.reader.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet

/**
 * The reader "chrome" shared by the manga and the novel reader: a top bar (title, chapter, actions) and a
 * docked bottom bar (previous/next chapter, slider, actions). Both readers show and hide it the same way,
 * so they feel like one app.
 */
object ReaderChrome {

	private const val DURATION_MS = 200L

	/**
	 * Shows or hides the bars; the top one slides from the top, the bottom one from the bottom, [fade] views
	 * (e.g. the info bar shown while the chrome is hidden) cross-fade.
	 */
	fun setVisible(
		root: ViewGroup,
		top: View,
		bottom: View?,
		isVisible: Boolean,
		animate: Boolean,
		vararg fade: View,
	) {
		if (animate) {
			val transition = TransitionSet()
				.setOrdering(TransitionSet.ORDERING_TOGETHER)
				.setDuration(DURATION_MS)
				.addTransition(Slide(Gravity.TOP).addTarget(top))
			bottom?.let { transition.addTransition(Slide(Gravity.BOTTOM).addTarget(it)) }
			if (fade.isNotEmpty()) {
				val fadeTransition = Fade()
				fade.forEach { fadeTransition.addTarget(it) }
				transition.addTransition(fadeTransition)
			}
			TransitionManager.beginDelayedTransition(root, transition)
		}
		top.isVisible = isVisible
		bottom?.isVisible = isVisible
	}
}
