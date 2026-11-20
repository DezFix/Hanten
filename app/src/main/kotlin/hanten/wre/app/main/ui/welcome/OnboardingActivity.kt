package hanten.wre.app.main.ui.welcome

import android.content.res.ColorStateList
import android.graphics.Color
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
import androidx.annotation.StringRes
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
import hanten.wre.app.core.model.titleResId
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.ListMode
import hanten.wre.app.core.ui.BaseActivity
import hanten.wre.app.core.ui.widgets.ChipsView
import hanten.wre.app.core.util.ext.getDisplayName
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.tryLaunch
import hanten.wre.app.databinding.ActivityOnboardingBinding
import hanten.wre.app.filter.ui.model.FilterProperty
import hanten.wre.app.parsers.model.ContentType
import hanten.wre.app.profile.ProfileStyle
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

	private val viewModel by viewModels<WelcomeViewModel>()
	private val backupSelectCall = registerForActivityResult(ActivityResultContracts.OpenDocument(), this)
	private var step = 0

	private val steps: List<View>
		get() = with(viewBinding) { listOf(stepWelcome, stepProfile, stepKinds, stepLanguages, stepImport) }

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
			buttonRestore.setOnClickListener(this@OnboardingActivity)
			buttonRestoreMihon.setOnClickListener(this@OnboardingActivity)
			buttonDirectories.setOnClickListener(this@OnboardingActivity)
			chipsType.onChipClickListener = this@OnboardingActivity
			chipsLocales.onChipClickListener = this@OnboardingActivity
			chipsListMode.onChipClickListener = this@OnboardingActivity
		}
		createDots()
		setupProfile()
		viewModel.locales.observe(this, ::onLocalesChanged)
		viewModel.types.observe(this, ::onTypesChanged)
		viewModel.listMode.observe(this, ::onListModeChanged)
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
			R.id.button_next -> if (step == LAST_STEP) finish() else showStep(step + 1)
			R.id.button_back -> showStep(step - 1)
			R.id.button_skip, R.id.button_have_backup -> showStep(LAST_STEP)
			R.id.button_restore, R.id.button_restore_mihon -> if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
				Snackbar.make(v, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
			}

			R.id.button_directories -> router.openDirectoriesSettings()
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			router.showBackupRestoreDialog(result)
		}
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		when (data) {
			is ContentType -> viewModel.setTypeChecked(data, !chip.isChecked)
			is Locale -> viewModel.setLocaleChecked(data, !chip.isChecked)
			is ListMode -> viewModel.setListMode(data)
		}
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
		for (i in 0 until binding.rowColors.childCount) {
			binding.rowColors.getChildAt(i).alpha = if (i == settings.profileColor) 1f else 0.45f
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

	private fun onTypesChanged(value: FilterProperty<ContentType>) {
		viewBinding.chipsType.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = getString(it.titleResId),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}

	private fun onListModeChanged(selected: ListMode) {
		viewBinding.chipsListMode.setChips(
			ListMode.entries.map {
				ChipsView.ChipModel(
					title = getString(it.titleResId),
					isChecked = it == selected,
					data = it,
				)
			},
		)
	}

	@get:StringRes
	private val ListMode.titleResId: Int
		get() = when (this) {
			ListMode.LIST -> R.string.list
			ListMode.DETAILED_LIST -> R.string.detailed_list
			ListMode.GRID -> R.string.grid
		}

	private companion object {

		const val KEY_STEP = "step"
		const val LAST_STEP = 4
		const val ACTIVE_DOT_SCALE = 4.5f
	}
}
