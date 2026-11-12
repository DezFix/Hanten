package hanten.wre.app.novel.data.offline

data class CachedNovel(
	val id: Long,
	val title: String,
	val source: String,
	val chapterCount: Int,
	val sizeBytes: Long,
	val isDownloaded: Boolean = false,
)

data class CachedChapter(
	val chapterId: Long,
	val title: String,
	val sizeBytes: Long,
	val isDownloaded: Boolean = true,
)
