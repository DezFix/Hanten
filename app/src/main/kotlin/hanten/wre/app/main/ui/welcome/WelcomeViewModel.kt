package hanten.wre.app.main.ui.welcome

import android.content.Context
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import hanten.wre.app.core.LocalizedAppContext
import hanten.wre.app.core.model.getLocale
import hanten.wre.app.core.model.getTitle
import hanten.wre.app.core.model.titleResId
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.LocaleComparator
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

/** What the user reads (mockup cards); each card switches on a group of source content types */
enum class ReadingKind(val types: Set<ContentType>) {
	MANGA(setOf(ContentType.MANGA, ContentType.ONE_SHOT, ContentType.DOUJINSHI)),
	WEBTOON(setOf(ContentType.MANHWA, ContentType.MANHUA)),
	NOVEL(setOf(ContentType.NOVEL)),
	OTHER(
		setOf(
			ContentType.COMICS, ContentType.HENTAI, ContentType.IMAGE_SET,
			ContentType.ARTIST_CG, ContentType.GAME_CG, ContentType.OTHER,
		),
	),
}

data class PopularSourceModel(
	val source: MangaSource,
	val title: String,
	val subtitle: String,
	val isChecked: Boolean,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: MangaSourcesRepository,
	@LocalizedAppContext private val context: Context,
) : BaseViewModel() {

	/** Opened again from More: keep the user's sources until they actually change something */
	private val isRerun = savedStateHandle.get<Boolean>(OnboardingActivity.EXTRA_RERUN) == true

	private val allSources: List<MangaSource> = buildList {
		addAll(repository.allMangaSources)
	}
	private val localesGroups by lazy {
		allSources.groupBy { it.getLocale() ?: Locale.ROOT }
	}
	private val excluded = HashSet<MangaSource>()

	private var updateJob: Job

	val locales = MutableStateFlow(
		FilterProperty<Locale>(
			availableItems = listOf(Locale.ROOT),
			selectedItems = setOf(Locale.ROOT),
			isLoading = true,
			error = null,
		),
	)

	val kinds = MutableStateFlow(setOf(ReadingKind.MANGA, ReadingKind.WEBTOON, ReadingKind.NOVEL))

	val popularSources = MutableStateFlow<List<PopularSourceModel>>(emptyList())

	val enabledCount = MutableStateFlow(0)

	init {
		updateJob = launchJob(Dispatchers.IO) {
			val languages = localesGroups.keys.associateBy { x -> x.language }
			val selectedLocales = HashSet<Locale>(2)
			ConfigurationCompat.getLocales(context.resources.configuration).toList()
				.firstNotNullOfOrNull { lc -> languages[lc.language] }
				?.let { selectedLocales += it }
			// Russian-speaking users usually read Ukrainian sources too (and the other way round)
			if (selectedLocales.any { it.language == "ru" || it.language == "uk" }) {
				languages["ru"]?.let { selectedLocales += it }
				languages["uk"]?.let { selectedLocales += it }
			}
			selectedLocales += Locale.ROOT
			locales.value = locales.value.copy(
				// chosen languages first: the full list is ~50 chips and pushed everything else off screen
				availableItems = localesGroups.keys.sortedWithSafe(LocaleComparator())
					.sortedBy { it !in selectedLocales },
				selectedItems = selectedLocales,
				isLoading = false,
			)
			if (isRerun) {
				updatePreview(computeEnabled())
			} else {
				repository.clearNewSourcesBadge()
				commit()
			}
		}
	}

	fun setLocaleChecked(locale: Locale, isChecked: Boolean) {
		val snapshot = locales.value
		locales.value = snapshot.copy(
			selectedItems = if (isChecked) snapshot.selectedItems + locale else snapshot.selectedItems - locale,
		)
		scheduleCommit()
	}

	fun setKindChecked(kind: ReadingKind, isChecked: Boolean) {
		kinds.value = if (isChecked) kinds.value + kind else kinds.value - kind
		scheduleCommit()
	}

	fun setSourceChecked(source: MangaSource, isChecked: Boolean) {
		if (isChecked) excluded -= source else excluded += source
		scheduleCommit()
	}

	private fun scheduleCommit() {
		val prevJob = updateJob
		updateJob = launchJob(Dispatchers.IO) {
			prevJob.join()
			commit()
		}
	}

	private fun computeEnabled(): List<MangaSource> {
		val languages = locales.value.selectedItems.mapToSet { it.language }
		val types = kinds.value.flatMapTo(HashSet()) { it.types }
		return allSources.filter { source ->
			val type = source.contentTypeOrNull()
			val language = source.getLocale()?.language.orEmpty()
			type != null && type in types && language in languages
		}
	}

	private suspend fun commit() {
		val candidates = computeEnabled()
		val enabled = candidates.filterNot { it in excluded }
		// Never leave the app without a single source: a user who unticks everything
		// must still end up with a working library instead of empty screens
		val safe = enabled.ifEmpty { allSources.filter { it.contentTypeOrNull() == ContentType.MANGA } }
		repository.setSourcesEnabledExclusive(safe.toSet())
		updatePreview(candidates)
	}

	private fun updatePreview(candidates: List<MangaSource>) {
		val byName = candidates.associateBy { it.name }
		val popular = POPULAR.mapNotNull { byName[it] }.ifEmpty { candidates.take(POPULAR_COUNT) }
		popularSources.value = popular.take(POPULAR_COUNT).map { source ->
			val type = source.contentTypeOrNull()
			val language = source.getLocale()?.language?.uppercase().orEmpty()
			PopularSourceModel(
				source = source,
				title = source.getTitle(context),
				subtitle = listOfNotNull(type?.let { context.getString(it.titleResId) }, language.ifEmpty { null })
					.joinToString(" · "),
				isChecked = source !in excluded,
			)
		}
		enabledCount.value = candidates.count { it !in excluded }
	}

	private fun MangaSource.contentTypeOrNull(): ContentType? = when (this) {
		is MangaParserSource -> contentType
		else -> null
	}

	private companion object {

		const val POPULAR_COUNT = 6

		/** Shown first on the languages step when they match the chosen kinds and languages */
		val POPULAR = listOf(
			"READMANGA_RU", "REMANGA", "MANGALIB", "RANOBELIB", "HONEYMANGA", "SENKURO",
			"MINTMANGA", "MANGABUFF", "USAGI",
		)
	}
}
