package hanten.wre.app.core.parser.favicon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import hanten.wre.app.core.parser.ParserMangaRepository
import hanten.wre.app.parsers.model.Favicons
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the list of favicons of a source once and shares it between all icon requests.
 *
 * The list is parsed from the site's main page (a full HTML download + jsoup DOM), and it used to run
 * inside every Coil request. A pending request then kept the view (and its fragment) alive through
 * OkHttp's call queue even after the screen was closed — heap dumps showed closed Catalog/Library
 * screens retained this way. Here the work runs in its own scope that knows nothing about views,
 * callers only await it, and it is cancelled once nobody is waiting anymore.
 */
@Singleton
class FaviconsLoader @Inject constructor() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val lock = Any()
	private val pending = HashMap<String, Entry>()
	private val loaded = object : LinkedHashMap<String, Favicons>(32, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Favicons>?) = size > MAX_CACHED
	}

	suspend fun load(repository: ParserMangaRepository): Favicons {
		val source = repository.source.name
		val entry = synchronized(lock) {
			loaded[source]?.let { return it }
			pending.getOrPut(source) {
				Entry(scope.async { repository.getFavicons() })
			}.also { it.waiters++ }
		}
		try {
			val result = entry.deferred.await()
			synchronized(lock) {
				loaded[source] = result
			}
			return result
		} finally {
			synchronized(lock) {
				entry.waiters--
				if (entry.waiters == 0) {
					if (pending[source] === entry) {
						pending.remove(source)
					}
					if (!entry.deferred.isCompleted) {
						entry.deferred.cancel(CancellationException("No more favicon requests for $source"))
					}
				}
			}
		}
	}

	private class Entry(val deferred: Deferred<Favicons>) {
		var waiters = 0
	}

	private companion object {

		const val MAX_CACHED = 64
	}
}
