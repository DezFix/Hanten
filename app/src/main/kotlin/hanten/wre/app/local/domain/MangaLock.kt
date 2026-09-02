package hanten.wre.app.local.domain

import hanten.wre.app.core.util.MultiMutex
import hanten.wre.app.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLock @Inject constructor() : MultiMutex<Manga>()
