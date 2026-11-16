package hanten.wre.app.scrobbling.common.domain.model

/**
 * A work related to a tracked title (sequel, prequel, spin-off, adaptation...),
 * as reported by the tracker itself. Unlike title matching, these links are curated
 * on the tracker side, so they are safe to show as "reading order".
 */
data class ScrobblerRelated(
	val relation: String,
	val title: String,
	val url: String,
	val isManga: Boolean,
)
