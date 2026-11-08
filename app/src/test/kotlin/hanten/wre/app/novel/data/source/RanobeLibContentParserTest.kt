package hanten.wre.app.novel.data.source

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RanobeLibContentParserTest {

	@Test
	fun convertsParagraphsAndMarks() {
		val content = """
			{"type":"doc","content":[
				{"type":"paragraph","content":[
					{"type":"text","text":"Hello "},
					{"type":"text","text":"world","marks":[{"type":"bold"}]},
					{"type":"text","text":" & <tags>"}
				]},
				{"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"Quote"}]}]}
			]}
		""".trimIndent()

		val html = RanobeLibContentParser.toHtml(content, null)

		assertEquals("<p>Hello <strong>world</strong> &amp; &lt;tags&gt;</p><blockquote><p>Quote</p></blockquote>", html)
	}

	@Test
	fun convertsNestedJsonObject() {
		val root = JSONObject(
			"""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Text"}]}]}""",
		)

		assertEquals("<p>Text</p>", RanobeLibContentParser.toHtml(root, null))
	}

	@Test
	fun convertsHorizontalRule() {
		val content = """{"type":"doc","content":[{"type":"horizontalRule"}]}"""

		assertEquals("<hr>", RanobeLibContentParser.toHtml(content, null))
	}

	@Test
	fun resolvesImageAttachments() {
		val content = """
			{"type":"doc","content":[
				{"type":"image","attrs":{"description":"Cover","images":[{"image":"abc-123"}]}}
			]}
		""".trimIndent()
		val attachments = JSONObject(
			"""{"name":"abc-123","extension":"jpg","url":"/uploads/ranobe/1/chapters/2/abc-123.jpg"}""",
		)

		val html = RanobeLibContentParser.toHtml(content, attachments)

		assertTrue(html.contains("src=\"https://ranobelib.me/uploads/ranobe/1/chapters/2/abc-123.jpg\""))
		assertTrue(html.contains("<figcaption>Cover</figcaption>"))
	}

	@Test
	fun keepsPlainTextUntouched() {
		assertEquals("plain text", RanobeLibContentParser.toHtml("plain text", null))
	}

	@Test
	fun convertsJsonSummaryToPlainText() {
		val summary = """
			{"type":"doc","content":[
				{"type":"paragraph","content":[{"type":"text","text":"First line"}]},
				{"type":"paragraph","content":[{"type":"text","text":"Second line"}]}
			]}
		""".trimIndent()

		assertEquals("First line Second line", RanobeLibContentParser.toPlainText(summary))
	}
}
