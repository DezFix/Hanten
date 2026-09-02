package hanten.wre.app.mihon.parsers.model

import hanten.wre.app.mihon.parsers.InternalParsersApi

@InternalParsersApi
public class WordSet(private vararg val words: String) {

	public fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
	public fun startsWith(dateString: String): Boolean = words.any { dateString.startsWith(it, ignoreCase = true) }
	public fun endsWith(dateString: String): Boolean = words.any { dateString.endsWith(it, ignoreCase = true) }
}

