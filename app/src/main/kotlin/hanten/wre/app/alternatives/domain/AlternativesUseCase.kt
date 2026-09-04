package hanten.wre.app.alternatives.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.core.util.ext.toLocale
import hanten.wre.app.explore.data.MangaSourcesRepository
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaParserSource
import hanten.wre.app.parsers.model.MangaSource
import hanten.wre.app.parsers.util.runCatchingCancellable
import hanten.wre.app.search.domain.SearchKind
import hanten.wre.app.search.domain.SearchV2Helper
import java.util.Locale
import javax.inject.Inject

private const val MAX_PARALLELISM = 4
private const val MAX_QUERIES = 5
private const val MAX_RESULTS_PER_SOURCE = 10

class AlternativesUseCase @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend operator fun invoke(manga: Manga, throughDisabledSources: Boolean): Flow<Manga> {
		val sources = getSources(manga.source, throughDisabledSources)
		if (sources.isEmpty()) {
			return emptyFlow()
		}
		val queries = (listOf(manga.title) + manga.altTitles)
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.distinct()
			.take(MAX_QUERIES)
		val semaphore = Semaphore(MAX_PARALLELISM)
		return channelFlow {
			for (source in sources) {
				launch {
					val searchHelper = searchHelperFactory.create(source)
					val found = linkedMapOf<Long, Manga>()
					runCatchingCancellable {
						semaphore.withPermit {
							for (query in queries) {
								val list = searchHelper(query, SearchKind.TITLE)?.manga
								if (list.isNullOrEmpty()) continue
								list.forEach { found.putIfAbsent(it.id, it) }
								if (found.size >= MAX_RESULTS_PER_SOURCE) break
							}
						}
					}.getOrNull()
					found.values.forEach { m ->
						if (m.id != manga.id) {
							launch {
								val details = runCatchingCancellable {
									mangaRepositoryFactory.create(m.source).getDetails(m)
								}.getOrDefault(m)
								send(details)
							}
						}
					}
				}
			}
		}
	}

	private suspend fun getSources(ref: MangaSource, disabled: Boolean): List<MangaSource> = if (disabled) {
		sourcesRepository.getDisabledSources()
	} else {
		sourcesRepository.getEnabledSources()
	}.sortedByDescending { it.priority(ref) }

	private fun MangaSource.priority(ref: MangaSource): Int {
		var res = 0
		if (this is MangaParserSource && ref is MangaParserSource) {
			if (locale == ref.locale) {
				res += 4
			} else if (locale.toLocale() == Locale.getDefault()) {
				res += 2
			}
			if (contentType == ref.contentType) {
				res++
			}
		}
		return res
	}
}
