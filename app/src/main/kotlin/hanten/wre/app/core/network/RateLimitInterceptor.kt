package hanten.wre.app.core.network

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import hanten.wre.app.parsers.exception.TooManyRequestExceptions
import java.io.InterruptedIOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Global, reactive rate-limit protection for every HTTP client built on top of
 * the base client (tracker, downloads, reader, search): no per-source rules.
 *
 * On 429 the host is blocked until the server-provided Retry-After (capped).
 * Subsequent requests to a blocked host either wait out short blocks or fail
 * fast with [TooManyRequestExceptions] on long ones, preserving the previous
 * failure semantics for absurd values.
 */
class RateLimitInterceptor(
	private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) : Interceptor {

	private val blockedUntil = ConcurrentHashMap<String, Long>()

	override fun intercept(chain: Interceptor.Chain): Response {
		val host = chain.request().url.host
		val now = nowMillis()
		val wait = (blockedUntil[host] ?: 0L) - now
		if (wait > 0) {
			if (wait > MAX_WAIT_MILLIS) {
				throw TooManyRequestExceptions(
					url = chain.request().url.toString(),
					retryAfter = wait,
				)
			}
			try {
				Thread.sleep(wait)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				throw InterruptedIOException("Interrupted while waiting for rate limit on $host")
			}
		} else if (wait < 0) {
			// Stale entry, lazy cleanup (negative means no block was ever set)
			if (blockedUntil[host] != null && blockedUntil[host]!! <= now) {
				blockedUntil.remove(host)
			}
		}
		val response = chain.proceed(chain.request())
		if (response.code == 429) {
			val request = response.request
			response.closeQuietly()
			val retryAfter = response.header(CommonHeaders.RETRY_AFTER)?.parseRetryAfter() ?: 0L
			if (retryAfter > 0) {
				blockedUntil[host] = now + retryAfter.coerceAtMost(MAX_BLOCK_MILLIS)
			}
			throw TooManyRequestExceptions(
				url = request.url.toString(),
				retryAfter = retryAfter,
			)
		}
		return response
	}

	private fun String.parseRetryAfter(): Long {
		return toLongOrNull()?.let { TimeUnit.SECONDS.toMillis(it) }
			?: runCatching {
				ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
			}.getOrDefault(0L)
	}

	private companion object {

		// Max time a worker thread sleeps inside the interceptor; longer blocks fail fast
		const val MAX_WAIT_MILLIS = 60_000L

		// Max remembered block per host (server values beyond that are capped)
		const val MAX_BLOCK_MILLIS = 15 * 60_000L
	}
}
