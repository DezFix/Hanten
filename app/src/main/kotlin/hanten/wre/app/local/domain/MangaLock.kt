package hanten.wre.app.local.domain

import hanten.wre.app.core.util.MultiMutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guards local manga files. Keyed by manga id: keying by the whole [hanten.wre.app.parsers.model.Manga]
 * would give remote, local and refreshed representations of one title different locks.
 */
@Singleton
class MangaLock @Inject constructor() : MultiMutex<Long>()
