package hanten.wre.app.core.ui.util

import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.core.util.ext.processLifecycleScope
import hanten.wre.app.parsers.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface ReversibleHandle {

	suspend fun reverse()
}

fun ReversibleHandle.reverseAsync() = processLifecycleScope.launch(Dispatchers.IO, CoroutineStart.ATOMIC) {
	runCatchingCancellable {
		withContext(NonCancellable) {
			reverse()
		}
	}.onFailure {
		it.printStackTraceDebug("ReversibleHandle::reverseAsync")
	}
}
