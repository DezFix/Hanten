package hanten.wre.app.novel.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Some novel sources (RanobeLib) return chapter text as a ProseMirror-like JSON
 * document instead of HTML. Rendering that JSON directly shows braces and tags in
 * the reader, so it is converted to a small safe HTML subset here.
 *
 * [normalize] is the single entry point used by the reader and by offline downloads.
 */
object NovelContentParser {

	private const val FALLBACK_BASE_URL = "https://ranobelib.me"

	fun normalize(raw: String, baseUrl: String? = null): String {
		val trimmed = raw.trim()
		if (trimmed.isEmpty()) return ""
		if (trimmed.startsWith("<")) return trimmed
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			val html = toHtml(trimmed, null, baseUrl)
			if (html.isNotBlank() && html != trimmed) return html
		}
		return trimmed
			.split(Regex("\\n\\s*\\n"))
			.filter { it.isNotBlank() }
			.joinToString("") { "<p>${escapeHtml(it.trim())}</p>" }
	}

	fun toHtml(content: Any?, attachments: Any?, baseUrl: String? = null): String {
		val root = when (content) {
			is JSONObject -> content
			is JSONArray -> JSONObject().put("type", "doc").put("content", content)
			is String -> parseJson(content)
			else -> null
		} ?: return (content as? String).orEmpty()

		val images = collectAttachments(attachments, baseUrl)
		return buildString {
			renderNode(root, images, this, baseUrl)
		}.trim()
	}

	fun toPlainText(content: Any?): String? {
		val html = toHtml(content, null)
		if (html.isBlank()) return null
		return Jsoup.parse(html).text().trim().takeIf { it.isNotBlank() }
	}

	private fun parseJson(raw: String): JSONObject? {
		val trimmed = raw.trim()
		if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
		return runCatching {
			if (trimmed.startsWith("[")) {
				JSONObject().put("type", "doc").put("content", JSONArray(trimmed))
			} else {
				JSONObject(trimmed)
			}
		}.getOrNull()
	}

	private fun renderNode(
		node: JSONObject,
		images: Map<String, String>,
		out: StringBuilder,
		baseUrl: String?,
	) {
		when (node.optString("type")) {
			"doc" -> renderChildren(node, images, out, baseUrl)
			"paragraph" -> {
				out.append("<p>")
				renderChildren(node, images, out, baseUrl)
				out.append("</p>")
			}

			"blockquote" -> {
				out.append("<blockquote>")
				renderChildren(node, images, out, baseUrl)
				out.append("</blockquote>")
			}

			"heading" -> {
				val level = node.optJSONObject("attrs")?.optInt("level", 2)
					?.coerceIn(1, 6) ?: 2
				out.append("<h").append(level).append('>')
				renderChildren(node, images, out, baseUrl)
				out.append("</h").append(level).append('>')
			}

			"bulletList" -> {
				out.append("<ul>")
				renderChildren(node, images, out, baseUrl)
				out.append("</ul>")
			}

			"orderedList" -> {
				out.append("<ol>")
				renderChildren(node, images, out, baseUrl)
				out.append("</ol>")
			}

			"listItem" -> {
				out.append("<li>")
				renderChildren(node, images, out, baseUrl)
				out.append("</li>")
			}

			"codeBlock" -> {
				out.append("<pre><code>")
				renderChildren(node, images, out, baseUrl)
				out.append("</code></pre>")
			}

			"horizontalRule" -> out.append("<hr>")
			"hardBreak" -> out.append("<br>")
			"text" -> renderText(node, out)
			"image" -> renderImage(node, images, out, baseUrl)
			else -> renderChildren(node, images, out, baseUrl)
		}
	}

	private fun renderChildren(
		node: JSONObject,
		images: Map<String, String>,
		out: StringBuilder,
		baseUrl: String?,
	) {
		val children = node.optJSONArray("content") ?: return
		for (i in 0 until children.length()) {
			children.optJSONObject(i)?.let { renderNode(it, images, out, baseUrl) }
		}
	}

	private fun renderText(node: JSONObject, out: StringBuilder) {
		var text = escapeHtml(node.optString("text"))
		val marks = node.optJSONArray("marks") ?: run {
			out.append(text)
			return
		}
		val tags = ArrayList<String>(marks.length())
		for (i in 0 until marks.length()) {
			val mark = marks.optJSONObject(i) ?: continue
			when (mark.optString("type")) {
				"bold", "strong" -> tags += "strong"
				"italic", "em" -> tags += "em"
				"underline" -> tags += "u"
				"strike", "strikethrough" -> tags += "s"
				"code" -> tags += "code"
				"highlight" -> tags += "mark"
				"link" -> {
					val href = mark.optJSONObject("attrs")?.optString("href").orEmpty()
					if (isSafeUrl(href)) {
						text = "<a href=\"${escapeHtml(href)}\">$text</a>"
					}
				}
			}
		}
		for (tag in tags) {
			text = "<$tag>$text</$tag>"
		}
		out.append(text)
	}

	private fun renderImage(
		node: JSONObject,
		images: Map<String, String>,
		out: StringBuilder,
		baseUrl: String?,
	) {
		val attrs = node.optJSONObject("attrs") ?: return
		val description = attrs.optString("description").ifBlank { attrs.optString("alt") }
		val sources = ArrayList<String>(2)
		val imageArray = attrs.optJSONArray("images")
		if (imageArray != null) {
			for (i in 0 until imageArray.length()) {
				val image = imageArray.optJSONObject(i) ?: continue
				val key = image.optString("image").ifBlank { image.optString("name") }
				images[key]?.let(sources::add)
			}
		}
		attrs.optString("src").takeIf { it.isNotBlank() }?.let {
			sources += absolutize(it, baseUrl)
		}
		if (sources.isEmpty()) return

		out.append("<figure class=\"novel-image\">")
		for (src in sources.distinct()) {
			out.append("<img src=\"")
				.append(escapeHtml(src))
				.append("\" alt=\"")
				.append(escapeHtml(description))
				.append("\" loading=\"lazy\">")
		}
		if (description.isNotBlank()) {
			out.append("<figcaption>").append(escapeHtml(description)).append("</figcaption>")
		}
		out.append("</figure>")
	}

	private fun collectAttachments(value: Any?, baseUrl: String?): Map<String, String> {
		val result = HashMap<String, String>()
		when (value) {
			is JSONObject -> {
				if (value.has("url") || value.has("originalUrl")) {
					addAttachment(value, result, baseUrl)
				} else {
					for (key in value.keys()) {
						when (val child = value.opt(key)) {
							is JSONObject -> addAttachment(child, result, baseUrl, key)
							is String -> result[key] = absolutize(child, baseUrl)
						}
					}
				}
			}

			is JSONArray -> {
				for (i in 0 until value.length()) {
					val child = value.optJSONObject(i) ?: continue
					addAttachment(child, result, baseUrl)
				}
			}
		}
		return result
	}

	private fun addAttachment(
		source: JSONObject,
		result: MutableMap<String, String>,
		baseUrl: String?,
		fallbackName: String? = null,
	) {
		val url = source.optString("url").ifBlank { source.optString("originalUrl") }
		if (url.isBlank()) return
		val fullUrl = absolutize(url, baseUrl)
		val names = buildSet {
			source.optString("name").takeIf { it.isNotBlank() }?.let(::add)
			source.optString("filename").substringBeforeLast('.').takeIf { it.isNotBlank() }?.let(::add)
			fallbackName?.takeIf { it.isNotBlank() }?.let(::add)
		}
		for (name in names) {
			result[name] = fullUrl
		}
	}

	private fun absolutize(url: String, baseUrl: String?): String = when {
		url.startsWith("http://") || url.startsWith("https://") -> url
		url.startsWith("//") -> "https:$url"
		else -> (baseUrl ?: FALLBACK_BASE_URL).trimEnd('/') + "/" + url.trimStart('/')
	}

	private fun isSafeUrl(url: String): Boolean {
		val scheme = url.substringBefore(':', "").lowercase()
		return scheme.isEmpty() || scheme == "http" || scheme == "https" || scheme == "mailto"
	}

	private fun escapeHtml(value: String): String = buildString(value.length) {
		for (char in value) {
			when (char) {
				'&' -> append("&amp;")
				'<' -> append("&lt;")
				'>' -> append("&gt;")
				'"' -> append("&quot;")
				'\'' -> append("&#39;")
				else -> append(char)
			}
		}
	}
}
