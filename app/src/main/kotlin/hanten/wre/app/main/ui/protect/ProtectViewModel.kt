package hanten.wre.app.main.ui.protect

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import hanten.wre.app.core.exceptions.WrongPasswordException
import hanten.wre.app.core.prefs.AppPasswordHasher
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseViewModel
import hanten.wre.app.core.util.ext.MutableEventFlow
import hanten.wre.app.core.util.ext.call
import javax.inject.Inject

private const val PASSWORD_COMPARE_DELAY = 1_000L

@HiltViewModel
class ProtectViewModel @Inject constructor(
	private val settings: AppSettings,
	private val protectHelper: AppProtectHelper,
) : BaseViewModel() {

	private var job: Job? = null

	val onUnlockSuccess = MutableEventFlow<Unit>()

	val isBiometricEnabled
		get() = settings.isBiometricProtectionEnabled

	val isNumericPassword
		get() = settings.isAppPasswordNumeric

	fun tryUnlock(password: String) {
		if (job?.isActive == true) {
			return
		}
		job = launchLoadingJob(Dispatchers.Default) {
			val stored = settings.appPassword
			if (AppPasswordHasher.verify(password, stored)) {
				if (AppPasswordHasher.isLegacy(stored)) {
					settings.appPassword = AppPasswordHasher.hash(password)
				}
				unlock()
			} else {
				delay(PASSWORD_COMPARE_DELAY)
				throw WrongPasswordException()
			}
		}
	}

	fun unlock() {
		protectHelper.unlock()
		onUnlockSuccess.call(Unit)
	}
}
