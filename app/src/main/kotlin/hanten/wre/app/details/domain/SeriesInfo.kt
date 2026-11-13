package hanten.wre.app.details.domain

import hanten.wre.app.parsers.model.Manga

data class SeriesLink(
	val manga: Manga,
	val part: Int?,
	val fromSource: Boolean,
)

/**
 * Automatically detected series information for the details screen.
 *
 * [related] contains explicit source relations and same-base titles that cannot be
 * ordered by part number. [sameAuthor] is a softer hint and must not be presented as
 * a confirmed continuation.
 */
data class SeriesInfo(
	val currentPart: Int?,
	val prequels: List<SeriesLink>,
	val continuations: List<SeriesLink>,
	val related: List<SeriesLink>,
	val sameAuthor: List<SeriesLink>,
) {

	val isEmpty: Boolean
		get() = prequels.isEmpty() &&
			continuations.isEmpty() &&
			related.isEmpty() &&
			sameAuthor.isEmpty()
}
