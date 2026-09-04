package hanten.wre.app.core

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import hanten.wre.app.BuildConfig
import hanten.wre.app.core.db.MangaDatabase
import hanten.wre.app.core.os.AppValidator
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.util.ext.processLifecycleScope
import hanten.wre.app.local.data.LocalStorageChanges
import hanten.wre.app.local.data.index.LocalMangaIndex
import hanten.wre.app.local.domain.model.LocalManga
import hanten.wre.app.mihon.MihonExtensionManager
import hanten.wre.app.settings.work.WorkScheduleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.internal.platform.PlatformRegistry
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
open class BaseApp : Application(), Configuration.Provider {

	@Inject
	lateinit var mihonExtensionManager: MihonExtensionManager

	@Inject
	lateinit var databaseObserversProvider: Provider<Set<@JvmSuppressWildcards InvalidationTracker.Observer>>

	@Inject
	lateinit var activityLifecycleCallbacks: Set<@JvmSuppressWildcards ActivityLifecycleCallbacks>

	@Inject
	lateinit var database: Provider<MangaDatabase>

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	@Inject
	lateinit var appValidator: AppValidator

	@Inject
	lateinit var workScheduleManager: WorkScheduleManager

	@Inject
	lateinit var localMangaIndexProvider: Provider<LocalMangaIndex>

	@Inject
	@LocalStorageChanges
	lateinit var localStorageChanges: MutableSharedFlow<LocalManga?>

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()

	override fun onCreate() {
		super.onCreate()
		PlatformRegistry.applicationContext = this // TODO replace with OkHttp.initialize
		AppCompatDelegate.setDefaultNightMode(settings.theme)
		// Initialize Sentry if the user opted into crash reports and/or source error reports
		if (settings.isCrashAnalyticsEnabled || settings.isSourceErrorReportsEnabled) {
			initializeSentry()
		}
		// TLS 1.3 support for Android < 10
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			Security.insertProviderAt(Conscrypt.newProvider(), 1)
		}
		setupActivityLifecycleCallbacks()
		mihonExtensionManager.initialize()
		processLifecycleScope.launch(Dispatchers.IO) {
			setupDatabaseObservers()
			localStorageChanges.collect(localMangaIndexProvider.get())
		}
		workScheduleManager.init()
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		// ACRA removed
	} 

	@WorkerThread
	private fun setupDatabaseObservers() {
		val tracker = database.get().invalidationTracker
		databaseObserversProvider.get().forEach {
			tracker.addObserver(it)
		}
	}

	private fun setupActivityLifecycleCallbacks() {
		activityLifecycleCallbacks.forEach {
			registerActivityLifecycleCallbacks(it)
		}
	}

	private fun initializeSentry() {
		try {
			io.sentry.android.core.SentryAndroid.init(this) { options ->
				// DSN comes from SENTRY_DSN environment variable at build time,
				// falling back to our own self-hosted Bugsink instance.
				// The DSN holds a public key only, it is safe to embed.
				val dsn = BuildConfig.SENTRY_DSN.ifEmpty { BUGSINK_DSN }
				options.dsn = dsn
				options.isEnableAutoSessionTracking = true
				options.isEnableUncaughtExceptionHandler = settings.isCrashAnalyticsEnabled
				options.environment = if (BuildConfig.DEBUG) "debug" else "production"
				options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
					val exceptions = event.exceptions
					if (exceptions != null && exceptions.any { it.isHttpError() }) null else event
				}
			}
		} catch (e: Exception) {
			// Log error but don't crash if Sentry initialization fails
			e.printStackTrace()
		}
	}

	private fun io.sentry.protocol.SentryException.isHttpError(): Boolean {
		val name = type ?: return false
		return name == "HttpException" ||
			name.endsWith(".HttpException") ||
			name == "SentryHttpClientException" ||
			name == "SocketTimeoutException" ||
			name == "UnknownHostException" ||
			name == "ConnectException" ||
			name == "SSLException"
	}

	private companion object {
		// Own self-hosted Bugsink instance (Sentry-compatible). Public key, safe to embed.
		const val BUGSINK_DSN = "https://57116ae6fb41473fa5176a5c7aa3e299@wrebug.bugsink.com/2"
	}
}
