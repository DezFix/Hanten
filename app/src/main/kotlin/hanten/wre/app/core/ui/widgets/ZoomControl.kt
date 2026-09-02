package hanten.wre.app.core.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.button.MaterialButtonGroup
import hanten.wre.app.R
import hanten.wre.app.databinding.ViewZoomBinding

class ZoomControl @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : MaterialButtonGroup(context, attrs), View.OnClickListener {

	private val binding = ViewZoomBinding.inflate(LayoutInflater.from(context), this)

	var listener: ZoomControlListener? = null

	init {
		binding.buttonZoomIn.setOnClickListener(this)
		binding.buttonZoomOut.setOnClickListener(this)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_zoom_in -> listener?.onZoomIn()
			R.id.button_zoom_out -> listener?.onZoomOut()
		}
	}

	interface ZoomControlListener {

		fun onZoomIn()

		fun onZoomOut()
	}
}
