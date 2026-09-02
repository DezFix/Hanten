package hanten.wre.app.core.parser

import hanten.wre.app.core.cache.MemoryContentCache
import hanten.wre.app.core.model.TestMangaSource
import hanten.wre.app.parsers.MangaLoaderContext

@Suppress("unused")
class TestMangaRepository(
	private val loaderContext: MangaLoaderContext,
	cache: MemoryContentCache
) : EmptyMangaRepository(TestMangaSource)
