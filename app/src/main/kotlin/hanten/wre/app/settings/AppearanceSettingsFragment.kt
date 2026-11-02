package hanten.wre.app.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hanten.wre.app.R
import hanten.wre.app.core.os.AppShortcutManager
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.ColorScheme
import hanten.wre.app.core.prefs.ImportedThemeFile
import hanten.wre.app.core.prefs.ImportedThemeStore
import hanten.wre.app.core.prefs.ListMode
import hanten.wre.app.core.prefs.ProgressIndicatorMode
import hanten.wre.app.core.prefs.ScreenshotsPolicy
import hanten.wre.app.core.prefs.SearchSuggestionType
import hanten.wre.app.core.prefs.TriStateOption
import hanten.wre.app.core.ui.BasePreferenceFragment
import hanten.wre.app.core.ui.util.ActivityRecreationHandle
import hanten.wre.app.core.util.LocaleComparator
import hanten.wre.app.core.util.ext.getLocalesConfig
import hanten.wre.app.core.util.ext.postDelayed
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.setDefaultValueCompat
import hanten.wre.app.core.util.ext.sortedWithSafe
import hanten.wre.app.core.util.ext.toList
import hanten.wre.app.parsers.util.mapToSet
import hanten.wre.app.parsers.util.names
import hanten.wre.app.parsers.util.toTitleCase
import hanten.wre.app.settings.protect.ProtectSetupActivity
import hanten.wre.app.settings.utils.ActivityListPreference
import hanten.wre.app.settings.utils.MultiSummaryProvider
import hanten.wre.app.settings.utils.PercentSummaryProvider
import hanten.wre.app.settings.utils.SliderPreference
import hanten.wre.app.settings.utils.ThemeChooserPreference
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

@AndroidEntryPoint
class AppearanceSettingsFragment :
    BasePreferenceFragment(R.string.appearance),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val customThemeFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importCustomTheme(uri)
        }
    }

    @Inject
    lateinit var activityRecreationHandle: ActivityRecreationHandle

    @Inject
    lateinit var appShortcutManager: AppShortcutManager

    @Inject
    lateinit var importedThemeStore: ImportedThemeStore

    private var restartScheduled = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_appearance)
        findPreference<SliderPreference>(AppSettings.KEY_GRID_SIZE)?.summaryProvider = PercentSummaryProvider()
        findPreference<ListPreference>(AppSettings.KEY_LIST_MODE)?.run {
            entryValues = ListMode.entries.names()
            setDefaultValueCompat(ListMode.GRID.name)
        }
        findPreference<ListPreference>(AppSettings.KEY_PROGRESS_INDICATORS)?.run {
            entryValues = ProgressIndicatorMode.entries.names()
            setDefaultValueCompat(ProgressIndicatorMode.PERCENT_READ.name)
        }
        findPreference<ActivityListPreference>(AppSettings.KEY_APP_LOCALE)?.run {
            initLocalePicker(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityIntent = Intent(
                    Settings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            }
            summaryProvider = Preference.SummaryProvider<ActivityListPreference> {
                val locale = AppCompatDelegate.getApplicationLocales().get(0)
                locale?.getDisplayName(locale)?.toTitleCase(locale) ?: getString(R.string.follow_system)
            }
            setDefaultValueCompat("")
        }
        findPreference<MultiSelectListPreference>(AppSettings.KEY_MANGA_LIST_BADGES)?.run {
            summaryProvider = MultiSummaryProvider(R.string.none)
        }
        findPreference<Preference>(KEY_CUSTOM_THEME_IMPORT)?.run {
            isVisible = DynamicColors.isDynamicColorAvailable()
            setOnPreferenceClickListener {
                customThemeFilePicker.launch(arrayOf("*/*"))
                true
            }
        }
        findPreference<Preference>(AppSettings.KEY_SHORTCUTS)?.isVisible =
            appShortcutManager.isDynamicShortcutsAvailable()
        findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
            ?.isChecked = !settings.appPassword.isNullOrEmpty()
        findPreference<ListPreference>(AppSettings.KEY_SCREENSHOTS_POLICY)?.run {
            entryValues = ScreenshotsPolicy.entries.names()
            setDefaultValueCompat(ScreenshotsPolicy.ALLOW.name)
        }
        findPreference<MultiSelectListPreference>(AppSettings.KEY_SEARCH_SUGGESTION_TYPES)?.let { pref ->
            pref.entryValues = SearchSuggestionType.entries.names()
            pref.entries = SearchSuggestionType.entries.map { pref.context.getString(it.titleResId) }.toTypedArray()
            pref.summaryProvider = MultiSummaryProvider(R.string.none)
            pref.values = settings.searchSuggestionTypes.mapToSet { it.name }
        }
        bindNavSummary()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings.subscribe(this)
    }

    override fun onDestroyView() {
        settings.unsubscribe(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        when (key) {
            AppSettings.KEY_THEME -> {
                AppCompatDelegate.setDefaultNightMode(settings.theme)
            }

            AppSettings.KEY_COLOR_THEME,
            AppSettings.KEY_THEME_AMOLED,
                -> {
                postRestart()
            }

            ImportedThemeStore.KEY_SELECTED -> {
                if (settings.colorScheme == ColorScheme.CUSTOM) {
                    postRestart()
                }
            }

            AppSettings.KEY_APP_LOCALE -> {
                AppCompatDelegate.setApplicationLocales(settings.appLocales)
            }

            AppSettings.KEY_NAV_MAIN -> {
                bindNavSummary()
            }

            AppSettings.KEY_APP_PASSWORD -> {
                findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
                    ?.isChecked = !settings.appPassword.isNullOrEmpty()
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            AppSettings.KEY_PROTECT_APP -> {
                val pref = (preference as? TwoStatePreference ?: return false)
                if (pref.isChecked) {
                    pref.isChecked = false
                    startActivity(Intent(preference.context, ProtectSetupActivity::class.java))
                } else {
                    settings.appPassword = null
                }
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun postRestart() {
        if (restartScheduled) {
            return
        }
        restartScheduled = true
        viewLifecycleOwner.lifecycle.postDelayed(400) {
            restartScheduled = false
            activityRecreationHandle.recreateAll()
        }
    }

    private fun importCustomTheme(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val wasCustom = settings.colorScheme == ColorScheme.CUSTOM
                val previousThemeId = importedThemeStore.selectedThemeId
                val json = withContext(Dispatchers.IO) { readThemeFile(appContext, uri) }
                val imported = importedThemeStore.import(json)
                val newThemeId = imported.last().id
                importedThemeStore.selectedThemeId = newThemeId
                findPreference<ThemeChooserPreference>(AppSettings.KEY_COLOR_THEME)?.apply {
                    value = ColorScheme.CUSTOM.name
                    refresh()
                }
                Toast.makeText(appContext, R.string.custom_theme_imported, Toast.LENGTH_SHORT).show()
                if (wasCustom && previousThemeId == newThemeId) {
                    postRestart()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTraceDebug()
                Toast.makeText(appContext, R.string.custom_theme_import_failed, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun readThemeFile(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open theme file")
        return input.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val length = reader.read(buffer)
                if (length < 0) break
                require(result.length + length <= ImportedThemeFile.MAX_FILE_SIZE) {
                    "Theme file is too large"
                }
                result.append(buffer, 0, length)
            }
            result.toString()
        }
    }

    private fun initLocalePicker(preference: ListPreference) {
        val locales = preference.context.getLocalesConfig()
            .toList()
            .sortedWithSafe(LocaleComparator())
        preference.entries = Array(locales.size + 1) { i ->
            if (i == 0) {
                getString(R.string.follow_system)
            } else {
                val lc = locales[i - 1]
                lc.getDisplayName(lc).toTitleCase(lc)
            }
        }
        preference.entryValues = Array(locales.size + 1) { i ->
            if (i == 0) {
                ""
            } else {
                locales[i - 1].toLanguageTag()
            }
        }
    }

    private fun bindNavSummary() {
        val pref = findPreference<Preference>(AppSettings.KEY_NAV_MAIN) ?: return
        pref.summary = settings.mainNavItems.joinToString {
            getString(it.title)
        }
    }

    private companion object {
        const val KEY_CUSTOM_THEME_IMPORT = "custom_theme_import"
    }
}
