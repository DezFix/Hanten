package hanten.wre.app.profile

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import hanten.wre.app.R
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.databinding.ActivityProfileBinding
import hanten.wre.app.databinding.ItemAchievementBinding
import hanten.wre.app.databinding.ItemShowcaseSlotBinding
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.profile.domain.Achievement
import hanten.wre.app.profile.domain.ReaderLevel
import hanten.wre.app.profile.domain.ReadingProgressStats
import java.text.NumberFormat
import com.google.android.material.R as materialR

/** Hanten 3.0: level card, avatar frame, showcase and achievements on the profile screen. */
class ProfileProgressBinder(
	private val binding: ActivityProfileBinding,
	private val onShowcaseClick: (Manga) -> Unit,
	private val onShowcaseLongClick: (Manga) -> Unit,
) {

	private val context: Context
		get() = binding.root.context

	private val numbers = NumberFormat.getIntegerInstance()

	fun bindLevel(stats: ReadingProgressStats) {
		val level = ReaderLevel.fromXp(stats.xp)
		val rank = context.getString(level.rank.titleResId)
		binding.textRank.text = context.getString(R.string.level_short, level.level, rank)
		binding.textLevel.text = context.getString(R.string.level_title, level.level)
		binding.textLevelRank.text = rank
		binding.progressLevel.setProgressCompat((level.progress * 1000).toInt(), true)
		binding.progressLevel.max = 1000
		binding.textLevelXp.text = if (level.isMax) {
			context.getString(R.string.level_xp_max, numbers.format(level.xp))
		} else {
			context.getString(
				R.string.level_xp_line,
				numbers.format(level.xp - level.levelStartXp),
				numbers.format(level.nextLevelXp - level.levelStartXp),
				level.level + 1,
			)
		}
		val nextRank = ReaderLevel.Rank.entries.firstOrNull { it.fromLevel > level.level }
		binding.textLevelNext.isVisible = nextRank != null
		if (nextRank != null) {
			binding.textLevelNext.text = context.getString(
				R.string.level_next_rank,
				context.getString(nextRank.titleResId),
				nextRank.fromLevel,
			)
		}
		bindFrame(level.frame)
	}

	/** The ring around the avatar: background-colored until level 10, then metal / sakura / aurora gradients. */
	private fun bindFrame(frame: ReaderLevel.Frame) {
		val colors = frame.colors
		binding.frameAvatar.background = if (colors == null) {
			GradientDrawable().apply {
				shape = GradientDrawable.OVAL
				setColor(context.getThemeColor(android.R.attr.colorBackground))
			}
		} else {
			GradientDrawable(GradientDrawable.Orientation.TL_BR, colors + colors.first()).apply {
				shape = GradientDrawable.OVAL
				gradientType = GradientDrawable.SWEEP_GRADIENT
			}
		}
	}

	fun bindAchievements(stats: ReadingProgressStats) {
		val container = binding.layoutAchievements
		container.removeAllViews()
		val all = Achievement.entries.sortedByDescending { it.isUnlocked(stats) }
		val unlocked = all.count { it.isUnlocked(stats) }
		binding.textAchievementsTitle.text = context.getString(R.string.achievements_title, unlocked, all.size)
		val inflater = LayoutInflater.from(context)
		val gap = context.resources.getDimensionPixelSize(R.dimen.margin_small)
		all.chunked(2).forEach { pair ->
			val row = LinearLayout(context).apply {
				orientation = LinearLayout.HORIZONTAL
				isBaselineAligned = false
			}
			pair.forEachIndexed { index, achievement ->
				val item = ItemAchievementBinding.inflate(inflater, row, false)
				val isUnlocked = achievement.isUnlocked(stats)
				item.textIcon.text = achievement.icon
				item.textTitle.setText(achievement.titleResId)
				item.textDescription.setText(achievement.descriptionResId)
				item.progress.max = 1000
				item.progress.progress = (achievement.current(stats) * 1000 / achievement.target).toInt()
				item.progress.isVisible = !isUnlocked
				item.textProgress.text = if (isUnlocked) {
					context.getString(R.string.achievement_done)
				} else {
					context.getString(
						R.string.achievement_progress,
						numbers.format(achievement.current(stats)),
						numbers.format(achievement.target),
					)
				}
				item.root.alpha = if (isUnlocked) 1f else LOCKED_ALPHA
				if (isUnlocked) {
					item.root.setCardBackgroundColor(context.getThemeColor(materialR.attr.colorSecondaryContainer))
					item.textProgress.setTextColor(context.getThemeColor(materialR.attr.colorOnSecondaryContainer))
				}
				row.addView(item.root)
				if (index == 0) {
					item.root.updateLayoutParams<LinearLayout.LayoutParams> { marginEnd = gap / 2 }
				} else {
					item.root.updateLayoutParams<LinearLayout.LayoutParams> { marginStart = gap / 2 }
				}
			}
			if (pair.size == 1) {
				row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = gap / 2 })
			}
			container.addView(
				row,
				LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
					.apply { bottomMargin = gap },
			)
		}
	}

	fun bindShowcase(titles: List<Manga>) {
		val container = binding.layoutShowcase
		container.removeAllViews()
		val inflater = LayoutInflater.from(context)
		val gap = context.resources.getDimensionPixelSize(R.dimen.margin_small)
		for (i in 0 until SHOWCASE_SIZE) {
			val manga = titles.getOrNull(i)
			val slot = ItemShowcaseSlotBinding.inflate(inflater, container, false)
			slot.textEmpty.isVisible = manga == null
			slot.textTitle.text = manga?.title.orEmpty()
			slot.textTitle.isVisible = manga != null
			if (manga != null) {
				slot.imageCover.setImageAsync(manga.coverUrl, manga)
				slot.root.setOnClickListener { onShowcaseClick(manga) }
				slot.root.setOnLongClickListener {
					onShowcaseLongClick(manga)
					true
				}
			} else {
				slot.imageCover.setImageDrawable(null)
				slot.imageCover.setBackgroundColor(Color.TRANSPARENT)
			}
			container.addView(slot.root)
			slot.root.updateLayoutParams<LinearLayout.LayoutParams> {
				if (i > 0) marginStart = gap
			}
		}
		binding.textShowcaseHint.setText(
			if (titles.isEmpty()) R.string.showcase_hint else R.string.showcase_hint_filled,
		)
	}

	companion object {

		const val SHOWCASE_SIZE = 3
		private const val LOCKED_ALPHA = 0.55f
	}
}
