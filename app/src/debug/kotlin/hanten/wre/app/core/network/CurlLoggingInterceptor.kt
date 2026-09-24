package hanten.wre.app.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import hanten.wre.app.core.network.CommonHeaders.ACCEPT_ENCODING

class CurlLoggingInterceptor(
	private val curlOptions: String? = null
) : Interceptor {

	private val escapeRegex = Regex("([\\[\\]\"])")

	// Values that must never reach logcat, even in a debug build
	private val secretRegex = Regex(
		"(?i)(client_secret|refresh_token|access_token|auth_token|password|passwd|secret|token|\"code\")" +
			"(\\s*[=:]\\s*[\"']?)([^&\"'\\s}]+)",
	)

	private val botTokenRegex = Regex("(?i)(/bot)[^/]+")

	private val sensitiveHeaders = setOf(
		"authorization",
		"proxy-authorization",
		"cookie",
		"set-cookie",
		"x-auth-token",
	)

	override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request()).also {
		logRequest(it.networkResponse?.request ?: it.request)
	}

	private fun logRequest(request: Request) {
		var isCompressed = false

		val url = request.url.toString().redactUrl()
		val curlCmd = StringBuilder()
		curlCmd.append("curl")
		if (curlOptions != null) {
			curlCmd.append(' ').append(curlOptions)
		}
		curlCmd.append(" -X ").append(request.method)

		for ((name, value) in request.headers) {
			if (name.equals(ACCEPT_ENCODING, ignoreCase = true) && value.equals("gzip", ignoreCase = true)) {
				isCompressed = true
			}
			val safeValue = if (name.lowercase() in sensitiveHeaders) "***" else value.escape()
			curlCmd.append(" -H \"").append(name).append(": ").append(safeValue).append('\"')
		}

		val body = request.body
		if (body != null) {
			curlCmd.append(readBody(body)?.let { " --data-raw '" + it.replace("\n", "\\n") + "'" } ?: "")
		}
		if (isCompressed) {
			curlCmd.append(" --compressed")
		}
		curlCmd.append(" \"").append(url.escape()).append('"')

		log("---cURL (" + url + ")")
		log(curlCmd.toString())
	}

	/**
	 * Reads at most [MAX_LOGGED_BODY] bytes, skips uploads entirely and masks secrets: an OAuth
	 * refresh request would otherwise print the client secret, and a backup upload would be
	 * buffered in full.
	 */
	private fun readBody(body: RequestBody): String? {
		val contentType = body.contentType()
		if (contentType != null && (
				contentType.type == "multipart" || contentType.subtype == "form-data"
			)
		) {
			return null
		}
		val length = body.contentLength()
		if (length > MAX_LOGGED_BODY) {
			return null
		}
		return runCatching {
			val buffer = Buffer()
			body.writeTo(buffer)
			val charset = contentType?.charset() ?: Charsets.UTF_8
			buffer.readString(charset).maskSecrets()
		}.getOrNull()
	}

	private fun String.maskSecrets() = secretRegex.replace(this) { match ->
		match.groupValues[1] + match.groupValues[2] + "***"
	}

	private fun String.redactUrl() = botTokenRegex.replace(this) { match ->
		match.groupValues[1] + "***"
	}

	private fun String.escape() = replace(escapeRegex) { match ->
		"\\" + match.value
	}

	private fun log(msg: String) {
		Log.d("CURL", msg)
	}

	private companion object {

		const val MAX_LOGGED_BODY = 4096L
	}
}
