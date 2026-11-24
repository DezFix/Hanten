package hanten.wre.app.backups.data.mihon

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/*
 * Read-only mirror of the Mihon / Tachiyomi backup format (.tachibk = gzip + protobuf).
 * Only the fields Hanten can use are declared; protobuf skips everything else, which also keeps
 * backups from forks (TachiyomiSY, J2K, Komikku) readable: they add fields, not change these.
 * Field numbers follow the upstream format, which is Apache 2.0 licensed (see NOTICE).
 */

@Serializable
class MihonBackup(
	@ProtoNumber(1) val mangas: List<MihonManga> = emptyList(),
	@ProtoNumber(2) val categories: List<MihonCategory> = emptyList(),
	@ProtoNumber(101) val sources: List<MihonSource> = emptyList(),
)

@Serializable
class MihonManga(
	@ProtoNumber(1) val source: Long,
	@ProtoNumber(2) val url: String,
	@ProtoNumber(3) val title: String = "",
	@ProtoNumber(4) val artist: String? = null,
	@ProtoNumber(5) val author: String? = null,
	@ProtoNumber(6) val description: String? = null,
	@ProtoNumber(7) val genre: List<String> = emptyList(),
	@ProtoNumber(8) val status: Int = 0,
	@ProtoNumber(9) val thumbnailUrl: String? = null,
	@ProtoNumber(13) val dateAdded: Long = 0,
	@ProtoNumber(16) val chapters: List<MihonChapter> = emptyList(),
	@ProtoNumber(17) val categories: List<Long> = emptyList(),
	@ProtoNumber(18) val tracking: List<MihonTracking> = emptyList(),
	@ProtoNumber(100) val favorite: Boolean = true,
	@ProtoNumber(104) val history: List<MihonHistory> = emptyList(),
)

@Serializable
class MihonChapter(
	@ProtoNumber(1) val url: String,
	@ProtoNumber(2) val name: String = "",
	@ProtoNumber(4) val read: Boolean = false,
	@ProtoNumber(5) val bookmark: Boolean = false,
	@ProtoNumber(6) val lastPageRead: Long = 0,
	@ProtoNumber(9) val chapterNumber: Float = 0f,
	@ProtoNumber(10) val sourceOrder: Long = 0,
)

@Serializable
class MihonCategory(
	@ProtoNumber(1) val name: String,
	@ProtoNumber(2) val order: Long = 0,
)

@Serializable
class MihonHistory(
	@ProtoNumber(1) val url: String,
	@ProtoNumber(2) val lastRead: Long,
)

@Serializable
class MihonTracking(
	@ProtoNumber(1) val syncId: Int,
	@ProtoNumber(2) val libraryId: Long = 0,
	@ProtoNumber(3) val mediaIdInt: Int = 0,
	@ProtoNumber(6) val lastChapterRead: Float = 0f,
	@ProtoNumber(8) val score: Float = 0f,
	@ProtoNumber(9) val status: Int = 0,
	@ProtoNumber(100) val mediaId: Long = 0,
)

@Serializable
class MihonSource(
	@ProtoNumber(1) val name: String = "",
	@ProtoNumber(2) val sourceId: Long,
)
