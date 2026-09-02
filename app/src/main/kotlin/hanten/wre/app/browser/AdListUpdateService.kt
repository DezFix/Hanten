package hanten.wre.app.browser

import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.core.network.webview.adblock.AdBlock
import hanten.wre.app.core.ui.CoroutineIntentService
import javax.inject.Inject

@AndroidEntryPoint
class AdListUpdateService : CoroutineIntentService() {

	@Inject
	lateinit var updater: AdBlock.Updater

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		updater.updateList()
	}

	override fun IntentJobContext.onError(error: Throwable) = Unit
}
