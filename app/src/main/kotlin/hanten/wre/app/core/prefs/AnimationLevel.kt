package hanten.wre.app.core.prefs

import android.content.Context
import androidx.annotation.Keep
import hanten.wre.app.core.util.ext.isAnimationsEnabled

/**
 * Hanten 3.0 «Анимации» setting.
 * FULL — cover flight into the title card, circular theme reveal, petals, skeleton pulse;
 * REDUCED — only quiet fades (static skeletons, no flights or petals);
 * OFF — plain spinners and instant changes.
 */
@Keep
enum class AnimationLevel {

	// Do not rename: stored by name
	FULL, REDUCED, OFF;

	companion object {

		/** The user's choice, lowered to OFF when the system "remove animations" switch is on. */
		fun effective(context: Context, settings: AppSettings): AnimationLevel =
			if (context.isAnimationsEnabled) settings.animationLevel else OFF
	}
}
