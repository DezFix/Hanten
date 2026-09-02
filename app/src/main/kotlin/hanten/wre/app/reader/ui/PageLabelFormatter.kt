package hanten.wre.app.reader.ui

import com.google.android.material.slider.LabelFormatter
import hanten.wre.app.parsers.util.format

class PageLabelFormatter : LabelFormatter {

	override fun getFormattedValue(value: Float): String {
		return (value + 1).format(0)
	}
}
