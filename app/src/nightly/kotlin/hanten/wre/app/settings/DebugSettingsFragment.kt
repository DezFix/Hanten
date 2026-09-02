package hanten.wre.app.settings

import android.os.Bundle
import androidx.preference.Preference
import leakcanary.LeakCanary
import hanten.wre.app.R
import hanten.wre.app.core.ui.BasePreferenceFragment
import hanten.wre.app.settings.utils.SplitSwitchPreference

class DebugSettingsFragment : BasePreferenceFragment(R.string.debug), Preference.OnPreferenceClickListener {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_debug)
		findPreference<SplitSwitchPreference>(KEY_LEAK_CANARY)?.let { pref ->
			pref.onContainerClickListener = this
		}
	}

	override fun onPreferenceClick(preference: Preference): Boolean = when (preference.key) {
		KEY_LEAK_CANARY -> {
			startActivity(LeakCanary.newLeakDisplayActivityIntent())
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	private companion object {

		const val KEY_LEAK_CANARY = "debug.leak_canary"
	}
}
