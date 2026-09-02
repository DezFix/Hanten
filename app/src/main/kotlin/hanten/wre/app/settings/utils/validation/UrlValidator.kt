package hanten.wre.app.settings.utils.validation

import android.webkit.URLUtil
import hanten.wre.app.R
import hanten.wre.app.core.util.EditTextValidator

class UrlValidator : EditTextValidator() {

	override fun validate(text: String): ValidationResult {
		val trimmed = text.trim()
		if (trimmed.isEmpty()) {
			return ValidationResult.Success
		}
		return if (!isValidUrl(trimmed)) {
			ValidationResult.Failed(context.getString(R.string.invalid_server_address_message))
		} else {
			ValidationResult.Success
		}
	}

	private fun isValidUrl(str: String): Boolean {
		return URLUtil.isValidUrl(str) || DomainValidator.isValidDomain(str)
	}
}
