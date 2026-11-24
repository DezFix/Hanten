package hanten.wre.app.profile.domain

import androidx.annotation.StringRes
import hanten.wre.app.R

/**
 * Level from XP. Reaching level L needs 40 × (L − 1)^2.5 XP in total:
 * 2 → 40, 5 → 1 280, 10 → 9 720, 20 → 62 900, 30 → 181 000, 40 → 380 000, 50 → 672 000.
 * A reader with ~26 000 chapters lands around level 35; the Legend rank stays a long-term goal.
 */
data class ReaderLevel(
	val level: Int,
	val xp: Long,
	val levelStartXp: Long,
	val nextLevelXp: Long,
) {

	val progress: Float
		get() = if (nextLevelXp <= levelStartXp) 1f else
			((xp - levelStartXp).toFloat() / (nextLevelXp - levelStartXp)).coerceIn(0f, 1f)

	val rank: Rank
		get() = Rank.entries.last { level >= it.fromLevel }

	val frame: Frame
		get() = Frame.entries.last { level >= it.fromLevel }

	val isMax: Boolean
		get() = level >= MAX_LEVEL

	/** Titles (титулы) unlocked every few levels. */
	enum class Rank(val fromLevel: Int, @StringRes val titleResId: Int) {
		NOVICE(1, R.string.rank_novice),
		READER(3, R.string.rank_reader),
		BOOKWORM(6, R.string.rank_bookworm),
		CONNOISSEUR(10, R.string.rank_connoisseur),
		OTAKU(15, R.string.rank_otaku),
		SENPAI(20, R.string.rank_senpai),
		SENSEI(30, R.string.rank_sensei),
		SCROLL_MASTER(40, R.string.rank_scroll_master),
		LEGEND(50, R.string.rank_legend),
	}

	/** Avatar frame: plain until level 10, then bronze, silver, gold, sakura, aurora. */
	enum class Frame(val fromLevel: Int, val colors: IntArray?) {
		NONE(1, null),
		BRONZE(10, intArrayOf(0xFFB87333.toInt(), 0xFFE3A869.toInt(), 0xFF8C5523.toInt())),
		SILVER(20, intArrayOf(0xFFB8C2CC.toInt(), 0xFFF2F5F7.toInt(), 0xFF8E9AA6.toInt())),
		GOLD(30, intArrayOf(0xFFD4A017.toInt(), 0xFFFFE08A.toInt(), 0xFFB8860B.toInt())),
		SAKURA(40, intArrayOf(0xFFF48FB1.toInt(), 0xFFFCE4EC.toInt(), 0xFFE91E63.toInt())),
		AURORA(50, intArrayOf(0xFF7C4DFF.toInt(), 0xFF00BFA5.toInt(), 0xFFFFD740.toInt(), 0xFFFF4081.toInt())),
	}

	companion object {

		const val MAX_LEVEL = 99

		fun xpForLevel(level: Int): Long = (40.0 * Math.pow((level - 1).toDouble(), 2.5)).toLong()

		fun fromXp(xp: Long): ReaderLevel {
			var level = 1
			while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) {
				level++
			}
			return ReaderLevel(
				level = level,
				xp = xp,
				levelStartXp = xpForLevel(level),
				nextLevelXp = if (level >= MAX_LEVEL) xpForLevel(level) else xpForLevel(level + 1),
			)
		}
	}
}
