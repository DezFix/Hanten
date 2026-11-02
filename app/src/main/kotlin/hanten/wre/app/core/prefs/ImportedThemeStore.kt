package hanten.wre.app.core.prefs

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for user-imported Idle Fantasy themes, kept outside of [AppSettings] because it is a
 * separate JSON blob with its own lifecycle.
 */
@Singleton
class ImportedThemeStore @Inject constructor(@ApplicationContext context: Context) {

	private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

	/** Parsed imported themes, cached in memory and invalidated on write. */
	val themes: List<ImportedTheme>
		get() = cachedThemes ?: ImportedThemeFile
			.parseStored(prefs.getString(KEY_THEMES, "{}") ?: "{}")
			.also { cachedThemes = it }

	var selectedThemeId: String?
		get() = prefs.getString(KEY_SELECTED, null)
		set(value) = prefs.edit { putString(KEY_SELECTED, value) }

	val selectedTheme: ImportedTheme?
		get() = selectedThemeId?.let { id -> themes.firstOrNull { it.id == id } }

	fun import(source: String): List<ImportedTheme> {
		val imported = ImportedThemeFile.parseImport(source)
		val stored = prefs.getString(KEY_THEMES, "{}") ?: "{}"
		prefs.edit { putString(KEY_THEMES, ImportedThemeFile.mergeStored(stored, imported)) }
		cachedThemes = null
		return imported
	}

	companion object {

		const val KEY_THEMES = "imported_themes"
		const val KEY_SELECTED = "imported_theme_selected"

		@Volatile
		private var cachedThemes: List<ImportedTheme>? = null
	}
}
