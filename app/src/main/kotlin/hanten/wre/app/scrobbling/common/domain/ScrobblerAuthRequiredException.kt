package hanten.wre.app.scrobbling.common.domain

import okio.IOException
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerService

class ScrobblerAuthRequiredException(
	val scrobbler: ScrobblerService,
) : IOException()
