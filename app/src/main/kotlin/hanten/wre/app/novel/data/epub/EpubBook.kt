package hanten.wre.app.novel.data.epub

data class EpubChapter(
	val id: Long,
	val title: String,
	val href: String,
	val index: Int,
)

data class EpubBook(
	val id: Long,
	val title: String,
	val author: String?,
	val description: String?,
	val coverHref: String?,
	val chapters: List<EpubChapter>,
	val opfDir: String,
)