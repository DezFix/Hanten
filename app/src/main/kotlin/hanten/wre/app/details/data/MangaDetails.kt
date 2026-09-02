package hanten.wre.app.details.data

import hanten.wre.app.core.model.getLocale
import hanten.wre.app.core.model.isLocal
import hanten.wre.app.core.model.withOverride
import hanten.wre.app.core.ui.model.MangaOverride
import hanten.wre.app.local.domain.model.LocalManga
import hanten.wre.app.parsers.model.Manga
import hanten.wre.app.parsers.model.MangaChapter
import hanten.wre.app.parsers.model.MangaState
import hanten.wre.app.parsers.util.ifNullOrEmpty
import hanten.wre.app.parsers.util.nullIfEmpty
import hanten.wre.app.reader.data.filterChapters
import java.util.Locale

data class MangaDetails(
    private val manga: Manga,
    private val localManga: LocalManga?,
    private val override: MangaOverride?,
    val description: CharSequence?,
    val isLoaded: Boolean,
) {

    constructor(manga: Manga) : this(
        manga = manga,
        localManga = null,
        override = null,
        description = null,
        isLoaded = false,
    )

    val id: Long
        get() = manga.id

    val allChapters: List<MangaChapter> by lazy { mergeChapters() }

    val chapters: Map<String?, List<MangaChapter>> by lazy {
        allChapters.groupBy { it.branch }
    }

    val isLocal
        get() = manga.isLocal

    val local: LocalManga?
        get() = localManga ?: if (manga.isLocal) LocalManga(manga) else null

    val coverUrl: String?
        get() = override?.coverUrl
            .ifNullOrEmpty { manga.largeCoverUrl }
            .ifNullOrEmpty { manga.coverUrl }
            .ifNullOrEmpty { localManga?.manga?.coverUrl }
            ?.nullIfEmpty()

    val isRestricted: Boolean
        get() = manga.state == MangaState.RESTRICTED

    private val mergedManga by lazy {
        if (localManga == null) {
            // fast path
            manga.withOverride(override)
        } else {
            manga.copy(
                title = override?.title.ifNullOrEmpty { manga.title },
                coverUrl = override?.coverUrl.ifNullOrEmpty { manga.coverUrl },
                largeCoverUrl = override?.coverUrl.ifNullOrEmpty { manga.largeCoverUrl },
                contentRating = override?.contentRating ?: manga.contentRating,
                chapters = allChapters,
            )
        }
    }

    fun toManga() = mergedManga

    fun getLocale(): Locale? {
        findAppropriateLocale(chapters.keys.singleOrNull())?.let {
            return it
        }
        return manga.source.getLocale()
    }

    fun filterChapters(branch: String?) = copy(
        manga = manga.filterChapters(branch),
        localManga = localManga?.run {
            copy(manga = manga.filterChapters(branch))
        },
    )

    private fun mergeChapters(): List<MangaChapter> {
        val chapters = manga.chapters
        val localChapters = local?.manga?.chapters.orEmpty()
        if (chapters.isNullOrEmpty()) {
            return localChapters
        }
        val localMap = if (localChapters.isNotEmpty()) {
            localChapters.associateByTo(LinkedHashMap(localChapters.size)) { it.id }
        } else {
            null
        }
        val result = ArrayList<MangaChapter>(chapters.size)
        for (chapter in chapters) {
            val local = localMap?.remove(chapter.id)
            result += local ?: chapter
        }
        if (!localMap.isNullOrEmpty()) {
            result.addAll(localMap.values)
        }
        return result
    }

    private fun findAppropriateLocale(name: String?): Locale? {
        if (name.isNullOrEmpty()) {
            return null
        }
        return Locale.getAvailableLocales().find { lc ->
            name.contains(lc.getDisplayName(lc), ignoreCase = true) ||
                name.contains(lc.getDisplayName(Locale.ENGLISH), ignoreCase = true) ||
                name.contains(lc.getDisplayLanguage(lc), ignoreCase = true) ||
                name.contains(lc.getDisplayLanguage(Locale.ENGLISH), ignoreCase = true)
        }
    }
}
