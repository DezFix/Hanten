# Futon → Hanten: что забрать и что разбирать отдельно

Дата: 2026-11-02. Upstream: `AppFuton/Futon`, ветка `devel`
(`fb96eb99b`, релиз `v9.9`). Hanten: форк с пакетом `hanten.wre.app`,
своими парсерами и изменениями в `devel`.

> Правило проекта: парсеры и новые источники — **только RU/UK**. Всё, что
> ниже помечено как EN/CN/JP, не берём, если не появится RU/UK-версия.

---

## 1. Уже перенесено из Futon v9.9

- Novel reader (`novel/ui/reader`), EPUB-парсер, PDF-читалка.
- Локальные новеллы: `NovelStorageManager`, импорт файлов и папок.
- `LocalNovelSource`, `NovelReaderActivity`, `PdfReaderActivity`.
- Настройки читалки новелл (шрифт, размер, интервалы, тема, выравнивание,
  режим прокрутки).
- `Ranobes` (RU), `RanobeLib` (RU, `api.cdnlibs.org`, site_id 3),
  `Baka.in.ua` (UA, HTML).
- `AppMode` и кнопка переключения «манга/новеллы».
- Скрытие вкладки страниц и `MangaPageFetcher` для новелл.
- RU/UK-строки для novel UI.

Не переносили: onboarding, watermark, mode-switcher spotlight, остальные
онлайн-источники новелл, smart resume, prefetch/history fixes, импорт тем.

---

## 2. Источники, которые требуют отдельной разборки

### 2.1. Новеллы (кандидаты)

| Источник | Язык | Что известно | Что нужно |
|---|---|---|---|
| `litnet.com` | RU | сайт доступен, большой каталог; каталог/главы на JS | разобрать API/HTML, проверить авторизацию и антибот |
| `ranobehub.org` | RU | Next.js, публичный `/api/search` отдаёт пустой результат | найти реальный API/GraphQL, проверить главы |
| `author.today` | RU | запросы из CI/сервера таймаутят/блокируются | WebView/API, проверить ToS и подписку |
| `booknet.com.ua` / `booknet.ua` | UA | книжная платформа, магазин/DRM | как источник не подходит без легального API |
| `animesvit.com/ranobe` | UA | открытый каталог, но всего 1 тайтл | проверить, будет ли расти; парсер простой |
| `hikka.io/novel` | UA | каталог/метаданные, не факт что есть полный текст | проверить наличие глав и права |
| `rulate.ru` | RU | большой каталог, Cloudflare | проверить API/HTML и антибот |
| `ifreedom.su` | RU | открытый каталог и главы | проверить стабильность селекторов |
| `ranobe.me` | RU | онлайн-читалка, fb2/epub | проверить каталог и главы |
| `ranobe.life` | RU | каталог | проверить наличие текста, а не только метаданных |
| `bllate.org` | RU | каталог/главы | проверить стабильность и права |
| `ранобэ.рф` | RU | каталог/главы | проверить антибот/редиректы |
| `Bookriver.ru` | RU | каталог | слабая структура TOC, проверить |
| `chtyvo.org.ua` | UK | библиотека украинских текстов | проверить наличие новелл и структуру глав |

Уже работают: `Ranobes`, `RanobeLib` (RU), `Baka.in.ua` (UA).

### 2.2. Манга-источники, требующие отдельной разборки

Это не «добавить новый», а «проверить/починить»: старые источники, которые
могут быть нестабильны или требуют WebView/антибота.

- `Com-X` — антибот/PoW, уже несколько раз чинили; держать отдельный тест.
- `JoiMang` — Vercel/чекпоинт, WebView-фолбэк; проверить после изменений.
- `MangaLib` / `SlashLib` / `RanobeLib` — общий lib.social API; менять
  одновременно, следить за `site_id` и `api.cdnlibs.org`.
- `ReadManga` / `MintManga` / `SelfManga` / `SeiManga` / `Usagi` / `AllHentai`
  — Grouple: премиум-главы, смена вёрстки, авторизация.
- `Remanga` / `Recomics` — 429/редиректы, даты, фильтр года.
- `Senkuro` — API/оценки, проверить после релиза.
- `TomiloLib` — пагинация глав и CDN.
- `MangaLib.com` — отдельный сайт, WebView-фолбэк.
- `AComics` — редизайн, селекторы.
- `NudeMoon` — форма тегов (уже поправлен `firstOrNull`), проверить live.
- `HentaiLib` / `Usagi` — помечены `@Broken`, не включать без проверки.

Отдельно: Mihon/Tachiyomi-расширения — чужой код; проверять подпись
репозитория и совместимость `libVersion`, а не «чинить» внутри Hanten.

---

## 3. Что можно забрать из Futon (полезное)

Приоритет: **P1** — сразу, **P2** — после релиза, **P3** — когда будет время.

| Что | Коммит/ветка | Файлы в Hanten | Зачем | Сложность | Конфликты |
|---|---|---|---|---|---|
| Prefetch следующей главы + защита от повторной/пустой загрузки | `311cfbf77` | `reader/domain/ChapterPrefetch.kt` (новый), `ChaptersLoader.kt`, `ReaderViewModel.kt` | После автоподгрузки соседней главы страницы не префетчатся → пауза; `loadPrevNextChapter` может повторно вставить главу | низкая | **сделано** |
| Cloudflare 403/503 по `cf-mitigated`/`cf-ray` | `14809e041` (часть), контекст `506cda65a` | `core/network/CloudFlareInterceptor.kt` | RU-новеллы и часть манги отдают 403/503; сейчас catch может дать `Unit` и не детектить защиту | низкая | **сделано** |
| Favicon для novel-репозиториев | `14809e041` (часть) | `core/parser/favicon/FaviconFetcher.kt`, `BaseNovelRepository.kt` | Novel-источники сейчас попадают в `Unsupported repo` — нет иконок и ошибки Coil | низкая | **сделано** |
| Smart Resume | `a57a10332` | `SmartResumeResolver.kt`, `ChapterCompletionMode.kt`, `AppSettings.kt`, `ChaptersLoader.kt`, `ReaderViewModel.kt`, `pref_reader.xml`, `smart_resume.xml` | Продолжать с новой главы, если почти дочитал предыдущую | средняя | **сделано** |
| Импорт кастомных тем | `fb96eb99b` | `ImportedTheme.kt`, `ImportedThemeStore.kt`, `ColorScheme.kt`, `BaseActivity.kt`, `ThemeChooserPreference.kt`, layouts/strings | Свои цветовые схемы | средняя | **сделано** (только `primary`/`onPrimary`, API 31+) |
| Фильтрация по `AppMode` | `14809e041` (часть) | `ExploreViewModel`, `SearchViewModel`, `SearchSuggestionViewModel`, `FavouritesListViewModel`, `HistoryListViewModel`, `AlternativesUseCase` | Переключатель режима уже есть, но поиск/библиотека смешивают мангу и новеллы | средняя | **сделано** |
| Alternatives: не искать мангу для новеллы | `14809e041` | `AlternativesUseCase.kt` | Одна строка `filter { it.isNovelSource == ref.isNovelSource }` | низкая | **сделано** |
| Guard `pageUrl.startsWith("<")` | `14809e041` | `MangaPageFetcher.kt` | HTML-текст новеллы не должен идти как URL картинки | низкая | низкий; рядом уже есть `isNovelSource` |
| Brotli + `fastFallback(true)` | `14809e041` | `NetworkModule.kt`, `build.gradle`, `libs.versions.toml`, `okhttp3/brotli/Brotli.kt` (удалён) | Меньше трафика, быстрее мёртвые зеркала | низкая | **сделано**: заглушка удалена, `okhttp-brotli` подключён, duplicate class нет |
| Explore perf | `31a7b1d0f` (`Navbar-Redesign-and-Theme-Update`) | `ExploreFragment`, `ExploreViewModel`, `ExploreAdapterDelegates` | `setItemViewCacheSize(20)`, payload-биндинг | низкая | **сделано** |
| Paparazzi snapshot tests | `60d19f661` (`chore/add-snapshot-tests`) | `build.gradle`, `libs.versions.toml`, CI | UI-регрессии | низкая | новый плагин, CI |
| Onyx Gold theme | `2381cf665` | `ColorScheme.kt`, `themes_colored.xml`, `colors_themed.xml` | Ещё одна тема | низкая | не менять дефолт |

**Уже есть в Hanten, переносить не нужно:**

- `af2688f02` — race истории закрыт своим `HistoryDao.setPercent`
  (`7047a3f22`), даже аккуратнее upstream.
- `c2145c709` — merge restore уже есть (`RestoreViewModel`,
  `AppSettings.upsertAll(isMerge)`).
- `d07b0fd7f` — stats by manga/genre уже есть (`getTagDurationStats`,
  `StatsRecord.tagName`, `byGenre`).
- `506cda65a` — Android 6 ICU workaround.
- `610e6cc1e` — фикс `item_extension.xml`.
- `ee2191e54` — okio dependency fix.
- `e28754d64` — bump `kotatsu-parsers-redo`; у Hanten свой `hanten-parsers`.
- `upstream/keiyoushi-improvements` — 0 уникальных коммитов.
- `upstream/release/9.8` — уникальные только `.gitignore`/release commit.
- `upstream/translate` — Weblate, 42 коммита позади `devel`.

**Что не брать:**

- Watermark + японские шрифты (`HomeBackgroundFont`, `FontCache`,
  `GenEiKiwamiGo.ttf` ~2.4 МБ, `ShimbashiGothic` ~2.6 МБ,
  `MojiwTegami`) — ~5 МБ APK ради декора.
- Onboarding (`OnboardingActivity`, `OnboardingPage`, `OnboardingAdapter`,
  `pref_about.xml`, `KEY_ONBOARDING_VERSION`) — у Hanten свой welcome.
- `ModeSwitcherSpotlightOverlay` — лишний onboarding-зависимый слой.
- EN/CN/JP novel-источники (`Lnori`, `LNCrawler`, `FreeWebNovel`,
  `RoyalRoad`, `ScribbleHub`, `Wuxiaworld`) — нарушает RU/UK-политику.
- Compose bottom nav `c762e2c0e` и pill navigation — переписывает
  `MainActivity`/`MainNavigationDelegate`, конфликт с Hanten.
- `14809e041` целиком — затащит onboarding/watermark/spotlight/EN-источники.
- `e28754d64` — другой парсер-репозиторий.

---

## 4. Отчёт агента по изучению Futon

Агент: general/explore, 2026-11-02. Ниже — результат, сжатый до решений.

### Кратко

- В `05f11b2e6..upstream/devel` 9 коммитов, реальных фич — четыре:
  smart resume (`a57a10332`), prefetch (`311cfbf77`), theme import
  (`fb96eb99b`), novel (`14809e041`). Остальное — релизные бампы.
- History-race (`af2688f02`), merge backup (`c2145c709`), stats by genre
  (`d07b0fd7f`), Android 6 ICU (`506cda65a`), okio fix (`ee2191e54`) в
  Hanten уже есть.
- Главный незакрытый баг — prefetch следующей главы после автоперехода
  (`311cfbf77`): `ChaptersLoader.loadPrevNextChapter` не грузит страницы
  новой главы и может повторно вставить главу. **Закрыто.**
- Novel-режим собран, но не хватает фильтрации контента по `AppMode`
  (Explore/поиск/избранное/история/alternatives) и favicon для novel-репо.
  **Закрыто.**
- Theme import — не «полная тема»: upstream применяет только
  `primary`/`onPrimary` через DynamicColors и скрывает пункт на Android <12.
- Smart resume упирается в самый разошедшийся `ReaderViewModel.loadImpl`
  (там же незакоммиченные правки). **Портировано поверх этих правок.**
- Ветки `keiyoushi-improvements`/`release/9.8` ничего нового не дают;
  `translate` — Weblate; `feature/merge-imports` уже в Hanten.

### Риски

- `ReaderViewModel.kt` уже содержит незакоммиченные правки (content-snapshot,
  `chapterIndex < 0`); smart resume и prefetch трогают те же методы —
  вливать вручную, не откатывать рабочее дерево.
- `MangaSourcesRepository.kt` и `MangaRepository.kt` тоже грязные
  (интеграция novel-источников) — фильтры AppMode и favicon делать поверх.
- Theme import: на Android <12 пункт CUSTOM скрыт; применяется только
  primary/onPrimary; нет лимита числа тем; одинаковый `id` перезатирает тему.
- Smart resume: `waitForUpdates=true` задерживает открытие ридера до
  обновления списка; нужен fallback при офлайне/битом списке.
- Prefetch: пустая глава может блокировать переход, пока не сработает
  повторный вызов; проверить на ленивых источниках.
- Cloudflare: проверять `cf-mitigated`/`cf-ray`, иначе можно ложно
  объявить капчу на обычном 403; в Hanten сейчас баг с `Unit` в catch.
- Brotli: локальная заглушка `okhttp3/brotli/Brotli.kt` удалена, подключён
  `okhttp-brotli`; stub `okhttp3/zstd/Zstd.kt` остался, `okhttp-zstd` не
  добавлен. Отдельно стоит проверить установку Mihon-расширения
  MangaFire-типа на устройстве.
- Novel-favicon: upstream ходит в Google за иконкой — лучше свой
  `https://<domain>/favicon.ico` с кэшем.
- Миграций БД и новых разрешений ни для одного пункта не нужно: темы и
  smart resume — SharedPreferences, импорт темы — SAF, favicon — сеть.

### Тесты, которые стоит добавить/портировать

- `ChapterPrefetchTest` (границы главы, короткий преемник, тримминг,
  пустой список) — почти дословно.
- `ChaptersLoaderTest` (повторный вызов, тримминг, пустой/падающий
  преемник) — заменить пакет и `hanten.wre.app.parsers.model`.
- `SmartResumeResolverTest`, `SmartResumeChapterLoadingTest`,
  `SmartResumeSettingsTest` (androidTest).
- `ProgressUpdateRaceTest` — адаптировать под `setPercent`.
- `ImportedThemeTest` — валидная тема, hex/алиасы, отказ при битом hex,
  merge/roundtrip.
- Новые: `CloudFlareInterceptor` (403/503 + `cf-mitigated` + обычный 403),
  `FaviconFetcher` для novel-репо, фильтры AppMode в
  `SearchViewModel`/`ExploreViewModel`, `AlternativesUseCase`.
- Ручной чек-лист: Android 6/7, Android 11 (кастомная тема недоступна),
  Android 14 (DynamicColors), офлайн-ридер с smart resume, переход через
  границу глав при выключенном preload.

---

## 5. Проверка отчёта агента (2026-11-02)

Я не принимал отчёт на веру: проверил ключевые утверждения по коду
Hanten и diff'ам upstream.

| Утверждение агента | Проверка | Решение |
|---|---|---|
| Prefetch после автоперехода не работает | Подтверждено: в Hanten нет `ChapterPrefetch.kt`, `ChaptersLoader` не отсекает повторную/пустую главу, `ReaderViewModel` не префетчит следующую | **Исправлено**: `ChapterPrefetch.kt`, guards в `ChaptersLoader`, prefetch в `ReaderViewModel` |
| Cloudflare catch может вернуть `Unit` | Подтверждено: в Hanten `protectionType` в catch не присваивался | **Исправлено**: `detected` + `cf-mitigated`/`cf-ray` |
| Novel-репозитории не имеют favicon | Подтверждено: `FaviconFetcher` бросал `Unsupported repo` для любого не-manga repo | **Исправлено**: ветка `BaseNovelRepository` + `/favicon.ico` |
| History-race (`af2688f02`) уже закрыт | **Неверно**: Hanten писал `setPercent` без проверки, что строка не изменилась; stale-запись всё ещё возможна | **Исправлено**: `withTransaction` + сравнение `find(manga.id) == history` |
| Merge restore (`c2145c709`) уже есть | Подтверждено: `RestoreViewModel.isMergeEnabled`, `AppSettings.upsertAll(isMerge)` | Не переносить |
| Stats by genre (`d07b0fd7f`) уже есть | Подтверждено: `StatsRepository.byGenre`, `getTagDurationStats`, `StatsRecord.tagName` | Не переносить |
| Android 6 ICU workaround (`506cda65a`) уже есть | Подтверждено: ветка `SDK_INT == 23` в `CloudFlareInterceptor` | Не переносить |
| Alternatives ищет мангу для новеллы | Подтверждено: `getSources()` не фильтровал по `isNovelSource` | **Исправлено** |
| AppMode не фильтрует Explore/поиск/библиотеку | Подтверждено: `ExploreViewModel`, `SearchViewModel`, `FavouritesListViewModel`, `HistoryListViewModel` не смотрят на `appMode` | **Исправлено**: фильтры по `isNovel`/`isNovelSource` в Explore, поиске, подсказках, избранном, истории |
| Theme import применяет только primary/onPrimary | Подтверждено по `ImportedTheme`: хранятся `primaryColor`/`onPrimaryColor`, остальные роли не применяются | **Реализовано** с этим ограничением; тема доступна только на API 31+ |
| Smart resume конфликтует с `ReaderViewModel.loadImpl` | Подтверждено: Hanten уже менял этот метод (content-snapshot, `chapterIndex < 0`) | **Портировано** с сохранением обеих правок |
| Brotli конфликтует с локальной заглушкой | Подтверждено: `okhttp3/brotli/Brotli.kt` — stub с тем же FQN | **Исправлено**: заглушка удалена, `okhttp-brotli` подключён, сборка и dexing проходят |
| `feature/merge-imports` уже в Hanten | Подтверждено по `isMergeEnabled`/`upsertAll` | Не переносить |
| `keiyoushi-improvements`/`release/9.8` не дают нового | Подтверждено по diff/логам | Не переносить |

**Итог:** из отчёта агента перенесены prefetch, Cloudflare-детект, favicon,
alternatives-фильтр, защита истории, AppMode-фильтры, smart resume, Brotli и
импорт тем. Watermark, onboarding, spotlight и EN/CN/JP-источники не нужны.

