package hanten.wre.app.scrobbling.common.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import hanten.wre.app.scrobbling.common.domain.ScrobblerRepositoryMap
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerUser
import java.security.SecureRandom
import javax.inject.Inject

class ScrobblerAuthHelper @Inject constructor(
	private val repositoriesMap: ScrobblerRepositoryMap,
) {

	fun isAuthorized(scrobbler: ScrobblerService) = repositoriesMap[scrobbler].isAuthorized

	fun getCachedUser(scrobbler: ScrobblerService): ScrobblerUser? {
		return repositoriesMap[scrobbler].cachedUser
	}

	suspend fun getUser(scrobbler: ScrobblerService): ScrobblerUser {
		return repositoriesMap[scrobbler].loadUser()
	}

	@SuppressLint("UnsafeImplicitIntentLaunch")
	fun startAuth(context: Context, scrobbler: ScrobblerService) = runCatching {
		val repository = repositoriesMap[scrobbler]
		val state = createState()
		context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit {
			putString(KEY_STATE, state)
			putLong(KEY_STATE_TIME, System.currentTimeMillis())
		}
		val intent = Intent(Intent.ACTION_VIEW)
		intent.data = "${repository.oauthUrl}&state=$state".toUri()
		context.startActivity(intent)
	}

	/** Rejects callbacks that were not started by this app instance. */
	fun validateState(context: Context, state: String?): Boolean {
		if (state.isNullOrBlank()) return false
		val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
		val expected = prefs.getString(KEY_STATE, null)
		val createdAt = prefs.getLong(KEY_STATE_TIME, 0L)
		val fresh = System.currentTimeMillis() - createdAt in 0..STATE_LIFETIME_MS
		val valid = fresh && expected != null && expected == state
		if (valid) {
			prefs.edit { clear() }
		}
		return valid
	}

	private fun createState(): String {
		val bytes = ByteArray(32)
		SecureRandom().nextBytes(bytes)
		return bytes.joinToString("") { "%02x".format(it) }
	}

	private companion object {
		const val AUTH_PREFS = "scrobbler_auth"
		const val KEY_STATE = "oauth_state"
		const val KEY_STATE_TIME = "oauth_state_time"
		const val STATE_LIFETIME_MS = 10 * 60 * 1000L
	}
}
