package hanten.wre.app.scrobbling.common.data

import hanten.wre.app.scrobbling.common.domain.model.ScrobblerManga
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerMangaInfo
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerRelated
import hanten.wre.app.scrobbling.common.domain.model.ScrobblerUser

interface ScrobblerRepository {

	val oauthUrl: String

	val isAuthorized: Boolean

	val cachedUser: ScrobblerUser?

	suspend fun authorize(code: String?)

	suspend fun loadUser(): ScrobblerUser

	fun logout()

	suspend fun unregister(mangaId: Long)

	suspend fun findManga(query: String, offset: Int): List<ScrobblerManga>

	suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo

	/** Related works of the tracker entry [id]; empty when the tracker has no such data. */
	suspend fun getRelated(id: Long): List<ScrobblerRelated> = emptyList()

	suspend fun createRate(mangaId: Long, scrobblerMangaId: Long)

	suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int)

	suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?)
}
