package hanten.wre.app.explore.ui

import hanten.wre.app.explore.ui.model.ExploreMoreSources
import androidx.collection.LongSet
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hanten.wre.app.R
import hanten.wre.app.core.model.MangaSourceInfo
import hanten.wre.app.core.model.isNovelSource
import hanten.wre.app.core.os.AppShortcutManager
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.observeAsFlow
import hanten.wre.app.core.prefs.observeAsStateFlow
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.ui.util.ReversibleAction
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.call
import hanten.wre.app.core.util.ext.combine
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.explore.domain.ExploreRepository
import hanten.wre.app.explore.ui.model.ExploreButtons
import hanten.wre.app.explore.ui.model.MangaSourceItem
import hanten.wre.app.explore.ui.model.RecommendationsItem
import hanten.wre.app.list.ui.model.EmptyHint
import hanten.wre.app.list.ui.model.ListHeader
import hanten.wre.app.list.ui.model.ListModel
import hanten.wre.app.list.ui.model.LoadingState
import hanten.wre.app.list.ui.model.MangaCompactListModel
import hanten.wre.app.suggestions.domain.SuggestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine as combineFlows
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.util.runCatchingCancellable
import hanten.wre.app.core.model.toChipModel
import hanten.wre.app.core.model.unwrap
import hanten.wre.app.list.domain.ListFilterOption
import hanten.wre.app.list.ui.model.QuickFilter
import hanten.wre.app.mihon.model.MihonMangaSource
import hanten.wre.app.parsers.model.MangaParserSource
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
	private val settings: AppSettings,
	private val suggestionRepository: SuggestionRepository,
	private val exploreRepository: ExploreRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val shortcutManager: AppShortcutManager,
) : BaseViewModel() {

	val isGrid = settings.observeAsStateFlow(
		key = AppSettings.KEY_SOURCES_GRID,
		scope = viewModelScope + Dispatchers.IO,
		valueProducer = { isSourcesGridMode },
	)

	val isAllSourcesEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_SOURCES_ENABLED_ALL,
		valueProducer = { isAllSourcesEnabled },
	)

	private val isSuggestionsEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_SUGGESTIONS,
		valueProducer = { isSuggestionsEnabled },
	)

	val onOpenManga = MutableEventFlow<Manga>()
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onShowSuggestionsTip = MutableEventFlow<Unit>()
	private val isRandomLoading = MutableStateFlow(false)

	val content: StateFlow<List<ListModel>> = isLoading.flatMapLatest { loading ->
		if (loading) {
			flowOf(getLoadingStateList())
		} else {
			createContentFlow()
		}
	}.distinctUntilChanged()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, getLoadingStateList())

	init {
		launchJob(Dispatchers.IO) {
			if (!settings.isSuggestionsEnabled && settings.isTipEnabled(TIP_SUGGESTIONS)) {
				onShowSuggestionsTip.call(Unit)
			}
		}
	}

	fun openRandom() {
		if (isRandomLoading.value) {
			return
		}
		launchJob(Dispatchers.IO) {
			isRandomLoading.value = true
			try {
				val manga = exploreRepository.findRandomManga(tagsLimit = 8)
				onOpenManga.call(manga)
			} finally {
				isRandomLoading.value = false
			}
		}
	}

	fun disableSources(sources: Collection<MangaSource>) {
		launchJob(Dispatchers.IO) {
			val rollback = sourcesRepository.setSourcesEnabled(sources, isEnabled = false)
			val message = if (sources.size == 1) R.string.source_disabled else R.string.sources_disabled
			onActionDone.call(ReversibleAction(message, rollback))
		}
	}

	fun requestPinShortcut(source: MangaSource) {
		launchLoadingJob(Dispatchers.IO) {
			shortcutManager.requestPinShortcut(source)
		}
	}

	fun setSourcesPinned(sources: Collection<MangaSource>, isPinned: Boolean) {
		launchJob(Dispatchers.IO) {
			sourcesRepository.setIsPinned(sources, isPinned)
			val message = if (sources.size == 1) {
				if (isPinned) R.string.source_pinned else R.string.source_unpinned
			} else {
				if (isPinned) R.string.sources_pinned else R.string.sources_unpinned
			}
			onActionDone.call(ReversibleAction(message, null))
		}
	}

	fun respondSuggestionTip(isAccepted: Boolean) {
		settings.isSuggestionsEnabled = isAccepted
		settings.closeTip(TIP_SUGGESTIONS)
	}

	fun sourcesSnapshot(ids: LongSet): List<MangaSourceInfo> {
		return content.value.mapNotNull {
			(it as? MangaSourceItem)?.takeIf { x -> x.id in ids }?.source
		}
	}

	/** Hanten 3.0: Catalog language chips; null = all languages */
	private val selectedLanguage = MutableStateFlow<String?>(null)

	fun toggleLanguage(code: String) {
		selectedLanguage.value = if (selectedLanguage.value == code) null else code
	}

	private class SourcesState(val all: List<MangaSourceInfo>, val language: String?)

	private fun observeFilteredSources(): Flow<SourcesState> = combineFlows(
		sourcesRepository.observeEnabledSources(),
		selectedLanguage,
	) { all, lang -> SourcesState(all, lang) }

	private fun createContentFlow(): Flow<List<ListModel>> = combine(
		observeFilteredSources(),
		getSuggestionFlow(),
		isGrid,
		isRandomLoading,
		isAllSourcesEnabled,
		sourcesRepository.observeHasNewSourcesForBadge(),
	) { sources, suggestions, grid, randomLoading, allSourcesEnabled, newSources ->
		buildList(sources, suggestions, grid, randomLoading, allSourcesEnabled, newSources)
	}.withErrorHandling()

	fun toggleGridMode() {
		settings.isSourcesGridMode = !settings.isSourcesGridMode
	}

	private fun buildList(
		state: SourcesState,
		recommendation: List<Manga>,
		isGrid: Boolean,
		randomLoading: Boolean,
		allSourcesEnabled: Boolean,
		hasNewSources: Boolean,
	): List<ListModel> {
		val languages = state.all.groupBy { it.languageCode() }
		val language = state.language?.takeIf { it in languages }
		val sources = if (language == null) state.all else languages.getValue(language)
		val result = ArrayList<ListModel>(sources.size + 4)
		result += ExploreButtons(randomLoading)
		if (recommendation.isNotEmpty()) {
			result += ListHeader(R.string.suggestions, R.string.more, R.id.nav_suggestions)
			result += RecommendationsItem(recommendation.toRecommendationList())
		}
		if (sources.isNotEmpty()) {
			result += ListHeader(
				textRes = R.string.remote_sources,
				buttonTextRes = if (allSourcesEnabled) R.string.manage else R.string.catalog,
				badge = if (!allSourcesEnabled && hasNewSources) "" else null,
			)
			if (languages.size > 1) {
				result += QuickFilter(
					languages.keys.sortedWith(compareBy<String> { it.isEmpty() }.thenByDescending { languages.getValue(it).size })
						.map { code ->
							ListFilterOption.Language(code, languageTitle(code)).toChipModel(isChecked = code == language)
						},
				)
			}
			sources.mapTo(result) { MangaSourceItem(it, isGrid) }
			result += ExploreMoreSources
		} else {
			result += EmptyHint(
				icon = R.drawable.ic_empty_common,
				textPrimary = R.string.no_manga_sources,
				textSecondary = R.string.no_manga_sources_text,
				actionStringRes = R.string.catalog,
			)
		}
		return result
	}

	private fun MangaSource.languageCode(): String = when (val s = unwrap()) {
		is MangaParserSource -> s.locale
		is MihonMangaSource -> s.locale
		else -> ""
	}.let { if (it == "all" || it == "multi") "" else it }

	/** The language's own name, like in onboarding: "Русский", "Українська" */
	private fun languageTitle(code: String): String? {
		if (code.isEmpty()) return null
		val locale = Locale.forLanguageTag(code)
		return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }.ifEmpty { code }
	}

	private fun getLoadingStateList() = listOf(
		ExploreButtons(isRandomLoading.value),
		LoadingState,
	)

	private fun getSuggestionFlow() = isSuggestionsEnabled.mapLatest { isEnabled ->
		if (isEnabled) {
			runCatchingCancellable {
				suggestionRepository.getRandomList(SUGGESTIONS_COUNT)
			}.getOrDefault(emptyList())
		} else {
			emptyList()
		}
	}

	private fun List<Manga>.toRecommendationList() = skipBlacklistedTags().map { manga ->
		MangaCompactListModel(
			manga = manga,
			override = null,
			subtitle = manga.tags.joinToString { it.title },
			counter = 0,
		)
	}

	private fun List<Manga>.skipBlacklistedTags(): List<Manga> {
		val blacklist = settings.tagsBlacklist
		if (blacklist.isEmpty()) {
			return this
		}
		return filterNot { manga ->
			manga.tags.any { it.title.lowercase() in blacklist }
		}
	}

	companion object {

		private const val TIP_SUGGESTIONS = "suggestions"
		private const val SUGGESTIONS_COUNT = 8
	}
}
