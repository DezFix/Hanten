package hanten.wre.app.core.parser.favicon

import android.net.Uri
import hanten.wre.app.parsers.model.MangaSource

const val URI_SCHEME_FAVICON = "favicon"

fun MangaSource.faviconUri(): Uri = Uri.fromParts(URI_SCHEME_FAVICON, name, null)