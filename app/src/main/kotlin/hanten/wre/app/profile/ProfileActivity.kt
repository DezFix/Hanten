package hanten.wre.app.profile

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hanten.wre.app.R
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.util.ext.consumeAllSystemBarsInsets
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.systemBarsInsets
import hanten.wre.app.databinding.ActivityProfileBinding
import hanten.wre.app.favourites.domain.FavouritesRepository
import hanten.wre.app.history.data.HistoryRepository
import hanten.wre.app.parsers.util.runCatchingCancellable
import hanten.wre.app.stats.data.StatsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Local profile (Hanten 3.0 mockup): banner and avatar in the profile color, nickname, stats from this device
 * and favourite genres from the reading history. Levels and achievements come later.
 */
@AndroidEntryPoint
class ProfileActivity : BaseActivity<ActivityProfileBinding>(), View.OnClickListener {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var statsRepository: StatsRepository

	@Inject
	lateinit var historyRepository: HistoryRepository

	@Inject
	lateinit var favouritesRepository: FavouritesRepository

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityProfileBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.title = null
		viewBinding.buttonEdit.setOnClickListener(this)
		viewBinding.buttonName.setOnClickListener(this)
		viewBinding.buttonStats.setOnClickListener(this)
		viewBinding.statHours.textLabel.setText(R.string.profile_stat_hours)
		viewBinding.statPages.textLabel.setText(R.string.profile_stat_pages)
		viewBinding.statTitles.textLabel.setText(R.string.profile_stat_titles)
		favouritesRepository.observeMangaCount().observe(this) {
			viewBinding.statTitles.textValue.text = it.toString()
		}
		bindProfile()
		loadStats()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.appbar.updatePadding(left = barsInsets.left, right = barsInsets.right, top = barsInsets.top)
		// the banner goes under the status bar, so the scroll content gets no top padding
		viewBinding.scrollView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		viewBinding.viewBanner.layoutParams = viewBinding.viewBanner.layoutParams.apply {
			height = resources.getDimensionPixelSize(R.dimen.profile_banner_height) + barsInsets.top
		}
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_edit -> {
				val isEditing = !viewBinding.layoutEdit.isVisible
				viewBinding.layoutEdit.isVisible = isEditing
				viewBinding.buttonEdit.setText(if (isEditing) R.string.profile_done else R.string.profile_edit)
			}

			R.id.button_name -> showNameDialog()
			R.id.button_stats -> router.openStatistic()
		}
	}

	private fun bindProfile() {
		val name = settings.profileName
		val color = ProfileStyle.color(settings.profileColor)
		viewBinding.textName.text = name ?: getString(R.string.profile_no_name)
		viewBinding.textAvatar.text = ProfileStyle.avatarText(settings.profileAvatar, name)
		viewBinding.textAvatar.backgroundTintList = ColorStateList.valueOf(color)
		viewBinding.viewBanner.setBackgroundColor(ColorUtils.setAlphaComponent(color, BANNER_ALPHA))
		viewBinding.layoutEdit.strokeColor = color
		bindAvatarChoices(name, color)
		bindColorChoices()
	}

	private fun bindAvatarChoices(name: String?, color: Int) {
		val container = viewBinding.layoutAvatars
		container.removeAllViews()
		val size = resources.getDimensionPixelSize(R.dimen.profile_choice_size)
		val gap = resources.getDimensionPixelOffset(R.dimen.margin_small)
		val ringColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary)
		ProfileStyle.avatars.indices.forEach { index ->
			val isSelected = index == settings.profileAvatar
			val view = TextView(this).apply {
				text = ProfileStyle.avatarText(index, name)
				gravity = Gravity.CENTER
				textSize = AVATAR_TEXT_SP
				setTextColor(android.graphics.Color.WHITE)
				background = circle(color, if (isSelected) ringColor else 0)
				contentDescription = text
				setOnClickListener {
					settings.profileAvatar = index
					bindProfile()
				}
			}
			container.addView(
				view,
				LinearLayout.LayoutParams(size, size).apply { marginEnd = gap },
			)
		}
	}

	private fun bindColorChoices() {
		val container = viewBinding.layoutColors
		container.removeAllViews()
		val size = resources.getDimensionPixelSize(R.dimen.profile_choice_size)
		val gap = resources.getDimensionPixelOffset(R.dimen.margin_small)
		val ringColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
		ProfileStyle.colors.forEachIndexed { index, color ->
			val isSelected = index == settings.profileColor
			val view = View(this).apply {
				background = circle(color, if (isSelected) ringColor else 0)
				setOnClickListener {
					settings.profileColor = index
					bindProfile()
				}
			}
			container.addView(
				view,
				LinearLayout.LayoutParams(size, size).apply { marginEnd = gap },
			)
		}
	}

	private fun circle(fill: Int, ring: Int) = GradientDrawable().apply {
		shape = GradientDrawable.OVAL
		setColor(fill)
		if (ring != 0) {
			setStroke(resources.getDimensionPixelSize(R.dimen.profile_choice_ring), ring)
		}
	}

	private fun loadStats() {
		lifecycleScope.launch {
			val totals = runCatchingCancellable {
				withContext(Dispatchers.IO) { statsRepository.getPeriodTotals(0L, emptySet()) }
			}.onFailure { it.printStackTraceDebug() }.getOrNull()
			val hours = TimeUnit.MILLISECONDS.toHours(totals?.duration ?: 0L)
			viewBinding.statHours.textValue.text = hours.toString()
			viewBinding.statPages.textValue.text = (totals?.pages ?: 0).toString()

			val tags = runCatchingCancellable {
				withContext(Dispatchers.IO) { historyRepository.getPopularTags(GENRES_LIMIT) }
			}.onFailure { it.printStackTraceDebug() }.getOrNull().orEmpty()
			val chips = viewBinding.chipsGenres
			chips.removeAllViews()
			for (tag in tags) {
				chips.addView(
					Chip(this@ProfileActivity).apply {
						text = tag.title
						isClickable = false
						// filled chips like in the mockup
						chipBackgroundColor = ColorStateList.valueOf(
							getThemeColor(com.google.android.material.R.attr.colorPrimaryContainer),
						)
						setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
						chipStrokeWidth = 0f
					},
				)
			}
			val hasGenres = tags.isNotEmpty()
			viewBinding.textGenresTitle.isVisible = hasGenres
			chips.isVisible = hasGenres
			viewBinding.textGenresHint.isVisible = hasGenres
		}
	}

	private fun showNameDialog() {
		val input = TextInputEditText(this).apply {
			setText(settings.profileName)
			hint = getString(R.string.profile_name_hint)
			filters = arrayOf(InputFilter.LengthFilter(MAX_NAME_LENGTH))
			setSingleLine()
		}
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.onboarding_profile_title)
			.setView(input)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				settings.profileName = input.text?.toString()
				bindProfile()
			}
			.show()
	}

	private companion object {

		const val BANNER_ALPHA = 200
		const val AVATAR_TEXT_SP = 18f
		const val GENRES_LIMIT = 6
		const val MAX_NAME_LENGTH = 24
	}
}
