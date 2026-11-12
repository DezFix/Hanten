package hanten.wre.app.core.model

import androidx.annotation.StringRes
import hanten.wre.app.R

enum class MangaRelationType(
	@StringRes val titleResId: Int,
) {
	SEQUEL(R.string.relation_sequel),
	PREQUEL(R.string.relation_prequel),
	SPIN_OFF(R.string.relation_spin_off),
	ADAPTATION(R.string.relation_adaptation),
	ALTERNATIVE(R.string.relation_alternative),
	SAME_UNIVERSE(R.string.relation_same_universe),
	OTHER(R.string.relation_other),
	;

	/** Relation type as seen from the other side. */
	val reverse: MangaRelationType
		get() = when (this) {
			SEQUEL -> PREQUEL
			PREQUEL -> SEQUEL
			else -> this
		}
}

enum class MangaRelationOrigin {
	MANUAL,
	SOURCE,
}

data class MangaRelation(
	val fromId: Long,
	val toId: Long,
	val type: MangaRelationType,
	val origin: MangaRelationOrigin = MangaRelationOrigin.MANUAL,
	val createdAt: Long = System.currentTimeMillis(),
)
