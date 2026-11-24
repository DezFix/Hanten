package hanten.wre.app.core.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentManager
import androidx.viewbinding.ViewBinding
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import hanten.wre.app.BuildConfig
import hanten.wre.app.R
import hanten.wre.app.core.exceptions.resolve.ExceptionResolver
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.prefs.ColorScheme
import hanten.wre.app.core.ui.util.ActionModeDelegate
import hanten.wre.app.core.util.ext.isWebViewUnavailable
import hanten.wre.app.extras.ThemeReveal
import hanten.wre.app.main.ui.protect.ScreenshotPolicyHelper
import androidx.appcompat.R as appcompatR

abstract class BaseActivity<B : ViewBinding> :
	AppCompatActivity(),
	OnApplyWindowInsetsListener,
	ScreenshotPolicyHelper.ContentContainer {

	private var isAmoledTheme = false

	lateinit var viewBinding: B
		private set

	protected lateinit var exceptionResolver: ExceptionResolver
		private set

	@JvmField
	val actionModeDelegate = ActionModeDelegate()

	private lateinit var entryPoint: BaseActivityEntryPoint

	private var retroCodeStep = 0
	private var retroCodeLastAt = 0L

	override fun attachBaseContext(newBase: Context) {
		entryPoint = EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(newBase.applicationContext)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			AppCompatDelegate.setApplicationLocales(entryPoint.settings.appLocales)
		}
		super.attachBaseContext(newBase)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		val settings = entryPoint.settings
		isAmoledTheme = settings.isAmoledTheme
		val importedTheme = entryPoint.importedThemeStore.selectedTheme.takeIf {
			settings.colorScheme == ColorScheme.CUSTOM && DynamicColors.isDynamicColorAvailable()
		}
		val colorScheme = when {
			importedTheme == null && settings.colorScheme == ColorScheme.CUSTOM -> ColorScheme.default
			else -> settings.colorScheme
		}
		setTheme(colorScheme.styleResId)
		if (importedTheme != null) {
			val uiMode = resources.configuration.uiMode
			val isNightMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
				Configuration.UI_MODE_NIGHT_YES
			setTheme(
				if (isNightMode) {
					R.style.ThemeOverlay_Hanten_Custom_Dark
				} else {
					R.style.ThemeOverlay_Hanten_Custom_Light
				},
			)
			DynamicColors.applyToActivityIfAvailable(
				this,
				DynamicColorsOptions.Builder()
					.setContentBasedSource(importedTheme.primaryColor)
					.build(),
			)
		}
		val accentOverlay = when (settings.accentColor) {
			"red" -> R.style.ThemeOverlay_Hanten_Accent_Red
			"blue" -> R.style.ThemeOverlay_Hanten_Accent_Blue
			"gold" -> R.style.ThemeOverlay_Hanten_Accent_Gold
			"green" -> R.style.ThemeOverlay_Hanten_Accent_Green
			"plum" -> R.style.ThemeOverlay_Hanten_Accent_Plum
			"teal" -> R.style.ThemeOverlay_Hanten_Accent_Teal
			else -> 0
		}
		if (accentOverlay != 0) {
			setTheme(accentOverlay)
		}
		when (settings.cornerStyle) {
			"small" -> setTheme(R.style.ThemeOverlay_Hanten_Corners_Small)
			"large" -> setTheme(R.style.ThemeOverlay_Hanten_Corners_Large)
		}
		if (isAmoledTheme) {
			setTheme(R.style.ThemeOverlay_Hanten_Amoled)
		}
		putDataToExtras(intent)
		exceptionResolver = entryPoint.exceptionResolverFactory.create(this)
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
	}

	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)
		onBackPressedDispatcher.addCallback(actionModeDelegate)
		ThemeReveal.playIfPending(this)
	}

	override fun onNewIntent(intent: Intent) {
		putDataToExtras(intent)
		super.onNewIntent(intent)
	}

	@Deprecated("Use ViewBinding", level = DeprecationLevel.ERROR)
	override fun setContentView(layoutResID: Int) = throw UnsupportedOperationException()

	@Deprecated("Use ViewBinding", level = DeprecationLevel.ERROR)
	override fun setContentView(view: View?) = throw UnsupportedOperationException()

	protected fun setContentView(binding: B) {
		this.viewBinding = binding
		super.setContentView(binding.root)
		ViewCompat.setOnApplyWindowInsetsListener(binding.root, this)
		val toolbar = (binding.root.findViewById<View>(R.id.toolbar) as? Toolbar)
		toolbar?.let(this::setSupportActionBar)
	}

	protected fun setDisplayHomeAsUp(isEnabled: Boolean, showUpAsClose: Boolean) {
		supportActionBar?.run {
			setDisplayHomeAsUpEnabled(isEnabled)
			if (showUpAsClose) {
				setHomeAsUpIndicator(appcompatR.drawable.abc_ic_clear_material)
			}
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		val fm = supportFragmentManager
		if (fm.isStateSaved) {
			return false
		}
		if (fm.backStackEntryCount > 0) {
			fm.popBackStack()
		} else {
			dispatchNavigateUp()
		}
		return true
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
			ThemeReveal.onTouch(ev.rawX, ev.rawY)
		}
		return super.dispatchTouchEvent(ev)
	}

	/**
	 * Easter egg: volume up, up, down, down (within a few seconds) unlocks and switches on the Retro theme;
	 * the same code again goes back to the Hanten theme. The keys still change the volume as usual.
	 */
	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		val keyCode = event.keyCode
		if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
			event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
			entryPoint.settings.isEasterEggsEnabled
		) {
			onRetroCodeKey(keyCode)
		}
		return super.dispatchKeyEvent(event)
	}

	private fun onRetroCodeKey(keyCode: Int) {
		val now = System.currentTimeMillis()
		if (now - retroCodeLastAt > RETRO_CODE_TIMEOUT_MS) {
			retroCodeStep = 0
		}
		retroCodeLastAt = now
		retroCodeStep = if (keyCode == RETRO_CODE[retroCodeStep]) {
			retroCodeStep + 1
		} else if (keyCode == RETRO_CODE[0]) {
			1
		} else {
			0
		}
		if (retroCodeStep == RETRO_CODE.size) {
			retroCodeStep = 0
			val settings = entryPoint.settings
			val toRetro = settings.colorScheme != ColorScheme.RETRO
			settings.isRetroThemeUnlocked = true
			Toast.makeText(
				this,
				if (toRetro) R.string.retro_theme_unlocked else R.string.retro_theme_off,
				Toast.LENGTH_SHORT,
			).show()
			ThemeReveal.capture(this, settings)
			if (toRetro) {
				settings.colorSchemeBeforeRetro = settings.colorScheme
				settings.colorScheme = ColorScheme.RETRO
			} else {
				settings.colorScheme = settings.colorSchemeBeforeRetro.takeIf { it != ColorScheme.RETRO }
					?: ColorScheme.default
			}
			entryPoint.activityRecreationHandle.recreateAll()
		}
	}

	protected fun isDarkAmoledTheme(): Boolean {
		val uiMode = resources.configuration.uiMode
		val isNight = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
		return isNight && isAmoledTheme
	}

	@CallSuper
	override fun onSupportActionModeStarted(mode: ActionMode) {
		super.onSupportActionModeStarted(mode)
		actionModeDelegate.onSupportActionModeStarted(mode, window)
	}

	@CallSuper
	override fun onSupportActionModeFinished(mode: ActionMode) {
		super.onSupportActionModeFinished(mode)
		actionModeDelegate.onSupportActionModeFinished(mode, window)
	}

	protected open fun dispatchNavigateUp() {
		val upIntent = parentActivityIntent
		if (upIntent != null) {
			if (!navigateUpTo(upIntent)) {
				startActivity(upIntent)
			}
		} else {
			finishAfterTransition()
		}
	}

	override fun isNsfwContent(): Flow<Boolean> = flowOf(false)

	private fun putDataToExtras(intent: Intent?) {
		intent?.putExtra(AppRouter.KEY_DATA, intent.data)
	}

	protected fun setContentViewWebViewSafe(viewBindingProducer: () -> B): Boolean {
		return try {
			setContentView(viewBindingProducer())
			true
		} catch (e: Exception) {
			if (e.isWebViewUnavailable()) {
				Toast.makeText(this, R.string.web_view_unavailable, Toast.LENGTH_LONG).show()
				finishAfterTransition()
				false
			} else {
				throw e
			}
		}
	}

	protected fun hasViewBinding() = ::viewBinding.isInitialized

	private companion object {

		const val RETRO_CODE_TIMEOUT_MS = 2500L
		val RETRO_CODE = intArrayOf(
			KeyEvent.KEYCODE_VOLUME_UP,
			KeyEvent.KEYCODE_VOLUME_UP,
			KeyEvent.KEYCODE_VOLUME_DOWN,
			KeyEvent.KEYCODE_VOLUME_DOWN,
		)
	}
}
