package hanten.wre.app.settings.about

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
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
import hanten.wre.app.core.prefs.LocalState
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.extras.Celebrations
import hanten.wre.app.extras.HantenPatternDrawable
import hanten.wre.app.settings.utils.LayoutPreference
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutSettingsFragment : BasePreferenceFragment(R.string.about) {

	override val isGroupedCards: Boolean
		get() = false

	private val viewModel by viewModels<AboutSettingsViewModel>()

	@Inject
	lateinit var updateFlow: AppUpdateFlow

	@Inject
	lateinit var appSettings: AppSettings

	@Inject
	lateinit var localState: LocalState

	private val installPermissionLauncher: ActivityResultLauncher<Intent> =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			updateFlow.handlePermissionResult(requireActivity(), listView, installPermissionLauncher)
		}

	private var versionClickCount = 0

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_about)
		findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.run {
			title = getString(R.string.app_version, BuildConfig.VERSION_NAME)
		}
		findPreference<Preference>(AppSettings.KEY_LINK_TELEGRAM)?.isVisible = false
		// Hanten 3.0: version, update check and Ko-fi moved into the header and the support card (mockup);
		// the old rows stay in the XML for settings search only
		findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.isVisible = false
		findPreference<Preference>("check_updates")?.isVisible = false
		findPreference<Preference>("about_donate")?.isVisible = false
		findPreference<LayoutPreference>("about_header")?.run {
			clickableIds = intArrayOf(R.id.text_version, R.id.button_check_updates, R.id.image_app_icon)
			onBind = { view ->
				view.findViewById<TextView>(R.id.text_version)?.text =
					getString(R.string.about_version_line, BuildConfig.VERSION_NAME)
				bindIconPattern(view)
			}
			onViewClick = { v ->
				when (v.id) {
					R.id.button_check_updates -> checkForUpdates()
					R.id.text_version -> onVersionClick()
					R.id.image_app_icon -> onIconClick(v)
				}
			}
		}
		findPreference<LayoutPreference>("about_donate_card")?.run {
			clickableIds = intArrayOf(R.id.button_donate)
			onViewClick = { openLink(R.string.url_donate, getString(R.string.donate_title)) }
		}
	}

	/** Easter egg: 7 taps on the version line — sakura petals fall over the screen. */
	private fun onVersionClick() {
		if (!appSettings.isEasterEggsEnabled) return
		versionClickCount++
		if (versionClickCount == VERSION_TAPS) {
			versionClickCount = 0
			activity?.let { Celebrations.sakuraRain(it) }
		}
	}

	/** Easter egg: tapping the icon changes the hanten pattern behind it (kept on this device). */
	private fun onIconClick(icon: View) {
		if (!appSettings.isEasterEggsEnabled) return
		localState.aboutPattern = (localState.aboutPattern + 1) % (HantenPatternDrawable.COUNT + 1)
		(icon.parent as? View)?.let { bindIconPattern(it) }
	}

	private fun bindIconPattern(view: View) {
		val frame = view.findViewById<View>(R.id.frame_icon_pattern) ?: return
		val index = localState.aboutPattern - 1
		frame.background = if (index < 0 || !appSettings.isEasterEggsEnabled) {
			null
		} else {
			val ctx = frame.context
			HantenPatternDrawable(
				pattern = index,
				background = ctx.getThemeColor(com.google.android.material.R.attr.colorPrimaryContainer),
				foreground = ctx.getThemeColor(androidx.appcompat.R.attr.colorPrimary),
			)
		}
		view.findViewById<ImageView>(R.id.image_app_icon)?.isClickable = appSettings.isEasterEggsEnabled
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_APP_VERSION -> {
				onVersionClick()
				true
			}

			"about_issues" -> {
				if (!router.openExternalBrowser(getString(R.string.url_github) + "/issues", preference.title)) {
					Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
				}
				true
			}

			AppSettings.KEY_LINK_WEBLATE -> {
				// Weblate project is gone: translations are offered through a GitHub issue
				router.openExternalBrowser(getString(R.string.url_github) + "/issues/new?title=Translation", preference.title)
				true
			}

			"about_legal" -> {
				androidx.appcompat.app.AlertDialog.Builder(requireContext())
					.setTitle(R.string.legal_notice)
					.setMessage(R.string.legal_notice_text)
					.setPositiveButton(android.R.string.ok, null)
					.show()
				true
			}

			AppSettings.KEY_LINK_GITHUB -> {
				openLink(R.string.url_github, preference.title)
				true
			}

			AppSettings.KEY_LINK_WEBSITE -> {
				openLink(R.string.url_website, preference.title)
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

	private fun openLink(
		@StringRes url: Int,
		title: CharSequence?
	): Boolean = if (router.openExternalBrowser(getString(url), title)) {
		true
	} else {
		Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		false
	}

	private companion object {

		const val VERSION_TAPS = 7
	}
}
