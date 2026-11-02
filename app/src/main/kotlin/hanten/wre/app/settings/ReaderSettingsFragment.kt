package hanten.wre.app.settings

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.model.ZoomMode
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.ChapterCompletionMode
import hanten.wre.app.core.prefs.ReaderAnimation
import hanten.wre.app.core.prefs.ReaderBackground
import hanten.wre.app.core.prefs.ReaderControl
import hanten.wre.app.core.prefs.ReaderMode
import hanten.wre.app.core.ui.BasePreferenceFragment
import hanten.wre.app.core.util.ext.setDefaultValueCompat
import hanten.wre.app.parsers.util.mapToSet
import hanten.wre.app.parsers.util.names
import hanten.wre.app.settings.reader.SmartResumeThresholdDialog
import hanten.wre.app.settings.utils.HelpSwitchPreference
import hanten.wre.app.settings.utils.MultiSummaryProvider
import hanten.wre.app.settings.utils.PercentSummaryProvider
import hanten.wre.app.settings.utils.SliderPreference

@AndroidEntryPoint
class ReaderSettingsFragment :
	BasePreferenceFragment(R.string.reader_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_reader)
		findPreference<ListPreference>(AppSettings.KEY_READER_MODE)?.run {
			entryValues = ReaderMode.entries.names()
			setDefaultValueCompat(ReaderMode.STANDARD.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_ORIENTATION)?.run {
			entryValues = arrayOf(
				ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED.toString(),
				ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR.toString(),
				ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT.toString(),
				ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE.toString(),
			)
			setDefaultValueCompat(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED.toString())
		}
		findPreference<MultiSelectListPreference>(AppSettings.KEY_READER_CONTROLS)?.run {
			entryValues = ReaderControl.entries.names()
			setDefaultValueCompat(ReaderControl.DEFAULT.mapToSet { it.name })
			summaryProvider = MultiSummaryProvider(R.string.none)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_BACKGROUND)?.run {
			entryValues = ReaderBackground.entries.names()
			setDefaultValueCompat(ReaderBackground.DEFAULT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_READER_ANIMATION)?.run {
			entryValues = ReaderAnimation.entries.names()
			setDefaultValueCompat(ReaderAnimation.DEFAULT.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_ZOOM_MODE)?.run {
			entryValues = ZoomMode.entries.names()
			setDefaultValueCompat(ZoomMode.FIT_CENTER.name)
		}
		findPreference<MultiSelectListPreference>(AppSettings.KEY_READER_CROP)?.run {
			summaryProvider = MultiSummaryProvider(R.string.disabled)
		}
		findPreference<SliderPreference>(AppSettings.KEY_WEBTOON_ZOOM_OUT)?.summaryProvider = PercentSummaryProvider()
		updateReaderModeDependency()
		findPreference<EditTextPreference>(AppSettings.KEY_SMART_RESUME_PERCENTAGE)?.summaryProvider =
			Preference.SummaryProvider<EditTextPreference> {
				getString(R.string.smart_resume_percentage_summary, settings.smartResumePercentage)
			}
		findPreference<EditTextPreference>(AppSettings.KEY_SMART_RESUME_PAGES)?.summaryProvider =
			Preference.SummaryProvider<EditTextPreference> {
				val count = settings.smartResumePagesRemaining
				resources.getQuantityString(R.plurals.smart_resume_pages_summary, count, count)
			}
		findPreference<HelpSwitchPreference>(AppSettings.KEY_SMART_RESUME_WAIT)?.run {
			helpDescription = getString(R.string.smart_resume_wait_help_title)
			onHelpClick = {
				MaterialAlertDialogBuilder(requireContext())
					.setTitle(R.string.smart_resume_wait_help_title)
					.setMessage(R.string.smart_resume_wait_help)
					.setPositiveButton(android.R.string.ok, null)
					.show()
			}
		}
		updateSmartResumeRule()
	}

	@Suppress("DEPRECATION")
	override fun onDisplayPreferenceDialog(preference: Preference) {
		if (preference.key != AppSettings.KEY_SMART_RESUME_PERCENTAGE &&
			preference.key != AppSettings.KEY_SMART_RESUME_PAGES
		) {
			super.onDisplayPreferenceDialog(preference)
			return
		}
		val tag = "androidx.preference.PreferenceFragment.DIALOG"
		if (parentFragmentManager.findFragmentByTag(tag) != null) return
		SmartResumeThresholdDialog().apply {
			arguments = bundleOf("key" to preference.key)
			setTargetFragment(this@ReaderSettingsFragment, 0)
		}.show(parentFragmentManager, tag)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_READER_TAP_ACTIONS -> {
				router.openReaderTapGridSettings()
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_READER_MODE -> updateReaderModeDependency()
			AppSettings.KEY_SMART_RESUME_MODE -> updateSmartResumeRule()
		}
	}

	private fun updateReaderModeDependency() {
		findPreference<Preference>(AppSettings.KEY_READER_MODE_DETECT)?.run {
			isEnabled = settings.defaultReaderMode != ReaderMode.WEBTOON
		}
	}

	private fun updateSmartResumeRule() {
		val percentage = settings.smartResumeCompletionMode == ChapterCompletionMode.PERCENTAGE
		findPreference<Preference>(AppSettings.KEY_SMART_RESUME_PERCENTAGE)?.isVisible = percentage
		findPreference<Preference>(AppSettings.KEY_SMART_RESUME_PAGES)?.isVisible = !percentage
	}
}
