package hanten.wre.app.scrobbling.shikimori.data

import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import hanten.wre.app.core.network.CommonHeaders
import hanten.wre.app.scrobbling.common.data.ScrobblerStorage
import hanten.wre.app.scrobbling.common.domain.ScrobblerAuthRequiredException
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService
import java.net.HttpURLConnection

private const val USER_AGENT_SHIKIMORI = "Hanten"

class ShikimoriInterceptor(private val storage: ScrobblerStorage) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val sourceRequest = chain.request()
		val request = sourceRequest.newBuilder()
		request.header(CommonHeaders.USER_AGENT, USER_AGENT_SHIKIMORI)
		val isAuthRequest = sourceRequest.url.pathSegments.contains("oauth")
		if (!isAuthRequest) {
			storage.accessToken?.let {
				request.header(CommonHeaders.AUTHORIZATION, "Bearer $it")
			}
		}
		val response = chain.proceed(request.build())
		if (!isAuthRequest && response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
			throw ScrobblerAuthRequiredException(ScrobblerService.SHIKIMORI)
		}
		if (!response.isSuccessful && !response.isRedirect) {
			val details = runCatching {
				response.peekBody(MAX_ERROR_BODY).string()
			}.getOrNull()?.takeIf { it.isNotBlank() }
			val message = listOfNotNull(
				response.code.toString(),
				response.message.takeIf { it.isNotBlank() },
				details,
			).joinToString(" ")
			throw IOException(message)
		}
		return response
	}

	private companion object {

		const val MAX_ERROR_BODY = 512L
	}
}
