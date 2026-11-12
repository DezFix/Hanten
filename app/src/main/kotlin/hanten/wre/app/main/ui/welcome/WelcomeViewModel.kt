package hanten.wre.app.main.ui.welcome

import android.content.Context
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import hanten.wre.app.core.LocalizedAppContext
import hanten.wre.app.core.model.getLocale
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.ListMode
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.LocaleComparator
import hanten.wre.app.core.util.ext.mapSortedByCount
import hanten.wre.app.core.util.ext.sortedWithSafe
import hanten.wre.app.core.util.ext.toList
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.filter.ui.model.FilterProperty
import hanten.wre.app.parsers.model.ContentType
import hanten.wre.app.parsers.model.MangaParserSource
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.util.mapToSet
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
	private val repository: MangaSourcesRepository,
	private val settings: AppSettings,
	@LocalizedAppContext context: Context,
) : BaseViewModel() {

	private val allSources: List<MangaSource> = buildList {
		addAll(repository.allMangaSources)
	}
	private val localesGroups by lazy {
		allSources.groupBy { it.getLocale() ?: Locale.ROOT }
	}

	private var updateJob: Job

	val locales = MutableStateFlow(
		FilterProperty<Locale>(
			availableItems = listOf(Locale.ROOT),
			selectedItems = setOf(Locale.ROOT),
			isLoading = true,
			error = null,
		),
	)

	val types = MutableStateFlow(
		FilterProperty(
			availableItems = listOf(ContentType.MANGA),
			selectedItems = setOf(ContentType.MANGA),
			isLoading = true,
			error = null,
		),
	)

	/**
	 * Beta: library appearance picked on the welcome screen.
	 * Written straight to settings, no migration involved.
	 */
	val listMode = MutableStateFlow(settings.favoritesListMode)

	fun setListMode(mode: ListMode) {
		listMode.value = mode
		settings.favoritesListMode = mode
	}

	init {
		updateJob = launchJob(Dispatchers.IO) {
			val contentTypes = allSources.mapSortedByCount { it.contentTypeOrNull() ?: ContentType.OTHER }
			types.value = types.value.copy(
				availableItems = contentTypes,
				selectedItems = buildSet {
					add(ContentType.MANGA)
					if (ContentType.NOVEL in contentTypes) add(ContentType.NOVEL)
				},
				isLoading = false,
			)
			val languages = localesGroups.keys.associateBy { x -> x.language }
			val selectedLocales = HashSet<Locale>(2)
			ConfigurationCompat.getLocales(context.resources.configuration).toList()
				.firstNotNullOfOrNull { lc -> languages[lc.language] }
				?.let { selectedLocales += it }
			selectedLocales += Locale.ROOT
			locales.value = locales.value.copy(
				availableItems = localesGroups.keys.sortedWithSafe(LocaleComparator()),
				selectedItems = selectedLocales,
				isLoading = false,
			)
			repository.clearNewSourcesBadge()
			commit()
		}
	}

	fun setLocaleChecked(locale: Locale, isChecked: Boolean) {
		val snapshot = locales.value
		locales.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + locale
			} else {
				snapshot.selectedItems - locale
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.IO) {
			prevJob.join()
			commit()
		}
	}

	fun setTypeChecked(type: ContentType, isChecked: Boolean) {
		val snapshot = types.value
		types.value = snapshot.copy(
			selectedItems = if (isChecked) {
				snapshot.selectedItems + type
			} else {
				snapshot.selectedItems - type
			},
		)
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.IO) {
			prevJob.join()
			commit()
		}
	}

	private suspend fun commit() {
		val languages = locales.value.selectedItems.mapToSet { it.language }
		val types = types.value.selectedItems
		val enabledSources = allSources.filter { source ->
			val type = source.contentTypeOrNull()
			val language = source.getLocale()?.language.orEmpty()
			type != null && type in types && language in languages
		}
		// Never leave the app without a single source: the sheet is dismissible now, and a user who
		// unticks everything must still end up with a working library instead of empty screens
		val safe = if (enabledSources.isEmpty()) {
			allSources.filter { it.contentTypeOrNull() == ContentType.MANGA }
		} else {
			enabledSources
		}
		repository.setSourcesEnabledExclusive(safe.toSet())
	}

	private fun MangaSource.contentTypeOrNull(): ContentType? = when (this) {
		is MangaParserSource -> contentType
		else -> null
	}
}
