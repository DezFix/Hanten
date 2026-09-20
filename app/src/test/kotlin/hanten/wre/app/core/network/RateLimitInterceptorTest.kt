package hanten.wre.app.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import hanten.wre.app.parsers.exception.TooManyRequestExceptions
import java.util.concurrent.TimeUnit

class RateLimitInterceptorTest {

	private var now = 1_000_000L
	private val interceptor = RateLimitInterceptor(nowMillis = { now })

	@Test
	fun test429ThrowsAndBlocksHost() {
		val request = Request.Builder().url("https://example.com/manga").build()
		val response429 = Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(429)
			.message("Too Many Requests")
			.header(CommonHeaders.RETRY_AFTER, "1")
			.body("".toResponseBody("text/plain".toMediaType()))
			.build()
		val chain = FakeChain(request) { response429 }
		val error = runCatching { interceptor.intercept(chain) }.exceptionOrNull()
		assertTrue(error is TooManyRequestExceptions)
		assertEquals(1, chain.proceedCalls)
	}

	@Test
	fun testBlockedHostWaits() {
		val request = Request.Builder().url("https://wait.example.com/x").build()
		val response429 = Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(429)
			.message("Too Many Requests")
			.header(CommonHeaders.RETRY_AFTER, "1")
			.body("".toResponseBody("text/plain".toMediaType()))
			.build()
		val response200 = Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(200)
			.message("OK")
			.body("".toResponseBody("text/plain".toMediaType()))
			.build()
		var calls = 0
		val chain = FakeChain(request) {
			calls++
			if (calls == 1) response429 else response200
		}
		runCatching { interceptor.intercept(chain) }
		val start = System.nanoTime()
		val result = interceptor.intercept(chain)
		val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
		assertEquals(200, result.code)
		// Retry-After: 1s block must be (mostly) waited out
		assertTrue("waited only ${elapsedMs}ms", elapsedMs >= 800L)
	}

	@Test
	fun testLongBlockFailsFast() {
		val request = Request.Builder().url("https://failfast.example.com/x").build()
		val response429 = Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(429)
			.message("Too Many Requests")
			.header(CommonHeaders.RETRY_AFTER, "3600")
			.body("".toResponseBody("text/plain".toMediaType()))
			.build()
		var calls = 0
		val chain = FakeChain(request) {
			calls++
			response429
		}
		runCatching { interceptor.intercept(chain) }
		val start = System.nanoTime()
		val error = runCatching { interceptor.intercept(chain) }.exceptionOrNull()
		val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
		assertTrue(error is TooManyRequestExceptions)
		assertEquals(1, calls)
		// Must not sleep through an hour-long block
		assertTrue("slept ${elapsedMs}ms", elapsedMs < 5_000L)
	}

	private class FakeChain(
		private val req: Request,
		private val responder: () -> Response,
	) : Interceptor.Chain {

		var proceedCalls = 0
			private set

		override fun request(): Request = req

		override fun proceed(request: Request): Response {
			proceedCalls++
			return responder()
		}

		override fun connection() = null

		override fun call() = throw UnsupportedOperationException()

		override fun connectTimeoutMillis() = 0

		override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this

		override fun readTimeoutMillis() = 0

		override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this

		override fun writeTimeoutMillis() = 0

		override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
	}
}
