package hanten.wre.app.novel.ui.reader

import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NovelReaderBridge(
	private val scope: CoroutineScope,
	private val onScroll: (percent: Float) -> Unit,
	private val onNearBottomReached: () -> Unit,
	private val onCenterTapped: () -> Unit,
	private val onPreviousChapter: () -> Unit,
	private val onNextChapter: () -> Unit,
	private val onPageChange: (currentPage: Int, totalPages: Int) -> Unit,
	private val onActiveChapter: (index: Int) -> Unit,
) {

	@JavascriptInterface
	fun onScrollProgress(percent: Float) {
		scope.launch(Dispatchers.Main) {
			onScroll(percent)
		}
	}

	@JavascriptInterface
	fun onNearBottom() {
		scope.launch(Dispatchers.Main) {
			onNearBottomReached()
		}
	}

	@JavascriptInterface
	fun onCenterTap() {
		scope.launch(Dispatchers.Main) {
			onCenterTapped()
		}
	}

	@JavascriptInterface
	fun requestPreviousChapter() {
		scope.launch(Dispatchers.Main) {
			onPreviousChapter()
		}
	}

	@JavascriptInterface
	fun requestNextChapter() {
		scope.launch(Dispatchers.Main) {
			onNextChapter()
		}
	}

	@JavascriptInterface
	fun onPageChanged(currentPage: Int, totalPages: Int) {
		scope.launch(Dispatchers.Main) {
			onPageChange(currentPage, totalPages)
		}
	}

	@JavascriptInterface
	fun onActiveChapter(index: Int) {
		scope.launch(Dispatchers.Main) {
			onActiveChapter(index)
		}
	}

	@JavascriptInterface
	fun log(msg: String) {
		android.util.Log.d("NovelReaderBridge", msg)
	}
}