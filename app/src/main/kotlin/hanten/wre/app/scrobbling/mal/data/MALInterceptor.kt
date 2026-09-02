package hanten.wre.app.scrobbling.mal.data

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import hanten.wre.app.core.network.CommonHeaders
import hanten.wre.app.parsers.util.mimeType
import hanten.wre.app.parsers.util.parseHtml
import hanten.wre.app.scrobbling.common.data.ScrobblerStorage
import hanten.wre.app.scrobbling.common.domain.ScrobblerAuthRequiredException
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import java.net.HttpURLConnection

private const val JSON = "application/json"
private const val HTML = "text/html"

class MALInterceptor(private val storage: ScrobblerStorage) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val sourceRequest = chain.request()
		val request = sourceRequest.newBuilder()
		request.header(CommonHeaders.CONTENT_TYPE, JSON)
		request.header(CommonHeaders.ACCEPT, JSON)
		val isAuthRequest = sourceRequest.url.pathSegments.contains("oauth")
		if (!isAuthRequest) {
			storage.accessToken?.let {
				request.header(CommonHeaders.AUTHORIZATION, "Bearer $it")
			}
		}
		val response = chain.proceed(request.build())
		if (!isAuthRequest && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
			throw ScrobblerAuthRequiredException(ScrobblerService.MAL)
		}
		if (response.mimeType == HTML) {
			throw IOException(response.parseHtml().title())
		}
		return response
	}

}
