package hanten.wre.app.core.exceptions

import hanten.wre.app.details.ui.pager.EmptyMangaReason
import hanten.wre.app.parsers.model.Manga

class EmptyMangaException(
    val reason: EmptyMangaReason?,
    val manga: Manga,
    cause: Throwable?
) : IllegalStateException(cause)
