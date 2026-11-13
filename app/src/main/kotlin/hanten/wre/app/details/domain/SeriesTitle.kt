package hanten.wre.app.details.domain

import hanten.wre.app.parsers.model.Manga
import java.util.Locale

/** Title heuristics for automatic continuation detection. */
object SeriesTitle {

	private val partWord = Regex(
		"""(?i)(?<![\p{L}])(часть|том|книга|сезон|part|book|volume|season)\s*([0-9]+|[ivxlcdm]+)(?![\p{L}])""",
	)
	private val trailingNumber = Regex("""(?i)(?:^|\s)([0-9]+|[ivxlcdm]+)$""")
	private val punctuation = Regex("""[^\p{L}\p{N}]+""")
	private val spaces = Regex("""\s+""")
	private const val MAX_PART = 999

	fun baseOf(manga: Manga): String {
		return (listOf(manga.title) + manga.altTitles)
			.asSequence()
			.map(::base)
			.firstOrNull { it.isNotBlank() }
			.orEmpty()
	}

	fun partOf(manga: Manga): Int? {
		return part(manga.title) ?: manga.altTitles.firstNotNullOfOrNull(::part)
	}

	fun base(title: String): String {
		val normalized = normalize(title)
		if (normalized.isBlank()) return ""
		var result = normalized
		partWord.findAll(normalized).toList().asReversed().forEach { match ->
			result = result.removeRange(match.range)
		}
		val trailing = trailingNumber.find(result)
		if (trailing != null) {
			val withoutNumber = normalize(result.removeRange(trailing.range))
			if (withoutNumber.isNotBlank()) {
				result = withoutNumber
			}
		}
		return normalize(result).ifBlank { normalized }
	}

	fun part(title: String): Int? {
		val normalized = normalize(title)
		partWord.find(normalized)?.groupValues?.getOrNull(2)?.let { raw ->
			return parseNumber(raw)
		}
		val trailing = trailingNumber.find(normalized) ?: return null
		val withoutNumber = normalize(normalized.removeRange(trailing.range))
		if (withoutNumber.isBlank()) return null
		return parseNumber(trailing.groupValues.getOrNull(1).orEmpty())
	}

	fun normalize(value: String): String {
		return value.lowercase(Locale.ROOT)
			.replace('ё', 'е')
			.replace('&', ' ')
			.replace(punctuation, " ")
			.replace(spaces, " ")
			.trim()
	}

	private fun parseNumber(raw: String): Int? {
		val value = raw.toIntOrNull() ?: parseRoman(raw) ?: return null
		return value.takeIf { it in 1..MAX_PART }
	}

	private fun parseRoman(raw: String): Int? {
		if (raw.isEmpty()) return null
		var result = 0
		var previous = 0
		for (char in raw.uppercase(Locale.ROOT).reversed()) {
			val value = when (char) {
				'I' -> 1
				'V' -> 5
				'X' -> 10
				'L' -> 50
				'C' -> 100
				else -> return null
			}
			if (value < previous) {
				result -= value
			} else {
				result += value
				previous = value
			}
		}
		return result.takeIf { it > 0 }
	}
}
