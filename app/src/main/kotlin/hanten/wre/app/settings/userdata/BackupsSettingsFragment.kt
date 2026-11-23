package hanten.wre.app.settings.userdata

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.backups.domain.BackupUtils
import hanten.wre.app.backups.ui.backup.BackupService
import hanten.wre.app.core.exceptions.resolve.SnackbarErrorObserver
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BasePreferenceFragment
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.core.util.ext.tryLaunch
import hanten.wre.app.settings.utils.LayoutPreference

@AndroidEntryPoint
class BackupsSettingsFragment : BasePreferenceFragment(R.string.backup_screen_title),
    ActivityResultCallback<Uri?> {

    private val viewModel: BackupsSettingsViewModel by viewModels()

    private val backupSelectCall = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
        this,
    )

    private val backupCreateCall = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            if (!BackupService.start(requireContext(), uri)) {
                Snackbar.make(
                    listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_backups)
        // Hanten 3.0: create/restore are big buttons (mockup); the rows stay only for settings search
        findPreference<Preference>(AppSettings.KEY_BACKUP)?.isVisible = false
        findPreference<Preference>(AppSettings.KEY_RESTORE)?.isVisible = false
        findPreference<LayoutPreference>(KEY_ACTIONS)?.run {
            clickableIds = intArrayOf(R.id.button_backup, R.id.button_restore)
            onViewClick = { v ->
                when (v.id) {
                    R.id.button_backup -> createBackup(v.context)
                    R.id.button_restore -> selectBackupFile()
                }
            }
        }
    }

    private fun createBackup(context: android.content.Context) {
        if (!backupCreateCall.tryLaunch(BackupUtils.generateFileName(context))) {
            Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun selectBackupFile() {
        if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
            Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindPeriodicalBackupSummary()
        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            AppSettings.KEY_BACKUP -> {
                createBackup(preference.context)
                true
            }

            // "Move from another app" uses the same picker: the restore dialog detects the format itself
            AppSettings.KEY_RESTORE, KEY_MIGRATE_KOTATSU, KEY_MIGRATE_MIHON -> {
                selectBackupFile()
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    override fun onActivityResult(result: Uri?) {
        if (result != null) {
            router.showBackupRestoreDialog(result)
        }
    }

    private companion object {

        const val KEY_MIGRATE_KOTATSU = "migrate_kotatsu"
        const val KEY_MIGRATE_MIHON = "migrate_mihon"
        const val KEY_ACTIONS = "backup_actions"
    }

    private fun bindPeriodicalBackupSummary() {
        val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_ENABLED) ?: return
        val entries = resources.getStringArray(R.array.backup_frequency)
        val entryValues = resources.getStringArray(R.array.values_backup_frequency)
        viewModel.periodicalBackupFrequency.observe(viewLifecycleOwner) { freq ->
            preference.summary = if (freq == 0L) {
                getString(R.string.disabled)
            } else {
                val index = entryValues.indexOf(freq.toString())
                entries.getOrNull(index)
            }
        }
    }
}
