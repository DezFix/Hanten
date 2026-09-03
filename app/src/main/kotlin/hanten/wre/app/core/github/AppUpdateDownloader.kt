package hanten.wre.app.core.github

import hanten.wre.app.core.network.BaseHttpClient
import hanten.wre.app.core.util.ext.printStackTraceDebug
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateDownloader @Inject constructor(
	@BaseHttpClient private val okHttp: OkHttpClient,
) {

	suspend fun downloadUpdate(
		url: String,
		destFile: File,
		onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
	): File = suspendCancellableCoroutine { cont ->
		val call = okHttp.newCall(
			Request.Builder().get().url(url).build(),
		)
		cont.invokeOnCancellation { call.cancel() }
		try {
			call.execute().use { response ->
				if (!response.isSuccessful) {
					throw IOException("HTTP ${response.code}")
				}
				val body = response.body ?: throw IOException("Empty response body")
				val total = body.contentLength().takeIf { it > 0 }
				destFile.parentFile?.mkdirs()
				var downloaded = 0L
				body.byteStream().use { input ->
					FileOutputStream(destFile).use { output ->
						val buffer = ByteArray(BUFFER_SIZE)
						while (true) {
							if (!cont.isActive) {
								throw java.util.concurrent.CancellationException()
							}
							val read = input.read(buffer)
							if (read < 0) break
							output.write(buffer, 0, read)
							downloaded += read
							onProgress(downloaded, total)
						}
					}
				}
			}
			if (cont.isActive) {
				cont.resumeWith(Result.success(destFile))
			}
		} catch (e: Throwable) {
			destFile.delete()
			if (cont.isActive) {
				cont.resumeWith(Result.failure(e))
			} else {
				e.printStackTraceDebug("AppUpdateDownloader::downloadUpdate")
			}
		}
	}

	private companion object {
		const val BUFFER_SIZE = 64 * 1024
	}
}
