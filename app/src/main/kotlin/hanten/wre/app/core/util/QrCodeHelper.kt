package hanten.wre.app.core.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import hanten.wre.app.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Beta: QR code for manga sharing. Encodes the short in-app link
 * (dezfix.github.io) — any camera app opens it, and the app itself
 * picks it up via deep-link intent filters. No in-app scanner needed.
 */
object QrCodeHelper {

	suspend fun encode(text: String, sizePx: Int = 512): Bitmap? = withContext(Dispatchers.Default) {
		runCatchingCancellable {
			require(text.isNotBlank())
			val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
			val pixels = IntArray(sizePx * sizePx)
			for (y in 0 until sizePx) {
				val offset = y * sizePx
				for (x in 0 until sizePx) {
					pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
				}
			}
			Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.RGB_565)
		}.getOrNull()
	}
}
