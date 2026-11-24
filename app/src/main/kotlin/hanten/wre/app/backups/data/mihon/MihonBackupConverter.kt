package hanten.wre.app.backups.data.mihon

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import hanten.wre.app.backups.data.model.BackupIndex
import hanten.wre.app.backups.data.model.CategoryBackup
import hanten.wre.app.backups.data.model.FavouriteBackup
import hanten.wre.app.backups.data.model.HistoryBackup
import hanten.wre.app.backups.data.model.MangaBackup
import hanten.wre.app.backups.data.model.ScrobblingBackup
import hanten.wre.app.backups.domain.BackupSection
import hanten.wre.app.parsers.model.MangaState
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Turns a Mihon / Tachiyomi backup into a regular Hanten backup, so the usual restore dialog and
 * service handle it (section choice, merge, progress) without a second restore path.
 *
 * Tachiyomi extensions run in Hanten as sources named `MIHON_{sourceId}`, and manga/chapter ids are
 * derived from the same urls the extension uses (see MihonDataConverters), so titles land exactly on
 * what the installed extension will load. Without the extension installed the titles are still
 * restored and start working once it is installed.
 */
object MihonBackupConverter {

	private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())

	private val json = Json {
		encodeDefaults = true
		allowSpecialFloatingPointValues = true
	}

	class Result(
		val titles: Int,
		val favourites: Int,
		val categories: Int,
		val history: Int,
		val tracking: Int = 0,
		/** Titles moved, per Mihon source id (the source needs its extension installed in Hanten) */
		val titlesBySource: Map<Long, Int> = emptyMap(),
		/** Source names as stored in the backup */
		val sourceNames: Map<Long, String> = emptyMap(),
	)

	/** True if the stream starts like a gzip file (.tachibk); Hanten's own backups are zip. Keeps the mark. */
	fun isMihonBackup(input: BufferedInputStream): Boolean {
		input.mark(GZIP_MAGIC.size)
		val head = ByteArray(GZIP_MAGIC.size)
		val read = input.read(head)
		input.reset()
		return read == GZIP_MAGIC.size && head.contentEquals(GZIP_MAGIC)
	}

	fun decode(input: InputStream): MihonBackup {
		val bytes = GZIPInputStream(input).use { it.readBytes() }
		@OptIn(ExperimentalSerializationApi::class)
		return ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), bytes)
	}

	fun convert(input: InputStream, output: OutputStream, uncategorizedTitle: String): Result {
		return write(decode(input), output, uncategorizedTitle)
	}

	fun write(backup: MihonBackup, output: OutputStream, uncategorizedTitle: String): Result {
		val now = System.currentTimeMillis()
		// Mihon links titles to categories by the category's "order" value
		val categoryIdByOrder = HashMap<Long, Int>()
		val categories = ArrayList<CategoryBackup>(backup.categories.size + 1)
		backup.categories.sortedBy { it.order }.forEachIndexed { index, c ->
			val id = index + 1
			categoryIdByOrder[c.order] = id
			categories += CategoryBackup(categoryId = id, createdAt = now, sortKey = index, title = c.name)
		}
		var uncategorizedId = -1

		val favourites = ArrayList<FavouriteBackup>()
		val history = ArrayList<HistoryBackup>()
		val scrobbling = ArrayList<ScrobblingBackup>()
		val titlesBySource = HashMap<Long, Int>()
		for ((index, m) in backup.mangas.withIndex()) {
			val sourceName = sourceName(m.source)
			val manga = m.toMangaBackup(sourceName)
			if (m.favorite) {
				var ids = m.categories.mapNotNull { categoryIdByOrder[it] }.distinct()
				if (ids.isEmpty()) {
					if (uncategorizedId < 0) {
						uncategorizedId = categories.size + 1
						categories += CategoryBackup(
							categoryId = uncategorizedId,
							createdAt = now,
							sortKey = categories.size,
							title = uncategorizedTitle,
						)
					}
					ids = listOf(uncategorizedId)
				}
				ids.mapTo(favourites) { categoryId ->
					FavouriteBackup(
						mangaId = manga.id,
						categoryId = categoryId.toLong(),
						sortKey = index,
						createdAt = m.dateAdded.takeIf { it > 0 } ?: now,
						manga = manga,
					)
				}
			}
			val historyItem = m.toHistory(manga, sourceName)
			historyItem?.let(history::add)
			if (m.favorite || historyItem != null) {
				// trackers need the title row, which only favourites and history create
				m.tracking.mapNotNullTo(scrobbling) { it.toScrobbling(manga.id) }
				titlesBySource[m.source] = (titlesBySource[m.source] ?: 0) + 1
			}
		}

		ZipOutputStream(output).use { zip ->
			zip.writeEntry(BackupSection.INDEX, BackupIndex.serializer(), listOf(BackupIndex()))
			zip.writeEntry(BackupSection.CATEGORIES, CategoryBackup.serializer(), categories)
			zip.writeEntry(BackupSection.FAVOURITES, FavouriteBackup.serializer(), favourites)
			zip.writeEntry(BackupSection.HISTORY, HistoryBackup.serializer(), history)
			if (scrobbling.isNotEmpty()) {
				zip.writeEntry(BackupSection.SCROBBLING, ScrobblingBackup.serializer(), scrobbling)
			}
		}
		return Result(
			titles = backup.mangas.size,
			favourites = favourites.distinctBy { it.mangaId }.size,
			categories = categories.size,
			history = history.size,
			tracking = scrobbling.size,
			titlesBySource = titlesBySource,
			sourceNames = backup.sources.associate { it.sourceId to it.name },
		)
	}

	/**
	 * Mihon tracker link → Hanten scrobbling link. Mihon sync ids: 1 MAL, 2 AniList, 4 Shikimori
	 * (Hanten: 3, 2, 1). Other trackers (Kitsu, MangaUpdates…) Hanten does not have.
	 */
	private fun MihonTracking.toScrobbling(mangaId: Long): ScrobblingBackup? {
		val remoteId = mediaId.takeIf { it > 0 } ?: mediaIdInt.toLong().takeIf { it > 0 } ?: return null
		val (scrobbler, entryId, statusName, ratingScale) = when (syncId) {
			1 -> Quad(3, remoteId.toInt(), malStatus(status), 10f)
			2 -> Quad(2, libraryId.toInt(), aniListStatus(status), 100f)
			4 -> Quad(1, libraryId.toInt(), shikimoriStatus(status), 10f)
			else -> return null
		}
		return ScrobblingBackup(
			scrobbler = scrobbler,
			id = entryId,
			mangaId = mangaId,
			targetId = remoteId,
			status = statusName,
			chapter = lastChapterRead.toInt(),
			comment = null,
			rating = (score / ratingScale).coerceIn(0f, 1f),
		)
	}

	private data class Quad(val scrobbler: Int, val id: Int, val status: String?, val ratingScale: Float)

	// Mihon status codes per tracker → status strings the Hanten scrobblers use
	private fun malStatus(s: Int) = when (s) {
		1, 7 -> "reading"
		2 -> "completed"
		3 -> "on_hold"
		4 -> "dropped"
		6 -> "plan_to_read"
		else -> null
	}

	private fun aniListStatus(s: Int) = when (s) {
		1 -> "CURRENT"
		2 -> "COMPLETED"
		3 -> "PAUSED"
		4 -> "DROPPED"
		5 -> "PLANNING"
		6 -> "REPEATING"
		else -> null
	}

	private fun shikimoriStatus(s: Int) = when (s) {
		1 -> "watching"
		2 -> "completed"
		3 -> "on_hold"
		4 -> "dropped"
		5 -> "planned"
		6 -> "rewatching"
		else -> null
	}

	private fun MihonManga.toMangaBackup(sourceName: String) = MangaBackup(
		id = mangaId(sourceName, url),
		title = title.ifBlank { url },
		url = url,
		publicUrl = url,
		coverUrl = thumbnailUrl.orEmpty(),
		state = mangaState(status)?.name,
		authors = listOfNotNull(author, artist).filter { it.isNotBlank() }.distinct()
			.joinToString().ifEmpty { null },
		source = sourceName,
		description = description,
	)

	/** Last read chapter becomes Hanten's history entry for the title. */
	private fun MihonManga.toHistory(manga: MangaBackup, sourceName: String): HistoryBackup? {
		val byUrl = chapters.associateBy { it.url }
		val last = history.maxByOrNull { it.lastRead }
		val chapter = last?.let { byUrl[it.url] }
			?: chapters.filter { it.read || it.lastPageRead > 0 }.maxByOrNull { it.chapterNumber }
			?: return null
		val updatedAt = last?.lastRead?.takeIf { it > 0 } ?: dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis()
		val total = chapters.size
		val readCount = chapters.count { it.read }
		return HistoryBackup(
			mangaId = manga.id,
			createdAt = dateAdded.takeIf { it > 0 } ?: updatedAt,
			updatedAt = updatedAt,
			chapterId = chapterId(sourceName, chapter.url),
			page = if (chapter.read) 0 else chapter.lastPageRead.toInt().coerceAtLeast(0),
			scroll = 0f,
			percent = if (total > 0) readCount.toFloat() / total else -1f,
			chaptersCount = total,
			manga = manga,
		)
	}

	private fun <T> ZipOutputStream.writeEntry(section: BackupSection, serializer: KSerializer<T>, items: List<T>) {
		putNextEntry(ZipEntry(section.entryName))
		write(json.encodeToString(ListSerializer(serializer), items).toByteArray())
		closeEntry()
	}

	fun sourceName(sourceId: Long) = "MIHON_$sourceId"

	// Must stay identical to generateContentId / generateChapterId in mihon/model/MihonDataConverters.kt
	fun mangaId(sourceName: String, url: String): Long = "$sourceName|$url".hashCode().toLong() and Long.MAX_VALUE

	fun chapterId(sourceName: String, url: String): Long =
		"$sourceName|chapter|$url".hashCode().toLong() and Long.MAX_VALUE

	/** SManga status codes. */
	private fun mangaState(status: Int): MangaState? = when (status) {
		1 -> MangaState.ONGOING
		2, 4 -> MangaState.FINISHED
		3 -> MangaState.RESTRICTED
		5 -> MangaState.ABANDONED
		6 -> MangaState.PAUSED
		else -> null
	}
}
