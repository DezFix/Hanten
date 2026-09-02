package hanten.wre.app.list.ui.size

import android.view.View
import android.widget.TextView
import hanten.wre.app.history.ui.util.ReadingProgressView

interface ItemSizeResolver {

	val cellWidth: Int

	fun attachToView(
		view: View,
		textView: TextView?,
		progressView: ReadingProgressView?,
	)
}
