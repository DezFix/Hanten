package hanten.wre.app.core.model

/**
 * Translation teams (chapter branches) the user prefers, in priority order.
 * Kept process-wide because [getPreferredBranch] is called from many places that
 * have no settings at hand; [hanten.wre.app.core.BaseApp] keeps it in sync with prefs.
 */
object PreferredBranches {

	@Volatile
	var keywords: List<String> = emptyList()
		private set

	fun update(raw: String?) {
		keywords = parse(raw)
	}

	fun parse(raw: String?): List<String> = raw
		?.split(',', ';', '\n')
		?.map { it.trim() }
		?.filter { it.isNotEmpty() }
		.orEmpty()

	/** The first branch (in user priority order) that mentions one of the keywords. */
	fun <T> pick(branches: Map<String?, T>): String? {
		for (keyword in keywords) {
			val match = branches.keys.firstOrNull { it != null && it.contains(keyword, ignoreCase = true) }
			if (match != null) {
				return match
			}
		}
		return null
	}
}
