package hanten.wre.app.mihon.parsers.model

import hanten.wre.app.parsers.model.MangaSource

interface ContentSource : MangaSource {

    override val name: String
    val locale: String
    val contentType: ContentType
}
