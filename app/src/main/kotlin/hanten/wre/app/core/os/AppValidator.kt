package hanten.wre.app.core.os

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import hanten.wre.app.parsers.util.suspendlazy.suspendLazy
import java.io.File
import java.security.MessageDigest
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

	/**
	 * Verifies that an APK file is signed with our release certificate
	 * and belongs to this application package. Used before installing updates.
	 */
	fun isTrustedApk(apkFile: File): Boolean = runCatching {
		val pm = context.packageManager
		val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			pm.getPackageArchiveInfo(apkFile.path, PackageManager.GET_SIGNING_CERTIFICATES)
		} else {
			@Suppress("DEPRECATION")
			pm.getPackageArchiveInfo(apkFile.path, PackageManager.GET_SIGNATURES)
		} ?: return false
		if (info.packageName != context.packageName) return false
		val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			info.signingInfo?.apkContentsSigners
		} else {
			@Suppress("DEPRECATION")
			info.signatures
		} ?: return false
		val digest = MessageDigest.getInstance("SHA-256")
		signatures.any { signature ->
			digest.digest(signature.toByteArray()).toHexString() == CERT_SHA256
		}
	}.getOrDefault(false)

	private fun ByteArray.toHexString(): String {
		val chars = CharArray(size * 2)
		var i = 0
		for (b in this) {
			val v = b.toInt() and 0xFF
			chars[i++] = HEX_CHARS[v ushr 4]
			chars[i++] = HEX_CHARS[v and 0x0F]
		}
		return chars.concatToString()
	}

	private companion object {
		private const val CERT_SHA256 = "d3f2ab2d82afa0ab02d5f6839826847d850b39f80265777debdeabe679e3cc67"
		private const val HEX_CHARS = "0123456789abcdef"
	}
}
