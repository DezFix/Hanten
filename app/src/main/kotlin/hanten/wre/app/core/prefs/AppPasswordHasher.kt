package hanten.wre.app.core.prefs

import android.util.Base64
import hanten.wre.app.parsers.util.md5
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted password hashing for the app lock.
 *
 * The old format was a bare unsalted MD5 hex string. It is still accepted for
 * verification (and upgraded on the next successful unlock), so existing users
 * are not locked out.
 */
object AppPasswordHasher {

	private const val PREFIX = "pbkdf2"
	private const val ITERATIONS = 120_000
	private const val KEY_LENGTH_BITS = 256
	private const val SALT_BYTES = 16
	private val random = SecureRandom()

	fun hash(password: String): String {
		val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
		val hash = derive(password, salt, ITERATIONS)
		return listOf(
			PREFIX,
			ITERATIONS.toString(),
			Base64.encodeToString(salt, Base64.NO_WRAP),
			Base64.encodeToString(hash, Base64.NO_WRAP),
		).joinToString(separator = "\$")
	}

	fun verify(password: String, stored: String?): Boolean {
		if (stored.isNullOrEmpty()) {
			return false
		}
		if (!stored.startsWith("$PREFIX\$")) {
			// Legacy unsalted MD5.
			return MessageDigest.isEqual(
				password.md5().toByteArray(),
				stored.toByteArray(),
			)
		}
		val parts = stored.split('$')
		if (parts.size != 4) {
			return false
		}
		val iterations = parts[1].toIntOrNull() ?: return false
		val salt = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return false
		val expected = runCatching { Base64.decode(parts[3], Base64.NO_WRAP) }.getOrNull() ?: return false
		val actual = derive(password, salt, iterations)
		return MessageDigest.isEqual(actual, expected)
	}

	fun isLegacy(stored: String?): Boolean = !stored.isNullOrEmpty() && !stored.startsWith("$PREFIX\$")

	private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
		val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
		return try {
			// PBKDF2WithHmacSHA256 is not available on every Android 6 device;
			// fall back to SHA1 there. Both paths use the same iteration count.
			val factory = runCatching {
				SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
			}.getOrElse {
				SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
			}
			factory.generateSecret(spec).encoded
		} finally {
			spec.clearPassword()
		}
	}
}
