package hanten.wre.app.list.ui.model

import hanten.wre.app.core.ui.model.MangaOverride
import hanten.wre.app.parsers.model.Manga

data class MangaCompactListModel(
	override val manga: Manga,
	override val override: MangaOverride?,
	val subtitle: String,
	override val counter: Int,
) : MangaListModel()
