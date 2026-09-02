package hanten.wre.app.details.ui.pager.pages

import coil3.key.Keyer
import coil3.request.Options
import hanten.wre.app.parsers.model.MangaPage

class MangaPageKeyer : Keyer<MangaPage> {

	override fun key(data: MangaPage, options: Options) = data.url
}
