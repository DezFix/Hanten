package hanten.wre.app.novel.data.source

import org.json.JSONArray
import org.json.JSONObject

/**
 * RanobeLib returns chapter text as a ProseMirror-like JSON document instead of HTML.
 * Rendering that JSON directly shows tags and braces in the reader, so it is converted
 * to a small safe HTML subset here.
 */
object RanobeLibContentParser {

	private const val BASE_URL = "https://ranobelib.me"

	fun toHtml(content: Any?, attachments: Any?): String {
		val root = when (content) {
			is JSONObject -> content
			is JSONArray -> JSONObject().put("type", "doc").put("content", content)
			is String -> parseJson(content)
			else -> null
		} ?: return (content as? String).orEmpty()

		val images = collectAttachments(attachments)
		return buildString {
			renderNode(root, images, this)
		}.trim()
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

	private fun renderNode(node: JSONObject, images: Map<String, String>, out: StringBuilder) {
		when (node.optString("type")) {
			"doc" -> renderChildren(node, images, out)
			"paragraph" -> {
				out.append("<p>")
				renderChildren(node, images, out)
				out.append("</p>")
			}
			"blockquote" -> {
				out.append("<blockquote>")
				renderChildren(node, images, out)
				out.append("</blockquote>")
			}
			"heading" -> {
				val level = node.optJSONObject("attrs")?.optInt("level", 2)
					?.coerceIn(1, 6) ?: 2
				out.append("<h").append(level).append('>')
				renderChildren(node, images, out)
				out.append("</h").append(level).append('>')
			}
			"bulletList" -> {
				out.append("<ul>")
				renderChildren(node, images, out)
				out.append("</ul>")
			}
			"orderedList" -> {
				out.append("<ol>")
				renderChildren(node, images, out)
				out.append("</ol>")
			}
			"listItem" -> {
				out.append("<li>")
				renderChildren(node, images, out)
				out.append("</li>")
			}
			"codeBlock" -> {
				out.append("<pre><code>")
				renderChildren(node, images, out)
				out.append("</code></pre>")
			}
			"horizontalRule" -> out.append("<hr>")
			"hardBreak" -> out.append("<br>")
			"text" -> renderText(node, out)
			"image" -> renderImage(node, images, out)
			else -> renderChildren(node, images, out)
		}
	}

	private fun renderChildren(node: JSONObject, images: Map<String, String>, out: StringBuilder) {
		val children = node.optJSONArray("content") ?: return
		for (i in 0 until children.length()) {
			children.optJSONObject(i)?.let { renderNode(it, images, out) }
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

	private fun renderImage(node: JSONObject, images: Map<String, String>, out: StringBuilder) {
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
			sources += absolutize(it)
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

	private fun collectAttachments(value: Any?): Map<String, String> {
		val result = HashMap<String, String>()
		when (value) {
			is JSONObject -> {
				if (value.has("url") || value.has("originalUrl")) {
					addAttachment(value, result)
				} else {
					for (key in value.keys()) {
						when (val child = value.opt(key)) {
							is JSONObject -> addAttachment(child, result, key)
							is String -> result[key] = absolutize(child)
						}
					}
				}
			}
			is JSONArray -> {
				for (i in 0 until value.length()) {
					val child = value.optJSONObject(i) ?: continue
					addAttachment(child, result)
				}
			}
		}
		return result
	}

	private fun addAttachment(source: JSONObject, result: MutableMap<String, String>, fallbackName: String? = null) {
		val url = source.optString("url").ifBlank { source.optString("originalUrl") }
		if (url.isBlank()) return
		val fullUrl = absolutize(url)
		val names = buildSet {
			source.optString("name").takeIf { it.isNotBlank() }?.let(::add)
			source.optString("filename").substringBeforeLast('.').takeIf { it.isNotBlank() }?.let(::add)
			fallbackName?.takeIf { it.isNotBlank() }?.let(::add)
		}
		for (name in names) {
			result[name] = fullUrl
		}
	}

	private fun absolutize(url: String): String = when {
		url.startsWith("http://") || url.startsWith("https://") -> url
		url.startsWith("//") -> "https:$url"
		else -> BASE_URL + "/" + url.trimStart('/')
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
