package hanten.wre.app.core.ui.image

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.withStyledAttributes
import coil3.Image
import coil3.asImage
import coil3.request.Disposable
import coil3.request.ImageRequest
import hanten.wre.app.R
import hanten.wre.app.core.exceptions.resolve.CaptchaHandler.Companion.suppressCaptchaErrors
import hanten.wre.app.core.image.CoilImageView
import hanten.wre.app.core.parser.favicon.faviconUri
import hanten.wre.app.core.util.ext.isAnimationsEnabled
import hanten.wre.app.core.util.ext.mangaSourceExtra
import hanten.wre.app.parsers.model.MangaSource

class FaviconView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	@AttrRes defStyleAttr: Int = 0,
) : CoilImageView(context, attrs, defStyleAttr) {

	@StyleRes
	private var iconStyle: Int = R.style.FaviconDrawable

	init {
		context.withStyledAttributes(attrs, R.styleable.FaviconView, defStyleAttr) {
			iconStyle = getResourceId(R.styleable.FaviconView_iconStyle, iconStyle)
		}
		if (isInEditMode) {
			setImageDrawable(
				FaviconDrawable(
					context = context,
					styleResId = iconStyle,
					name = context.getString(R.string.app_name).random().toString(),
				),
			)
		}
	}

	fun setImageAsync(mangaSource: MangaSource): Disposable {
		val fallbackFactory: (ImageRequest) -> Image? = {
			FaviconDrawable(context, iconStyle, mangaSource.name).asImage()
		}
		val placeholderFactory: (ImageRequest) -> Image? = if (context.isAnimationsEnabled) {
			{ AnimatedFaviconDrawable(context, iconStyle, mangaSource.name).asImage() }
		} else {
			fallbackFactory
		}
		return enqueueRequest(
			newRequestBuilder()
				.data(mangaSource.faviconUri())
				.error(fallbackFactory)
				.fallback(fallbackFactory)
				.placeholder(placeholderFactory)
				.mangaSourceExtra(mangaSource)
				.suppressCaptchaErrors()
				.build(),
		)
	}
}
