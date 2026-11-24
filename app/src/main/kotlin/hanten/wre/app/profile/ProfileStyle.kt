package hanten.wre.app.profile

import androidx.annotation.ColorInt

/** Looks a local profile can pick: avatar glyphs and accent colors (shared by onboarding and the profile screen). */
object ProfileStyle {

	/**
	 * Index 0 is the first letter of the nickname. Indexes are stored in settings and backups:
	 * only append new avatars (1 fox, 2 cat and 3 moon replaced the old 狐 / 猫 / 月 glyphs in place).
	 */
	val avatars = listOf("", "🦊", "🐱", "🌙", "🌸", "📚", "🐉", "🍙")

	@ColorInt
	val colors = intArrayOf(
		0xFFA63D41.toInt(), // sakura
		0xFF22305A.toInt(), // navy
		0xFF7A3E73.toInt(), // plum
		0xFF45703A.toInt(), // matcha
		0xFFB8860B.toInt(), // gold
		0xFF1E7A78.toInt(), // teal
	)

	fun avatarText(index: Int, name: String?): String {
		val glyph = avatars.getOrNull(index).orEmpty()
		if (glyph.isNotEmpty()) return glyph
		return name?.trim()?.firstOrNull()?.uppercase() ?: "?"
	}

	@ColorInt
	fun color(index: Int): Int = colors.getOrElse(index) { colors[0] }
}
