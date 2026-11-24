package hanten.wre.app.settings.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.XmlRes
import androidx.core.content.edit
import org.xmlpull.v1.XmlPullParser

/**
 * Hanten 3.0: settings screens crashed (ClassCastException) when a value had another type than the
 * row expects, e.g. a number restored from an old backup as Int where a Float/String is read.
 * Before a screen is built, every persisted key of its XML is checked and converted (or dropped
 * to the default when it can't be converted).
 */
object PreferenceTypeGuard {

	private enum class Kind { BOOLEAN, STRING, INT, STRING_SET }

	fun sanitize(context: Context, prefs: SharedPreferences?, @XmlRes xmlRes: Int) {
		prefs ?: return
		val expected = runCatching { readKinds(context, xmlRes) }.getOrNull() ?: return
		val all = prefs.all
		prefs.edit {
			for ((key, kind) in expected) {
				val value = all[key] ?: continue
				when (kind) {
					Kind.BOOLEAN -> if (value !is Boolean) {
						when (value.toString().lowercase()) {
							"true", "1" -> putBoolean(key, true)
							"false", "0" -> putBoolean(key, false)
							else -> remove(key)
						}
					}

					Kind.STRING -> if (value !is String) {
						if (value is Set<*>) remove(key) else putString(key, value.toString().removeSuffix(".0"))
					}

					Kind.INT -> if (value !is Int) {
						val number = when (value) {
							is Number -> value.toInt()
							is String -> value.toFloatOrNull()?.toInt()
							else -> null
						}
						if (number != null) putInt(key, number) else remove(key)
					}

					Kind.STRING_SET -> if (value !is Set<*>) remove(key)
				}
			}
		}
	}

	private fun readKinds(context: Context, xmlRes: Int): Map<String, Kind> {
		val result = HashMap<String, Kind>()
		val parser = context.resources.getXml(xmlRes)
		parser.use {
			while (it.next() != XmlPullParser.END_DOCUMENT) {
				if (it.eventType != XmlPullParser.START_TAG) continue
				val key = it.getAttributeValue(ANDROID_NS, "key") ?: continue
				if (it.getAttributeValue(ANDROID_NS, "persistent") == "false") continue
				val tag = it.name.substringAfterLast('.')
				val kind = when {
					tag.contains("Switch") || tag.contains("CheckBox") || tag.contains("TwoState") -> Kind.BOOLEAN
					tag.contains("MultiSelect") -> Kind.STRING_SET
					tag == "SeekBarPreference" || tag == "SliderPreference" -> Kind.INT
					tag.contains("List") || tag.contains("EditText") || tag.contains("AutoComplete") -> Kind.STRING
					else -> null
				} ?: continue
				result[key] = kind
			}
		}
		return result
	}

	private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
}
