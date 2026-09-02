package hanten.wre.app.mihon.model

import hanten.wre.app.mihon.parsers.model.ContentSource
import hanten.wre.app.mihon.parsers.model.ContentType
import hanten.wre.app.parsers.model.MangaParserSource

data class KotatsuParserSource(
    val mangaSource: MangaParserSource
) : ContentSource {
    override val name: String get() = mangaSource.name
    override val locale: String get() = mangaSource.locale
    override val contentType: ContentType get() = when (mangaSource.contentType) {
        hanten.wre.app.parsers.model.ContentType.MANGA -> ContentType.MANGA
        hanten.wre.app.parsers.model.ContentType.HENTAI -> ContentType.HENTAI_MANGA
        hanten.wre.app.parsers.model.ContentType.COMICS -> ContentType.COMICS
        hanten.wre.app.parsers.model.ContentType.OTHER -> ContentType.OTHER
        hanten.wre.app.parsers.model.ContentType.MANHWA -> ContentType.MANHWA
        hanten.wre.app.parsers.model.ContentType.MANHUA -> ContentType.MANHUA
        hanten.wre.app.parsers.model.ContentType.NOVEL -> ContentType.NOVEL
        hanten.wre.app.parsers.model.ContentType.ONE_SHOT -> ContentType.ONE_SHOT
        hanten.wre.app.parsers.model.ContentType.DOUJINSHI -> ContentType.DOUJINSHI
        hanten.wre.app.parsers.model.ContentType.IMAGE_SET -> ContentType.IMAGE_SET
        hanten.wre.app.parsers.model.ContentType.ARTIST_CG -> ContentType.ARTIST_CG
        hanten.wre.app.parsers.model.ContentType.GAME_CG -> ContentType.GAME_CG
    }
    val isBroken: Boolean get() = mangaSource.isBroken
}
