package hanten.wre.app.core.os

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import hanten.wre.app.parsers.util.suspendlazy.suspendLazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppValidator @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	@SuppressLint("InlinedApi")
	val isOriginalApp = suspendLazy(Dispatchers.IO) {
		val certificates = mapOf(CERT_SHA256.hexToByteArray() to PackageManager.CERT_INPUT_SHA256)
		PackageInfoCompat.hasSignatures(context.packageManager, context.packageName, certificates, false)
	}

	private companion object {
		private const val CERT_SHA256 = "d3f2ab2d82afa0ab02d5f6839826847d850b39f80265777debdeabe679e3cc67"
	}
}
