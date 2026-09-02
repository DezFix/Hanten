package hanten.wre.app.core.exceptions

class SyncApiException(
	message: String,
	val code: Int,
) : RuntimeException(message)
