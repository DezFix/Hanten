package hanten.wre.app.history.domain.model

import hanten.wre.app.core.model.MangaHistory
import hanten.wre.app.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory
)
