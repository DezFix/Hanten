package hanten.wre.app.details.domain

import dagger.Reusable
import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.core.parser.MangaRepository
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.SortOrder
import hanten.wre.app.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Collects possible series links from the local library and from the source.
 *
 * The detector never writes to the database: the result is only a hint for the details
 * screen. "Same universe" is not claimed here; author matches are shown separately.
 */
@Reusable
class SeriesDetector @Inject constructor(
	private val dataRepository: MangaDataRepository,
	private val repositoryFactory: MangaRepository.Factory,
	private val relatedMangaUseCase: RelatedMangaUseCase,
) {

	suspend fun detect(manga: Manga): SeriesInfo {
		val candidates = LinkedHashMap<Long, SeriesClassifier.Candidate>(16)
		val base = SeriesTitle.baseOf(manga)
		val currentPart = SeriesTitle.partOf(manga)
		if (base.isNotBlank()) {
			dataRepository.findSimilarByTitle("%$base%", manga.id, LOCAL_LIMIT).forEach {
				candidates[it.id] = SeriesClassifier.Candidate(it, fromSource = false)
			}
		}
		for (author in manga.authors) {
			if (author.isBlank()) continue
			dataRepository.findByAuthor("%$author%", manga.id, LOCAL_LIMIT).forEach {
				candidates.putIfAbsent(it.id, SeriesClassifier.Candidate(it, fromSource = false))
			}
		}
		relatedMangaUseCase(manga).orEmpty().forEach {
			candidates[it.id] = SeriesClassifier.Candidate(it, fromSource = true)
		}
		// If the title has a part number, or nothing was found yet, ask the source
		// itself: the continuation may not be in the user's library yet.
		if (base.isNotBlank() && (currentPart != null || candidates.isEmpty())) {
			val repository = repositoryFactory.create(manga.source)
			runCatchingCancellable {
				repository.getList(
					offset = 0,
					order = SortOrder.RELEVANCE,
					filter = MangaListFilter(query = base),
				)
			}.getOrDefault(emptyList())
				.asSequence()
				.filter { it.id != manga.id }
				.take(SOURCE_SEARCH_LIMIT)
				.forEach {
					candidates.putIfAbsent(it.id, SeriesClassifier.Candidate(it, fromSource = false))
				}
		}
		return SeriesClassifier.classify(manga, candidates.values.toList())
	}

	private companion object {
		private const val LOCAL_LIMIT = 30
		private const val SOURCE_SEARCH_LIMIT = 20
	}
}
