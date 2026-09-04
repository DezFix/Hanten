package hanten.wre.app.settings.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.BuildConfig
import hanten.wre.app.R
import hanten.wre.app.core.github.AppUpdateFlow
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BasePreferenceFragment
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.xml.KonfettiView
import nl.dionsegijn.konfetti.xml.image.DrawableImage
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class AboutSettingsFragment : BasePreferenceFragment(R.string.about) {

	private val viewModel by viewModels<AboutSettingsViewModel>()

	@Inject
	lateinit var updateFlow: AppUpdateFlow

	private val installPermissionLauncher: ActivityResultLauncher<Intent> =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			updateFlow.handlePermissionResult(requireActivity(), listView, installPermissionLauncher)
		}

	private var versionClickCount = 0
	private lateinit var konfettiView: KonfettiView

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_about)
		findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.run {
			title = getString(R.string.app_version, BuildConfig.VERSION_NAME)
		}
		findPreference<Preference>(AppSettings.KEY_LINK_TELEGRAM)?.isVisible = false
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val list = super.onCreateView(inflater, container, savedInstanceState)
		konfettiView = KonfettiView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
			// Ensure it doesn't consume clicks
			isClickable = false
			isFocusable = false
		}
		return FrameLayout(requireContext()).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
			addView(list)
			addView(konfettiView)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
	}

	override fun onDestroyView() {
		(view as? ViewGroup)?.removeView(konfettiView)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_APP_VERSION -> {
				versionClickCount++
				if (versionClickCount == 8) {
					versionClickCount = 0
					triggerEasterEgg()
				}
				true
			}

			AppSettings.KEY_LINK_WEBLATE -> {
				openLink(R.string.url_weblate, preference.title)
				true
			}

			AppSettings.KEY_LINK_GITHUB -> {
				openLink(R.string.url_github, preference.title)
				true
			}

			AppSettings.KEY_LINK_MANUAL -> {
				openLink(R.string.url_user_manual, preference.title)
				true
			}

			AppSettings.KEY_LINK_TELEGRAM -> {
				if (!openLink(R.string.url_telegram, null)) {
					openLink(R.string.url_telegram_web, preference.title)
				}
				true
			}

			"check_updates" -> {
				checkForUpdates()
				true
			}

			"about_donate" -> {
				openLink(R.string.url_donate, preference.title)
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun checkForUpdates() {
		lifecycleScope.launch {
			val release = updateFlow.checkForUpdate(ignoreSkippedTag = true)
			if (release == null) {
				Snackbar.make(listView, R.string.update_uptodate, Snackbar.LENGTH_SHORT).show()
			} else {
				updateFlow.showUpdateDialog(requireActivity(), listView, installPermissionLauncher, release)
			}
		}
	}

	private fun triggerEasterEgg() {
		val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.unicorn)
		if (drawable == null) {
			Snackbar.make(listView, "Failed to load unicorn drawable", Snackbar.LENGTH_SHORT).show()
			return
		}

		val coreImage = DrawableImage(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
		val drawableShape = Shape.DrawableShape(coreImage, tint = false, applyAlpha = true)

		val presets = listOf(
			Presets.festive(drawableShape),
			Presets.explode(drawableShape),
			Presets.parade(drawableShape),
			Presets.rain(drawableShape)
		)

		val randomPreset = presets[Random.nextInt(presets.size)]
		konfettiView.start(randomPreset)
	}

	private fun openLink(
		@StringRes url: Int,
		title: CharSequence?
	): Boolean = if (router.openExternalBrowser(getString(url), title)) {
		true
	} else {
		Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		false
	}
}
