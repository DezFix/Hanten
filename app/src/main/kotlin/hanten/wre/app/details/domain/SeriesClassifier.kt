package hanten.wre.app.details.domain

import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.util.levenshteinDistance
import java.util.Locale

object SeriesClassifier {

	data class Candidate(
		val manga: Manga,
		val fromSource: Boolean,
	)

	private const val MAX_LINKS = 8
	private const val MAX_BASE_DISTANCE = 1

	fun classify(current: Manga, candidates: List<Candidate>): SeriesInfo {
		val currentBase = SeriesTitle.baseOf(current)
		val currentPart = SeriesTitle.partOf(current)
		val currentAuthors = current.authors.mapTo(HashSet()) { it.normalizedAuthor() }
		val seen = HashSet<Long>(candidates.size)
		val prequels = ArrayList<SeriesLink>()
		val continuations = ArrayList<SeriesLink>()
		val related = ArrayList<SeriesLink>()
		val sameAuthor = ArrayList<SeriesLink>()

		for (candidate in candidates) {
			val manga = candidate.manga
			if (manga.id == current.id || !seen.add(manga.id)) continue
			val base = SeriesTitle.baseOf(manga)
			val part = SeriesTitle.partOf(manga)
			val sameBase = currentBase.isNotBlank() &&
				base.isNotBlank() &&
				(base == currentBase || isCloseTitle(base, currentBase))
			val authorMatch = currentAuthors.isNotEmpty() &&
				manga.authors.any { it.normalizedAuthor() in currentAuthors }
			val link = SeriesLink(manga = manga, part = part, fromSource = candidate.fromSource)

			when {
				sameBase && currentPart != null && part != null && part == currentPart -> Unit

				sameBase && currentPart != null && part != null && part > currentPart ->
					continuations += link

				sameBase && currentPart != null && part != null && part < currentPart ->
					prequels += link

				// Current title has no number, but the candidate is "2", "3", ...
				sameBase && currentPart == null && part != null && part > 1 ->
					continuations += link

				// Candidate title has no number and the current one is "2", "3", ...
				sameBase && currentPart != null && part == null && currentPart > 1 ->
					prequels += link

				sameBase && currentPart == null && part != null && part == 1 -> Unit

				sameBase -> related += link
				authorMatch -> sameAuthor += link
				candidate.fromSource -> related += link
			}
		}

		return SeriesInfo(
			currentPart = currentPart,
			prequels = prequels.sortedLinks(),
			continuations = continuations.sortedLinks(),
			related = related.sortedLinks(),
			sameAuthor = sameAuthor.sortedLinks(),
		)
	}

	private fun List<SeriesLink>.sortedLinks(): List<SeriesLink> {
		return sortedWith(
			compareBy<SeriesLink> { it.part ?: Int.MAX_VALUE }
				.thenBy { it.manga.title.lowercase(Locale.ROOT) },
		).take(MAX_LINKS)
	}

	private fun isCloseTitle(first: String, second: String): Boolean {
		if (minOf(first.length, second.length) < 4) return false
		return first.levenshteinDistance(second) <= MAX_BASE_DISTANCE
	}

	private fun String.normalizedAuthor(): String {
		return SeriesTitle.normalize(this)
	}
}
