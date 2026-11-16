package hanten.wre.app.core.network.imageproxy

import android.util.Log
import androidx.collection.ArraySet
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.HttpStatusException
import hanten.wre.app.BuildConfig
import hanten.wre.app.core.exceptions.CloudFlareBlockedException
import hanten.wre.app.parsers.util.await
import hanten.wre.app.parsers.util.isHttpOrHttps
import hanten.wre.app.parsers.util.runCatchingCancellable
import java.net.HttpURLConnection
import java.util.Collections

abstract class BaseImageProxyInterceptor : ImageProxyInterceptor {

	private val blacklist = Collections.synchronizedSet(ArraySet<String>())

	final override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val request = chain.request
		val url: HttpUrl? = when (val data = request.data) {
			is HttpUrl -> data
			is String -> data.toHttpUrlOrNull()
			else -> null
		}
		if (url == null || !url.isHttpOrHttps || url.host in blacklist) {
			return chain.proceed()
		}
		val newRequest = onInterceptImageRequest(request, url)
		return when (val result = chain.withRequest(newRequest).proceed()) {
			is SuccessResult -> result
			is ErrorResult -> {
				logDebug(result.throwable, newRequest.data)
				chain.proceed().also {
					if (it is SuccessResult && result.throwable.isBlockedByServer()) {
						blacklist.add(url.host)
					}
				}
			}
		}
	}

	final override suspend fun interceptPageRequest(request: Request, okHttp: OkHttpClient): Response {
		val newRequest = onInterceptPageRequest(request)
		val proxyResponse = runCatchingCancellable {
			okHttp.newCall(newRequest).await()
		}.getOrElse { error ->
			logDebug(error, newRequest.url)
			null
		}
		if (proxyResponse != null) {
			if (proxyResponse.isSuccessful) {
				return proxyResponse
			}
			proxyResponse.close()
		}
		// A proxy can return 404/5xx for an image the origin still serves.
		// Fall back to the direct request and let the caller render a page error
		// instead of turning every 404 into a noisy exception with a stack trace.
		val direct = okHttp.newCall(request).await()
		if (!direct.isSuccessful) {
			logStatus(direct.code, request.url)
		}
		return direct
	}

	protected abstract suspend fun onInterceptImageRequest(request: ImageRequest, url: HttpUrl): ImageRequest

	protected abstract suspend fun onInterceptPageRequest(request: Request): Request

	private fun logStatus(code: Int, url: Any) {
		if (BuildConfig.DEBUG) {
			Log.w("ImageProxy", "HTTP $code: $url")
		}
	}

	private fun logDebug(e: Throwable, url: Any) {
		if (BuildConfig.DEBUG) {
			Log.w("ImageProxy", "${e.message}: $url", e)
		}
	}

	private fun Throwable.isBlockedByServer(): Boolean {
		return this is CloudFlareBlockedException
			|| (this is HttpException && response.code == HttpURLConnection.HTTP_FORBIDDEN)
			|| (this is HttpStatusException && statusCode == HttpURLConnection.HTTP_FORBIDDEN)
	}
}
