package hanten.wre.app.profile

import hanten.wre.app.core.parser.MangaDataRepository
import hanten.wre.app.profile.domain.ProfileProgressRepository
import hanten.wre.app.profile.domain.ReadingProgressStats
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.Menu
import android.widget.CompoundButton
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isGone
import com.google.android.material.chip.ChipDrawable
import hanten.wre.app.core.model.FavouriteCategory
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.ui.BaseListAdapter
import hanten.wre.app.core.ui.dialog.buildAlertDialog
import hanten.wre.app.core.ui.list.OnListItemClickListener
import hanten.wre.app.core.ui.util.ReversibleActionObserver
import hanten.wre.app.core.util.HantenColors
import hanten.wre.app.core.util.ext.observeEvent
import hanten.wre.app.details.data.ReadingTime
import hanten.wre.app.list.ui.adapter.ListItemType
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.stats.domain.StatsPeriod
import hanten.wre.app.stats.domain.StatsRecord
import hanten.wre.app.stats.ui.StatsViewModel
import hanten.wre.app.stats.ui.statsAD
import hanten.wre.app.stats.ui.views.PieChartView
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
class ProfileActivity : BaseActivity<ActivityProfileBinding>(),
	View.OnClickListener,
	OnListItemClickListener<Manga>,
	PieChartView.OnSegmentClickListener,
	CompoundButton.OnCheckedChangeListener {

	private val statsViewModel: StatsViewModel by viewModels()

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var statsRepository: StatsRepository

	@Inject
	lateinit var historyRepository: HistoryRepository

	@Inject
	lateinit var favouritesRepository: FavouritesRepository

	@Inject
	lateinit var progressRepository: ProfileProgressRepository

	@Inject
	lateinit var mangaDataRepository: MangaDataRepository

	private lateinit var progressBinder: ProfileProgressBinder

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityProfileBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.title = null
		viewBinding.buttonEdit.setOnClickListener(this)
		viewBinding.buttonName.setOnClickListener(this)
		initStats()
		viewBinding.statTitles.textLabel.setText(R.string.profile_stat_titles)
		favouritesRepository.observeMangaCount().observe(this) {
			viewBinding.statTitles.textValue.text = it.toString()
		}
		progressBinder = ProfileProgressBinder(
			binding = viewBinding,
			onShowcaseClick = { manga -> router.openDetails(manga) },
			onShowcaseLongClick = ::confirmShowcaseRemove,
		)
		bindProfile()
		loadStats()
		// the bar floats over the banner; once the banner scrolls away it gets a solid fill so text does not show under it
		val barColor = getThemeColor(android.R.attr.colorBackground)
		viewBinding.scrollView.setOnScrollChangeListener(androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
			val range = (viewBinding.appbar.height * 2).coerceAtLeast(1)
			val alpha = (scrollY.toFloat() / range).coerceIn(0f, 1f)
			viewBinding.appbar.setBackgroundColor(ColorUtils.setAlphaComponent(barColor, (alpha * 255).toInt()))
		})
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
			R.id.chip_period -> showPeriodSelector()
			R.id.chip_type -> showTypeSelector()
			R.id.button_stats_clear -> showClearStatsDialog()
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

	override fun onResume() {
		super.onResume()
		// levels, achievements and the showcase can change after reading or editing a title card
		loadProgress()
	}

	private fun loadProgress() {
		lifecycleScope.launch {
			val stats = runCatchingCancellable { progressRepository.load() }
				.onFailure { it.printStackTraceDebug() }
				.getOrDefault(ReadingProgressStats.EMPTY)
			progressBinder.bindLevel(stats)
			progressBinder.bindAchievements(stats)
			val showcase = runCatchingCancellable {
				withContext(Dispatchers.IO) {
					settings.profileShowcase.mapNotNull { id -> mangaDataRepository.findMangaById(id, withChapters = false) }
				}
			}.onFailure { it.printStackTraceDebug() }.getOrNull().orEmpty()
			progressBinder.bindShowcase(showcase)
		}
	}

	private fun confirmShowcaseRemove(manga: Manga) {
		com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
			.setTitle(manga.title)
			.setMessage(R.string.showcase_remove_question)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.showcase_remove) { _, _ ->
				settings.profileShowcase = settings.profileShowcase - manga.id
				loadProgress()
			}
			.show()
	}

	private fun loadStats() {
		lifecycleScope.launch {
			val totals = runCatchingCancellable {
				withContext(Dispatchers.IO) { statsRepository.getPeriodTotals(0L, emptySet()) }
			}.onFailure { it.printStackTraceDebug() }.getOrNull()
			val hours = TimeUnit.MILLISECONDS.toHours(totals?.duration ?: 0L)
			val pages = totals?.pages ?: 0
			viewBinding.statHours.textValue.text = hours.toString()
			viewBinding.statHours.textLabel.text = resources.getQuantityString(R.plurals.profile_stat_hours, hours.toInt())
			viewBinding.statPages.textValue.text = pages.toString()
			viewBinding.statPages.textLabel.text = resources.getQuantityString(R.plurals.profile_stat_pages, pages.toInt())

			val tags = runCatchingCancellable {
				withContext(Dispatchers.IO) { historyRepository.getPopularTags(GENRES_LIMIT * 3) }
			}.onFailure { it.printStackTraceDebug() }.getOrNull().orEmpty()
				// the same genre comes from several sources as separate tags: show it once
				.distinctBy { it.title.trim().lowercase() }
				.take(GENRES_LIMIT)
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

	// region Reading statistics (moved here from the separate Statistics screen)

	private fun initStats() {
		val stats = viewBinding.stats
		val adapter = BaseListAdapter<StatsRecord>()
			.addDelegate(ListItemType.FEED, statsAD(this))
		stats.recyclerStats.adapter = adapter
		stats.chartStats.onSegmentClickListener = this
		stats.chipPeriod.setOnClickListener(this)
		stats.chipType.setOnClickListener(this)
		stats.buttonStatsClear.setOnClickListener(this)
		statsViewModel.isLoading.observe(this) { stats.progressStats.isVisible = it }
		statsViewModel.period.observe(this) { stats.chipPeriod.setText(it.titleResId) }
		statsViewModel.byGenre.observe(this) {
			stats.chipType.setText(if (it) R.string.genres else R.string.manga)
		}
		statsViewModel.favoriteCategories.observe(this, ::createCategoriesChips)
		statsViewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.scrollView))
		statsViewModel.readingStats.observe(this) { records ->
			val sum = records.sumOf { it.duration }
			stats.chartStats.setData(
				records.map { v ->
					PieChartView.Segment(
						value = (v.duration / 1000).toInt(),
						label = v.manga?.title ?: v.tagName ?: getString(R.string.other_manga),
						percent = if (sum > 0) (v.duration.toDouble() / sum).toFloat() else 0f,
						color = HantenColors.ofManga(this, v.manga),
						tag = v.manga,
					)
				},
			)
			adapter.emit(records)
			val isEmpty = records.isEmpty()
			stats.chartStats.isGone = isEmpty
			stats.recyclerStats.isGone = isEmpty
			stats.textStatsEmpty.isVisible = isEmpty
			refreshStatsSummary()
		}
		statsViewModel.summary.observe(this) { refreshStatsSummary() }
		if (intent.getBooleanExtra(AppRouter.KEY_SCROLL_TO_STATS, false)) {
			viewBinding.scrollView.post {
				viewBinding.scrollView.smoothScrollTo(0, viewBinding.stats.root.top)
			}
		}
	}

	private fun refreshStatsSummary() {
		val stats = viewBinding.stats
		val summary = statsViewModel.summary.value
		val hasRecords = statsViewModel.readingStats.value.isNotEmpty()
		if (summary == null || !hasRecords || (summary.durationMs == 0L && summary.pages == 0)) {
			stats.layoutStatsSummary.isGone = true
			return
		}
		stats.layoutStatsSummary.isGone = false
		val minutes = TimeUnit.MILLISECONDS.toMinutes(summary.durationMs).toInt()
		stats.textStatsTime.text = ReadingTime(minutes = minutes % 60, hours = minutes / 60, isContinue = false)
			.formatShort(resources) ?: getString(R.string.less_than_minute_short)
		stats.textStatsPages.text = summary.pages.toString()
		val showDaily = summary.days > 0 && summary.days < Int.MAX_VALUE
		stats.layoutStatsDaily.isGone = !showDaily
		if (showDaily) {
			stats.textStatsDaily.text = if (summary.pages < summary.days) {
				getString(R.string.less_than_one_short)
			} else {
				(summary.pages / summary.days).toString()
			}
		}
	}

	override fun onItemClick(item: Manga, view: View) {
		router.showStatisticSheet(item)
	}

	override fun onSegmentClick(view: PieChartView, segment: PieChartView.Segment) {
		val manga = segment.tag as? Manga ?: return
		onItemClick(manga, view)
	}

	override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
		val category = buttonView.tag as? FavouriteCategory ?: return
		statsViewModel.setCategoryChecked(category, isChecked)
	}

	private fun createCategoriesChips(categories: List<FavouriteCategory>) {
		val container = viewBinding.stats.layoutStatsChips
		if (container.childCount > 2) return // period + type chips are already there
		val checkedIds = statsViewModel.selectedCategories.value
		for (category in categories) {
			val chip = Chip(this)
			chip.setChipDrawable(
				ChipDrawable.createFromAttributes(this, null, 0, R.style.Widget_Hanten_Chip_Filter),
			)
			chip.text = category.title
			chip.tag = category
			chip.isChecked = category.id in checkedIds
			chip.setOnCheckedChangeListener(this)
			container.addView(chip)
		}
	}

	private fun showClearStatsDialog() {
		buildAlertDialog(this, isCentered = true) {
			setMessage(R.string.clear_stats_confirm)
			setTitle(R.string.clear_stats)
			setIcon(R.drawable.ic_delete_all)
			setNegativeButton(android.R.string.cancel, null)
			setPositiveButton(R.string.clear) { _, _ -> statsViewModel.clearStats() }
		}.show()
	}

	private fun showPeriodSelector() {
		val menu = PopupMenu(this, viewBinding.stats.chipPeriod)
		val selected = statsViewModel.period.value
		for ((i, branch) in StatsPeriod.entries.withIndex()) {
			val item = menu.menu.add(R.id.group_period, Menu.NONE, i, branch.titleResId)
			item.isCheckable = true
			item.isChecked = selected.ordinal == i
		}
		menu.menu.setGroupCheckable(R.id.group_period, true, true)
		menu.setOnMenuItemClickListener { item ->
			StatsPeriod.entries.getOrNull(item.order)?.also { statsViewModel.period.value = it } != null
		}
		menu.show()
	}

	private fun showTypeSelector() {
		val menu = PopupMenu(this, viewBinding.stats.chipType)
		val byGenre = statsViewModel.byGenre.value
		val group = R.id.group_period + 1
		menu.menu.add(group, Menu.NONE, 0, R.string.manga).apply {
			isCheckable = true
			isChecked = !byGenre
		}
		menu.menu.add(group, Menu.NONE, 1, R.string.genres).apply {
			isCheckable = true
			isChecked = byGenre
		}
		menu.menu.setGroupCheckable(group, true, true)
		menu.setOnMenuItemClickListener {
			statsViewModel.byGenre.value = it.order == 1
			true
		}
		menu.show()
	}

	// endregion

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
