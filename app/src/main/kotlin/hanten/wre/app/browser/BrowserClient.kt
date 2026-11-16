package hanten.wre.app.browser

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import hanten.wre.app.core.network.webview.adblock.AdBlock
import java.io.ByteArrayInputStream

open class BrowserClient(
	private val callback: BrowserCallback,
	private val adBlock: AdBlock?,
) : WebViewClient() {

	@Volatile
	var isAdBlockEnabled: Boolean = true

	@Volatile
	private var currentUrl: String? = null

	/**
	 * https://stackoverflow.com/questions/57414530/illegalstateexception-reasonphrase-cant-be-empty-with-android-webview
	 */

	override fun onPageFinished(webView: WebView, url: String) {
		super.onPageFinished(webView, url)
		currentUrl = url
		callback.onLoadingStateChanged(isLoading = false)
	}

	override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
		super.onPageStarted(view, url, favicon)
		currentUrl = url
		callback.onLoadingStateChanged(isLoading = true)
	}

	override fun onReceivedError(
		view: WebView?,
		request: WebResourceRequest?,
		error: WebResourceError?,
	) {
		super.onReceivedError(view, request, error)
		// Drop the spinner on main-frame failures (DNS, certificate, offline,
		// 4xx/5xx) too, since onPageFinished does not always fire after an
		// error and the BrowserCallback would otherwise stay stuck on
		// isLoading = true.
		if (request?.isForMainFrame == true) {
			callback.onLoadingStateChanged(isLoading = false)
		}
	}

	override fun onPageCommitVisible(view: WebView, url: String) {
		super.onPageCommitVisible(view, url)
		currentUrl = url
		callback.onTitleChanged(view.title.orEmpty(), url)
	}

	override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
		super.doUpdateVisitedHistory(view, url, isReload)
		currentUrl = url
		callback.onHistoryChanged()
	}

	@WorkerThread
	@Deprecated("Deprecated in Java")
	override fun shouldInterceptRequest(
		view: WebView?,
		url: String?
	): WebResourceResponse? = if (!isAdBlockEnabled || url.isNullOrEmpty() || adBlock?.shouldLoadUrl(url, view?.getUrlSafe()) ?: true) {
		super.shouldInterceptRequest(view, url)
	} else {
		emptyResponse()
	}

	@WorkerThread
	override fun shouldInterceptRequest(
		view: WebView?,
		request: WebResourceRequest?
	): WebResourceResponse? =
		if (!isAdBlockEnabled || request == null || adBlock?.shouldLoadUrl(request.url.toString(), view?.getUrlSafe()) ?: true) {
			super.shouldInterceptRequest(view, request)
		} else {
			emptyResponse()
		}

	private fun emptyResponse(): WebResourceResponse =
		WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))

	@AnyThread
	private fun WebView.getUrlSafe(): String? = currentUrl
}
