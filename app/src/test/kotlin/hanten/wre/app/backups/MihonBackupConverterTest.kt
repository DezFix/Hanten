package hanten.wre.app.backups

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import hanten.wre.app.backups.data.mihon.MihonBackup
import hanten.wre.app.backups.data.mihon.MihonBackupConverter
import hanten.wre.app.backups.data.mihon.MihonCategory
import hanten.wre.app.backups.data.mihon.MihonChapter
import hanten.wre.app.backups.data.mihon.MihonHistory
import hanten.wre.app.backups.data.mihon.MihonManga
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

@OptIn(ExperimentalSerializationApi::class)
class MihonBackupConverterTest {

	private val backup = MihonBackup(
		mangas = listOf(
			MihonManga(
				source = 42L,
				url = "/manga/one",
				title = "One",
				status = 2,
				dateAdded = 1_000L,
				categories = listOf(5L),
				chapters = listOf(
					MihonChapter(url = "/c/1", read = true, chapterNumber = 1f),
					MihonChapter(url = "/c/2", lastPageRead = 7, chapterNumber = 2f),
				),
				history = listOf(MihonHistory(url = "/c/2", lastRead = 5_000L)),
			),
			MihonManga(source = 42L, url = "/manga/two", title = "Two"),
			MihonManga(source = 7L, url = "/manga/gone", title = "Not in library", favorite = false),
		),
		categories = listOf(MihonCategory(name = "Isekai", order = 5L)),
	)

	private fun tachibk(): ByteArray {
		val raw = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup)
		val out = ByteArrayOutputStream()
		GZIPOutputStream(out).use { it.write(raw) }
		return out.toByteArray()
	}

	@Test
	fun detectsGzipButNotZip() {
		assertTrue(MihonBackupConverter.isMihonBackup(BufferedInputStream(ByteArrayInputStream(tachibk()))))
		val zip = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
		assertFalse(MihonBackupConverter.isMihonBackup(BufferedInputStream(ByteArrayInputStream(zip))))
	}

	@Test
	fun convertsToHantenBackup() {
		val out = ByteArrayOutputStream()
		val result = MihonBackupConverter.convert(ByteArrayInputStream(tachibk()), out, "From Mihon")
		assertEquals(3, result.titles)
		assertEquals(2, result.favourites)
		assertEquals(1, result.history)

		val entries = readZip(out.toByteArray())
		val categories = JSONArray(entries.getValue("categories"))
		// "Isekai" plus the folder for titles without a category
		assertEquals(2, categories.length())
		assertEquals("Isekai", categories.getJSONObject(0).getString("title"))
		assertEquals("From Mihon", categories.getJSONObject(1).getString("title"))

		val favourites = JSONArray(entries.getValue("favourites"))
		assertEquals(2, favourites.length())
		val first = favourites.getJSONObject(0)
		assertEquals(1L, first.getLong("category_id"))
		val manga = first.getJSONObject("manga")
		assertEquals("MIHON_42", manga.getString("source"))
		assertEquals(MihonBackupConverter.mangaId("MIHON_42", "/manga/one"), manga.getLong("id"))
		assertEquals("FINISHED", manga.getString("state"))

		val history = JSONArray(entries.getValue("history")).getJSONObject(0)
		assertEquals(MihonBackupConverter.chapterId("MIHON_42", "/c/2"), history.getLong("chapter_id"))
		assertEquals(7, history.getInt("page"))
		assertEquals(5_000L, history.getLong("updated_at"))
		assertEquals(0.5f, history.getDouble("percent").toFloat(), 0.001f)
		assertTrue(entries.containsKey("index"))
	}

	private fun readZip(bytes: ByteArray): Map<String, String> {
		val result = HashMap<String, String>()
		ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
			var entry = zip.nextEntry
			while (entry != null) {
				result[entry.name] = zip.readBytes().decodeToString()
				entry = zip.nextEntry
			}
		}
		return result
	}
}
