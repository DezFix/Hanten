package hanten.wre.app.main.ui.welcome

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.widgets.ChipsView
import hanten.wre.app.core.util.ext.getDisplayName
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.tryLaunch
import hanten.wre.app.databinding.ActivityOnboardingBinding
import hanten.wre.app.databinding.ItemOnboardingOptionBinding
import hanten.wre.app.filter.ui.model.FilterProperty
import hanten.wre.app.profile.ProfileStyle
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import hanten.wre.app.scrobbling.common.ui.ScrobblerAuthHelper
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

/**
 * First-run setup in four short steps: welcome, content types, languages, moving the library over.
 * Every step can be skipped; choices are committed by [WelcomeViewModel] as soon as they change,
 * so leaving early still gives a working set of sources.
 */
@AndroidEntryPoint
class OnboardingActivity :
	BaseActivity<ActivityOnboardingBinding>(),
	ChipsView.OnChipClickListener,
	View.OnClickListener,
	ActivityResultCallback<Uri?> {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var scrobblerAuthHelper: ScrobblerAuthHelper

	@Inject
	lateinit var localState: hanten.wre.app.core.prefs.LocalState

	private val viewModel by viewModels<WelcomeViewModel>()
	private val backupSelectCall = registerForActivityResult(ActivityResultContracts.OpenDocument(), this)
	private var step = 0

	private val steps: List<View>
		get() = with(viewBinding) { listOf(stepWelcome, stepProfile, stepKinds, stepLanguages, stepImport, stepReports) }

	private val backCallback = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() = showStep(step - 1)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityOnboardingBinding.inflate(layoutInflater))
		step = savedInstanceState?.getInt(KEY_STEP) ?: 0
		onBackPressedDispatcher.addCallback(this, backCallback)
		with(viewBinding) {
			buttonNext.setOnClickListener(this@OnboardingActivity)
			buttonBack.setOnClickListener(this@OnboardingActivity)
			buttonSkip.setOnClickListener(this@OnboardingActivity)
			buttonHaveBackup.setOnClickListener(this@OnboardingActivity)
			buttonShikimori.setOnClickListener(this@OnboardingActivity)
			buttonAnilist.setOnClickListener(this@OnboardingActivity)
			chipsLocales.onChipClickListener = this@OnboardingActivity
		}
		setupImportOptions()
		// on by default for a new install; a re-run shows the current choice
		viewBinding.switchErrorReports.isChecked = !localState.isErrorReportsAsked || settings.isErrorReportsEnabled
		createDots()
		setupProfile()
		viewModel.locales.observe(this, ::onLocalesChanged)
		viewModel.kinds.observe(this, ::onKindsChanged)
		viewModel.popularSources.observe(this, ::onPopularSourcesChanged)
		viewModel.enabledCount.observe(this) {
			viewBinding.textSourcesCount.text = getString(R.string.onboarding_sources_count, it)
		}
		showStep(step, animate = false)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putInt(KEY_STEP, step)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
		v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
		return WindowInsetsCompat.CONSUMED
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_next -> if (step == LAST_STEP) {
				settings.isErrorReportsEnabled = viewBinding.switchErrorReports.isChecked
				localState.isErrorReportsAsked = true
				finish()
			} else {
				showStep(step + 1)
			}
			R.id.button_back -> showStep(step - 1)
			R.id.button_skip, R.id.button_have_backup -> showStep(LAST_STEP)
			R.id.button_shikimori -> startTrackerAuth(v, ScrobblerService.SHIKIMORI)
			R.id.button_anilist -> startTrackerAuth(v, ScrobblerService.ANILIST)
		}
	}

	private fun startTrackerAuth(v: View, service: ScrobblerService) {
		if (scrobblerAuthHelper.isAuthorized(service)) {
			router.openScrobblerSettings(service)
			return
		}
		scrobblerAuthHelper.startAuth(this, service).onFailure {
			Snackbar.make(v, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	private fun pickBackup(v: View) {
		if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
			Snackbar.make(v, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			router.showBackupRestoreDialog(result)
		}
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		if (data is Locale) viewModel.setLocaleChecked(data, !chip.isChecked)
	}

	private fun showStep(index: Int, animate: Boolean = true) {
		step = index.coerceIn(0, LAST_STEP)
		with(viewBinding) {
			if (animate) {
				TransitionManager.beginDelayedTransition(root, AutoTransition().setDuration(220L))
			}
			steps.forEachIndexed { i, view -> view.isVisible = i == step }
			buttonSkip.visibility = if (step < LAST_STEP) View.VISIBLE else View.INVISIBLE
			buttonHaveBackup.isVisible = step == 0
			buttonBack.isVisible = step > 0
			buttonNext.setText(
				when (step) {
					0 -> R.string.onboarding_start
					LAST_STEP -> R.string.onboarding_finish
					else -> R.string.next
				},
			)
		}
		backCallback.isEnabled = step > 0
		updateDots()
	}

	/** Local profile: nickname, avatar glyph and color, saved to settings as they change. */
	private fun setupProfile() {
		val binding = viewBinding
		binding.editProfileName.setText(settings.profileName)
		binding.editProfileName.doAfterTextChanged {
			settings.profileName = it?.toString()
			updateProfilePreview()
		}
		val size = resources.getDimensionPixelSize(R.dimen.onboarding_profile_option)
		val gap = resources.getDimensionPixelSize(R.dimen.onboarding_dot_gap) * 2
		ProfileStyle.avatars.indices.forEach { index ->
			val view = TextView(this).apply {
				gravity = Gravity.CENTER
				textSize = 20f
				setTextColor(Color.WHITE)
				setBackgroundResource(R.drawable.bg_profile_avatar)
				setOnClickListener {
					settings.profileAvatar = index
					updateProfilePreview()
				}
			}
			binding.rowAvatars.addView(view, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
		}
		ProfileStyle.colors.indices.forEach { index ->
			val view = View(this).apply {
				setBackgroundResource(R.drawable.bg_profile_avatar)
				backgroundTintList = ColorStateList.valueOf(ProfileStyle.color(index))
				contentDescription = getString(R.string.profile_color)
				setOnClickListener {
					settings.profileColor = index
					updateProfilePreview()
				}
			}
			binding.rowColors.addView(view, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
		}
		updateProfilePreview()
	}

	private fun updateProfilePreview() {
		val binding = viewBinding
		val name = binding.editProfileName.text?.toString()
		val color = ProfileStyle.color(settings.profileColor)
		binding.textAvatarPreview.text = ProfileStyle.avatarText(settings.profileAvatar, name)
		binding.textAvatarPreview.backgroundTintList = ColorStateList.valueOf(color)
		val neutral = getThemeColor(materialR.attr.colorOutline)
		for (i in 0 until binding.rowAvatars.childCount) {
			val view = binding.rowAvatars.getChildAt(i) as TextView
			view.text = ProfileStyle.avatarText(i, name)
			val selected = i == settings.profileAvatar
			view.backgroundTintList = ColorStateList.valueOf(if (selected) color else neutral)
		}
		// Every swatch gets an outline (dark colors vanish on the night background); the chosen one a thick ring
		val ring = getThemeColor(materialR.attr.colorOnSurface)
		val density = resources.displayMetrics.density
		for (i in 0 until binding.rowColors.childCount) {
			val selected = i == settings.profileColor
			binding.rowColors.getChildAt(i).background = GradientDrawable().apply {
				shape = GradientDrawable.OVAL
				setColor(ProfileStyle.color(i))
				setStroke((if (selected) 3f else 1f).times(density).toInt(), if (selected) ring else neutral)
			}
			binding.rowColors.getChildAt(i).backgroundTintList = null
		}
	}

	private fun createDots() {
		val container = viewBinding.dots
		val height = resources.getDimensionPixelSize(R.dimen.onboarding_dot_height)
		val gap = resources.getDimensionPixelSize(R.dimen.onboarding_dot_gap)
		repeat(LAST_STEP + 1) {
			container.addView(
				View(this).apply { setBackgroundResource(R.drawable.onboarding_dot) },
				LinearLayout.LayoutParams(height, height).apply { marginEnd = gap },
			)
		}
	}

	private fun updateDots() {
		val active = getThemeColor(appcompatR.attr.colorPrimary)
		val inactive = getThemeColor(materialR.attr.colorOutlineVariant)
		val height = resources.getDimensionPixelSize(R.dimen.onboarding_dot_height)
		for (i in 0 until viewBinding.dots.childCount) {
			val dot = viewBinding.dots.getChildAt(i)
			dot.background.mutate().setTint(if (i <= step) active else inactive)
			dot.layoutParams = dot.layoutParams.apply {
				width = if (i == step) (height * ACTIVE_DOT_SCALE).roundToInt() else height
			}
		}
	}

	private fun onLocalesChanged(value: FilterProperty<Locale>) {
		val chips = viewBinding.chipsLocales
		chips.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = it.getDisplayName(chips.context),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}

	private fun onKindsChanged(selected: Set<ReadingKind>) {
		val container = viewBinding.listKinds
		container.removeAllViews()
		ReadingKind.entries.forEach { kind ->
			val (glyph, title, hint) = when (kind) {
				ReadingKind.MANGA -> Triple("漫", R.string.onboarding_kind_manga, R.string.onboarding_kind_manga_hint)
				ReadingKind.WEBTOON -> Triple("韓", R.string.onboarding_kind_webtoon, R.string.onboarding_kind_webtoon_hint)
				ReadingKind.NOVEL -> Triple("文", R.string.onboarding_kind_novel, R.string.onboarding_kind_novel_hint)
				ReadingKind.OTHER -> Triple("18", R.string.onboarding_kind_other, R.string.onboarding_kind_other_hint)
			}
			val isOn = kind in selected
			addOption(container, glyph, getString(title), getString(hint), isOn, isCard = true) {
				viewModel.setKindChecked(kind, !isOn)
			}
		}
	}

	private fun onPopularSourcesChanged(items: List<PopularSourceModel>) {
		val binding = viewBinding
		binding.textSourcesTitle.isVisible = items.isNotEmpty()
		binding.listSources.isVisible = items.isNotEmpty()
		binding.listSources.removeAllViews()
		items.forEach { item ->
			val glyph = item.title.filter { it.isLetterOrDigit() }.take(2)
			addOption(binding.listSources, glyph, item.title, item.subtitle, item.isChecked, isCard = false) {
				viewModel.setSourceChecked(item.source, !item.isChecked)
			}
		}
	}

	private fun setupImportOptions() {
		val container = viewBinding.listImport
		addOption(
			container, "H", getString(R.string.onboarding_import_kotatsu),
			getString(R.string.onboarding_import_kotatsu_hint), null, isCard = true, onClick = ::pickBackup,
		)
		addOption(
			container, "M", getString(R.string.onboarding_import_mihon),
			getString(R.string.onboarding_import_mihon_hint), null, isCard = true, onClick = ::pickBackup,
		)
		addOption(
			container, "↓", getString(R.string.local_manga_directories),
			getString(R.string.onboarding_import_dirs_hint), null, isCard = true,
		) { router.openDirectoriesSettings() }
	}

	/**
	 * One mockup choice row. [isChecked] null = an action row (arrow instead of a check mark).
	 * Cards are separate rounded blocks; other rows sit inside a shared rounded list.
	 */
	private fun addOption(
		container: LinearLayout,
		glyph: String,
		title: String,
		hint: String?,
		isChecked: Boolean?,
		isCard: Boolean,
		onClick: (View) -> Unit,
	) {
		val row = ItemOnboardingOptionBinding.inflate(layoutInflater, container, false)
		row.textGlyph.text = glyph
		row.textTitle.text = title
		row.textHint.text = hint
		row.textHint.isVisible = !hint.isNullOrEmpty()
		val primary = getThemeColor(appcompatR.attr.colorPrimary)
		val outline = getThemeColor(materialR.attr.colorOutline)
		val density = resources.displayMetrics.density
		when (isChecked) {
			true -> {
				row.imageCheck.setImageResource(R.drawable.ic_check)
				row.imageCheck.imageTintList = ColorStateList.valueOf(getThemeColor(materialR.attr.colorOnPrimary))
				row.imageCheck.setPadding((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
				row.imageCheck.background = GradientDrawable().apply {
					if (isCard) shape = GradientDrawable.OVAL else cornerRadius = 6 * density
					setColor(primary)
				}
			}

			false -> {
				row.imageCheck.setImageDrawable(null)
				row.imageCheck.background = GradientDrawable().apply {
					if (isCard) shape = GradientDrawable.OVAL else cornerRadius = 6 * density
					setStroke((2 * density).toInt(), outline)
				}
			}

			null -> {
				row.imageCheck.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_expand_more))
				row.imageCheck.rotation = -90f
				row.imageCheck.imageTintList = ColorStateList.valueOf(outline)
			}
		}
		if (isCard) {
			row.root.background = GradientDrawable().apply {
				cornerRadius = 20 * density
				setColor(
					getThemeColor(
						if (isChecked == true) materialR.attr.colorPrimaryContainer else materialR.attr.colorSurfaceContainer,
					),
				)
				setStroke((2 * density).toInt(), if (isChecked == true) primary else Color.TRANSPARENT)
			}
		} else {
			(row.root.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 0
			val tile = (40 * density).toInt()
			row.textGlyph.layoutParams = row.textGlyph.layoutParams.apply {
				width = tile
				height = tile
			}
			row.textGlyph.textSize = 14f
		}
		if (isChecked != null) {
			row.root.isSelected = isChecked
		}
		row.root.setOnClickListener(onClick)
		container.addView(row.root)
	}

	companion object {

		const val EXTRA_RERUN = "rerun"
		private const val KEY_STEP = "step"
		private const val LAST_STEP = 5
		private const val ACTIVE_DOT_SCALE = 4.5f
	}
}
