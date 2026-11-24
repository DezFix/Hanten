package hanten.wre.app.backups.ui.restore

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hanten.wre.app.R
import hanten.wre.app.backups.data.mihon.MihonBackupConverter
import hanten.wre.app.backups.data.model.BackupIndex
import hanten.wre.app.backups.domain.BackupSection
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.toUriOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Date
import java.util.EnumMap
import java.util.EnumSet
import java.util.zip.ZipInputStream
import javax.inject.Inject

@HiltViewModel
class RestoreViewModel @Inject constructor(
	private val mihonExtensions: hanten.wre.app.mihon.MihonExtensionManager,
	savedStateHandle: SavedStateHandle,
	@ApplicationContext context: Context,
) : BaseViewModel() {

	private val sourceUri = savedStateHandle.get<String>(AppRouter.KEY_FILE)?.toUriOrNull()
	private val contentResolver = context.contentResolver
	private val cacheDir = context.cacheDir
	private val uncategorizedTitle = context.getString(R.string.library_from_mihon)

	/** The backup to restore: the picked file, or its Hanten-format copy if a Mihon backup was picked. */
	@Volatile
	var uri: Uri? = sourceUri
		private set

	/** Set when a Mihon/Tachiyomi backup was converted: what it contained. */
	val mihonImport = MutableStateFlow<MihonBackupConverter.Result?>(null)

	/** Import summary shown in the dialog for a Mihon/Tachiyomi backup: what moves and which extensions are missing */
	val mihonSummary = MutableStateFlow<String?>(null)
	private val resources = context.resources

	val availableEntries = MutableStateFlow<List<BackupSectionModel>>(emptyList())
	val backupDate = MutableStateFlow<Date?>(null)
    val isMergeEnabled = MutableStateFlow(false)

	init {
		launchLoadingJob(Dispatchers.IO) {
			loadBackupInfo()
		}
	}

	private suspend fun loadBackupInfo() {
		val sections = runInterruptible(Dispatchers.IO) {
			val uri = prepareBackupFile() ?: throw FileNotFoundException()
			this.uri = uri
			ZipInputStream(contentResolver.openInputStream(uri)).use { stream ->
				val result = EnumSet.noneOf(BackupSection::class.java)
				var entry = stream.nextEntry
				while (entry != null) {
					val s = BackupSection.of(entry)
					if (s != null) {
						result.add(s)
						if (s == BackupSection.INDEX) {
							backupDate.value = stream.readDate()
						}
					}
					stream.closeEntry()
					entry = stream.nextEntry
				}
				result
			}
		}
		availableEntries.value = BackupSection.entries.mapNotNull { entry ->
			if (entry == BackupSection.INDEX || entry !in sections) {
				return@mapNotNull null
			}
			BackupSectionModel(
				section = entry,
				isChecked = true,
				isEnabled = true,
			)
		}
	}

	fun onItemClick(item: BackupSectionModel) {
		val map = availableEntries.value.associateByTo(EnumMap(BackupSection::class.java)) { it.section }
		map[item.section] = item.copy(isChecked = !item.isChecked)
		map.validate()
		availableEntries.value = map.values.sortedBy { it.section.ordinal }
	}

    fun onMergeToggle(isChecked: Boolean) {
        isMergeEnabled.value = isChecked
    }

	fun getCheckedSections(): Set<BackupSection> = availableEntries.value
		.mapNotNullTo(EnumSet.noneOf(BackupSection::class.java)) {
			if (it.isChecked) it.section else null
		}

	/**
	 * Check for inconsistent user selection
	 * Favorites cannot be restored without categories
	 */
	private fun MutableMap<BackupSection, BackupSectionModel>.validate() {
		val favorites = this[BackupSection.FAVOURITES] ?: return
		val categories = this[BackupSection.CATEGORIES]
		if (categories?.isChecked == true) {
			if (!favorites.isEnabled) {
				this[BackupSection.FAVOURITES] = favorites.copy(isEnabled = true)
			}
		} else {
			if (favorites.isEnabled) {
				this[BackupSection.FAVOURITES] = favorites.copy(isEnabled = false, isChecked = false)
			}
		}
	}

	/**
	 * Mihon/Tachiyomi backups (.tachibk, gzip) are converted into a Hanten backup in the cache dir,
	 * so the rest of the restore flow, including RestoreService, reads one format only.
	 */
	private fun prepareBackupFile(): Uri? {
		val source = sourceUri ?: return null
		val stream = contentResolver.openInputStream(source) ?: return null
		BufferedInputStream(stream).use { input ->
			if (!MihonBackupConverter.isMihonBackup(input)) {
				return source
			}
			val dir = File(cacheDir, MIHON_IMPORT_DIR).apply { mkdirs() }
			val file = File(dir, MIHON_IMPORT_FILE)
			file.outputStream().use { output ->
				val result = MihonBackupConverter.convert(input, output, uncategorizedTitle)
				mihonImport.value = result
				mihonSummary.value = buildMihonSummary(result)
			}
			return Uri.fromFile(file)
		}
	}

	private fun buildMihonSummary(r: MihonBackupConverter.Result): String {
		val sb = StringBuilder(
			resources.getString(R.string.mihon_import_summary, r.favourites, r.categories, r.history, r.tracking),
		)
		val missing = r.titlesBySource.filterKeys { mihonExtensions.getSourceById(it) == null }
		if (missing.isNotEmpty()) {
			val names = missing.entries.sortedByDescending { it.value }.joinToString { (id, count) ->
				"${r.sourceNames[id] ?: id.toString()} ($count)"
			}
			sb.append("\n\n").append(resources.getString(R.string.mihon_import_missing_extensions, names))
		}
		return sb.toString()
	}

	private fun InputStream.readDate(): Date? = runCatching {
		val index = Json.decodeFromStream<List<BackupIndex>>(this)
		Date(index.single().createdAt)
	}.onFailure { e ->
		e.printStackTraceDebug("RestoreViewModel::InputStream")
	}.getOrNull()

	private companion object {

		const val MIHON_IMPORT_DIR = "restore"
		const val MIHON_IMPORT_FILE = "mihon-import.bk.zip"
	}
}
