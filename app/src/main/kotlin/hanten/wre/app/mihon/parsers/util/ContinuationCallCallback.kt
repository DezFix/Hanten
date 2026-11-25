package hanten.wre.app.mihon.parsers.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

internal class ContinuationCallCallback(
    private val call: Call,
    continuation: CancellableContinuation<Response>,
) : Callback, CompletionHandler {

    /**
     * Cleared on cancellation: a cancelled call can wait in OkHttp's ready queue for a long time,
     * and the continuation would keep the whole caller chain (up to views and fragments) alive.
     */
    private val continuationRef = AtomicReference(continuation)

    override fun onResponse(call: Call, response: Response) {
        val continuation = continuationRef.getAndSet(null)
        if (continuation == null) {
            response.closeQuietly()
            return
        }
        continuation.resume(response) { _, value, _ ->
            value.closeQuietly()
        }
    }

    override fun onFailure(call: Call, e: IOException) {
        continuationRef.getAndSet(null)?.resumeWithException(e)
    }

    override fun invoke(cause: Throwable?) {
        continuationRef.set(null)
        runCatching {
            call.cancel()
        }.onFailure { e ->
            cause?.addSuppressed(e)
        }
    }
}
