# Архитектура Hanten

Документ для тех, кто впервые открыл репозиторий. Цель — за 15–20 минут понять,
где что лежит, как данные ходят между слоями и куда добавлять код, не изучая
85 000 строк Kotlin построчно.

Статус на 2026-11-02: `versionName 2.4.3`, `versionCode 20403`, БД v32.
Сборка: `compileSdk 36`, `minSdk 23`, JDK 17, package `hanten.wre.app`.

---

## 0. TL;DR

Hanten — Android-читалка на Kotlin/ViewBinding + Hilt + Room + OkHttp + Coil.
Два репозитория:

| Репозиторий | Что внутри | Как попадает в APK |
|---|---|---|
| `Hanten/` | Android-приложение: UI, БД, загрузки, трекеры, бэкапы, reader | `:app` |
| `hanten-parsers/` | ~1350 парсеров источников и общий parser API | JitPack-зависимость `com.github.DezFix:hanten-parsers` |

Поток данных в одну строку:

```
экран → ViewModel → domain/use case → repository/DAO или parser → Room/сеть
```

Три «внешних» расширения функциональности:

1. **Парсеры Hanten** — обычные Kotlin-классы, собираются отдельно.
2. **Расширения Mihon/Tachiyomi** — APK, загружаются в процесс через
   `ChildFirstPathClassLoader`.
3. **Синхронизация** — код есть, но `is_sync_enabled=false`, своего сервера нет.

---

## 1. Структура репозитория

```
Hanten/
├── app/
│   ├── build.gradle                 # variants, signing, Sentry, зависимости
│   ├── schemas/                     # Room schema 30/31/32
│   └── src/
│       ├── main/kotlin/hanten/wre/app/
│       │   ├── core/                # БД, сеть, кэш, парсеры, общие UI-утилиты
│       │   ├── main/                # MainActivity, навигация, welcome, app lock
│       │   ├── list/                # общие списки манги, фильтры, сортировки
│       │   ├── favourites/          # избранное и категории
│       │   ├── history/             # история чтения
│       │   ├── details/             # экран манги, главы, страницы
│       │   ├── reader/              # читалка: pager/webtoon/vertical/double
│       │   ├── novel/               # novels: EPUB/PDF reader, local import, Ranobes
│       │   ├── download/            # очередь, WorkManager, локальные файлы
│       │   ├── tracker/             # проверка новых глав, уведомления
│       │   ├── scrobbling/          # Shikimori, AniList, MAL
│       │   ├── backups/             # zip-бэкапы, восстановление, Telegram
│       │   ├── suggestions/         # рекомендации и обновления
│       │   ├── stats/               # статистика чтения
│       │   ├── sync/                # Android Account/SyncAdapter (выключено)
│       │   ├── mihon/               # совместимость с расширениями Mihon
│       │   └── ...
│       ├── debug/                   # debug-only код, LeakCanary, StrictMode
│       ├── nightly/                 # неиспользуемый nightly-вариант
│       └── release/                 # release-заглушки
├── docs/                            # этот документ + review + roadmap
├── metadata/                        # скриншоты и store-метаданные
├── DESIGN.md                        # дизайн-система (частично не реализована)
├── AGENTS.md                        # правила для AI-агентов
├── CI.md                            # сборка/подпись/релизы
└── CONTRIBUTING.md
```

`hanten-parsers/`:

```
src/main/kotlin/hanten/wre/app/parsers/
├── MangaParser.kt                   # базовый API парсера
├── MangaLoaderContext.kt            # мост к приложению: HTTP, cookies, JS, bitmap
├── core/                            # PagedMangaParser, SinglePageMangaParser, Flexible*
├── engine/ (site/<engine>)          # общие движки: Madara, MangaReader и т.д.
├── site/ru|uk|en|...                # конкретные источники по языкам
├── model/                           # Manga, MangaChapter, MangaPage, ContentRating
├── config/                          # ConfigKey и настройки источника
├── network/                         # WebClient, CloudFlareHelper, UserAgents
├── webview/                         # InterceptedRequest
└── util/                            # Jsoup/JSON/корутинные расширения
```

---

## 2. Точки входа и жизненный цикл

### Приложение

- `HantenApp` (`app/src/{debug,release,nightly}/...`) наследует
  `core/BaseApp.kt`.
- `BaseApp.onCreate()`:
  - тема (`AppCompatDelegate.setDefaultNightMode`);
  - Sentry/Bugsink, если пользователь включил отчёты;
  - Conscrypt для TLS 1.3 на Android < 10;
  - `MihonExtensionManager.initialize()`;
  - `WorkScheduleManager.init()` — периодические WorkManager-задачи;
  - фоновые observers БД и локального индекса.
- `MainActivity` — основной экран, нижняя навигация/rail, поиск, welcome-sheet,
  проверка обновления приложения.

### Активности, которые важно знать

| Activity | Назначение |
|---|---|
| `MainActivity` | Главный экран, поиск, нижняя навигация |
| `DetailsActivity` | Карточка манги, главы, страницы, related, scrobbling |
| `ReaderActivity` | Читалка |
| `MangaListActivity` | Каталог/поиск по источнику |
| `MultiSearchActivity` | Поиск по нескольким источникам |
| `SourcesCatalogActivity` / `ExtensionDownloaderActivity` | Источники и расширения |
| `DownloadsActivity` / `DownloadQueueActivity` | Загрузки |
| `HistoryActivity`, `FavouritesActivity`, `AllBookmarksActivity` | Библиотека |
| `UpdatesActivity`, `FeedFragment` | Лента обновлений |
| `StatsActivity` | Статистика |
| `SettingsActivity` | Настройки |
| `ProtectActivity` / `ProtectSetupActivity` | Пароль/биометрия |
| `CloudFlareActivity` | Ручное прохождение Cloudflare |
| `SyncAuthActivity` | Вход в sync (сейчас выключено) |

Сервисы: `BackupService`, `RestoreService`, `PeriodicalBackupService`,
`ImportService`, `LocalIndexUpdateService`, `LocalChaptersRemoveService`,
`AutoFixService`, `MangaPrefetchService`, `AdListUpdateService`.
Воркеры: `TrackWorker`, `DownloadWorker`, `DownloadSchedulerWorker`,
`SuggestionsWorker`, `LocalStorageCleanupWorker`.

---

## 3. Слои и правила

В `app` принят feature-based подход:

```
<feature>/
├── data/       # Room entity/DAO, DTO, repository
├── domain/     # use cases, модели, бизнес-логика
└── ui/         # Activity/Fragment/ViewModel/adapter
```

Общие вещи — в `core/`:

| Пакет | Что там |
|---|---|
| `core/db` | `MangaDatabase`, DAO, entity, миграции |
| `core/network` | OkHttp-клиенты, interceptors, cookies, DoH, прокси, image proxy |
| `core/parser` | `MangaRepository`, `CachingMangaRepository`, `MirrorSwitcher` |
| `core/prefs` | `AppSettings`, `SourceSettings`, все enum-настройки |
| `core/ui` | базовые Activity/Fragment/ViewModel, адаптеры, bottom sheets |
| `core/cache` | memory cache и `SafeDeferred` |
| `core/github` | проверка и скачивание обновлений |
| `core/os` | shortcuts, NetworkState, AppValidator |

Правила, которые экономят время:

- **Ошибки**: `runCatchingCancellable` вместо `runCatching`, чтобы не съесть
  `CancellationException`. В release `printStackTraceDebug()` — no-op.
- **Потоки**: сеть/диск — `Dispatchers.IO`, CPU — `Dispatchers.Default`;
  в БД — только через `withTransaction`, без сетевых вызовов внутри транзакции.
- **Hilt**: constructor injection; `@AndroidEntryPoint` для Activity/Fragment/
  Service/Receiver; `@HiltViewModel`; `@HiltWorker`.
- **ViewModel**: `BaseViewModel.launchJob` / `launchLoadingJob`, ошибки через
  `MutableEventFlow<Throwable>` и `withErrorHandling()`.
- **Flow**: `StateFlow` для состояния, `MutableEventFlow` для one-shot событий.
- **Настройки**: новое поле — в `AppSettings`, ключ — `const val`, UI — в
  `res/xml/pref_*.xml`, миграция не нужна.

---

## 4. База данных

`core/db/MangaDatabase.kt`, Room v32, файл `hanten-db`.
При первом запуске после Futon копируется `futon-db` (включая `-wal`/`-shm`).

Основные таблицы:

| Таблица | Сущность | Комментарий |
|---|---|---|
| `manga` | `MangaEntity` | метаданные тайтла, source, url |
| `chapters` | `ChapterEntity` | кэш глав; PK `(manga_id, chapter_id)` |
| `history` | `HistoryEntity` | прогресс, мягкое удаление через `deleted_at` |
| `favourites` | `FavouriteEntity` | many-to-many manga↔category |
| `favourite_categories` | `FavouriteCategoryEntity` | категории, `track`, `show_in_lib` |
| `bookmarks` | `BookmarkEntity` | закладки на страницу |
| `tracks` / `track_logs` | `TrackEntity` / `TrackLogEntity` | трекер и лог обновлений |
| `scrobblings` | `ScrobblingEntity` | привязки к Shikimori/AniList/MAL |
| `stats` | `StatsEntity` | время и страницы по сессиям |
| `suggestions` | `SuggestionEntity` | рекомендации |
| `manga_sources` | `MangaSourceEntity` | вкл/выкл, pinned, домен, порядок |
| `local_index` | `LocalMangaIndexEntity` | локальные главы |
| `download_queue` / `smart_downloads` | очередь и smart-download |
| `external_extension_repos` | репозитории расширений Mihon |

Особенности:

- Удаление истории/избранного — **мягкое** (`deleted_at`), очистка в `gc()`.
- `HistoryRepository.addOrUpdate` пересчитывает прогресс и вызывает трекер и
  scrobblers; сеть вынесена за транзакцию.
- Индексы добавлены миграцией 30→31 (история, треки, логи).
- Схемы для экспорта лежат в `app/schemas/30.json`, `31.json`, `32.json`.
- **Не добавляйте downgrade-миграции** — Room не умеет откатываться вниз
  (см. `Migration24To23` в разборе).

---

## 5. Источники и парсеры

### Hanten-парсеры

**Scope проекта — только русские и украинские источники.** Остальные языки
в `site/<lang>` — наследие upstream, их не изучаем и не трогаем без отдельной
задачи. Это осознанное ограничение: поддерживать 1300+ парсеров на всех
языках невозможно.

- Приложение не содержит парсеры: зависимость подключается в
  `app/build.gradle` (`libs.versions.toml` → `parsers = "v2026.10.19"`).
- `MangaLoaderContextImpl` реализует мост `MangaLoaderContext`:
  HTTP-клиент, cookies, `evaluateJs` через WebView, base64, bitmap.
- `MangaRepository.Factory` (`core/parser/MangaRepository.kt`):
  - `MangaParserSource` → `ParserMangaRepository`;
  - `LocalMangaSource` → `LocalMangaRepository`;
  - `ExternalMangaSource` → `ExternalMangaRepository` (SAF/плагин);
  - `MihonMangaSource` → `MihonMangaRepository`;
  - `UnknownMangaSource` → `EmptyMangaRepository`.
- `ParserMangaRepository` оборачивает парсер и `MirrorSwitcher`:
  при ошибке/пустом результате пробует зеркала из `configKeyDomain`.
- `CachingMangaRepository` кэширует details/pages/related в
  `MemoryContentCache` (LRU + TTL, реакция на trim memory).
- Парсеры не должны хардкодить домен: только `configKeyDomain` + `domain`.

### Mihon/Tachiyomi-расширения

- `mihon/` — загрузчик APK-расширений:
  - `MihonExtensionLoader` сканирует установленные пакеты, читает manifest
    metadata (`tachiyomi.extension.class/factory`);
  - `ChildFirstPathClassLoader` грузит классы из APK;
  - `MihonInjektBridge` регистрирует `Application`, `OkHttpClient`, `CookieJar`,
    `SharedPreferences`, `Json` в Injekt;
  - `eu.kanade.tachiyomi.*` — локальная shim-копия API для совместимости.
- Ограничения версии библиотеки: `LIB_VERSION_MIN = 1.2`,
  `LIB_VERSION_MAX = 2.5` (максимум сейчас **не проверяется**).
- Проверка подписи расширения задумана
  (`InstalledExtensionSignatureValidator`), но **не подключена**.

### Локальные источники

- `local/` читает папки/архивы через `LocalMangaParser` и SAF.
- Поддерживаются CBZ/архивы и папки с изображениями; глава = архив/подпапка.
- `LocalMangaOutput` пишет CBZ или папку, `LocalMangaRepository` ведёт индекс.

---

## 6. Сеть

Два OkHttp-клиента (`core/network/NetworkModule.kt`):

| Клиент | Где используется | Особенности |
|---|---|---|
| `@BaseHttpClient` | обновления, Telegram, трекеры, служебные запросы | cookies, DoH, прокси, Cloudflare, rate limit |
| `@MangaHttpClient` | парсеры, картинки, загрузки | base + `CommonHeadersInterceptor` + `CacheLimitInterceptor` |

Порядок interceptors (важно при отладке):

```
CloudFlareInterceptor → RateLimitInterceptor → CurlLoggingInterceptor (debug)
→ [MangaHttpClient] CacheLimitInterceptor (network) → CommonHeadersInterceptor
```

- `RateLimitInterceptor`: на 429 блокирует host на `Retry-After`
  (до 15 мин), короткие паузы ждёт, длинные — сразу бросает
  `TooManyRequestExceptions`.
- `CloudFlareInterceptor`: распознаёт challenge/blocked и бросает
  `CloudFlareProtectedException` / `CloudFlareBlockedException`.
- `DoHManager`: DNS-over-HTTPS, настраивается в `AppSettings`.
- `ProxyProvider`: HTTP/SOCKS, для WebView прокси не поддерживается.
- `AndroidCookieJar` — cookies WebView; fallback `PreferencesCookieJar`.
- Coil: `AppModule.provideCoil` — disk cache, AVIF/GIF/SVG, CBZ fetcher,
  `MangaSourceHeaderInterceptor`, `CoverRestoreInterceptor`, image proxy.

---

## 7. Ключевые потоки

### 7.1 Экран манги

```
DetailsActivity
  → DetailsViewModel.doLoad()
    → DetailsLoadUseCase (network/cache; details + local + override)
      → MangaDataRepository.updateChapters()
      → ChaptersLoader.init()
  → ChaptersPagesViewModel (главы/страницы/закладки)
  → HistoryUpdateUseCase / ProgressUpdateUseCase
  → TrackWorker / Scrobbler
```

### 7.2 Читалка

```
ReaderActivity
  → ReaderViewModel
    → getStateFromIntent() (savedState/history/branch)
    → ChaptersLoader.loadSingleChapter()
    → PageLoader.loadPageAsync() (Semaphore 3, disk/memory cache, prefetch)
    → Coil + SSIV/Pager
  → onCurrentPageChanged() → saveCurrentState() → HistoryUpdateUseCase
  → StatsCollector.onStateChanged()
```

Режимы: `STANDARD`, `REVERSED`, `VERTICAL`, `WEBTOON`; double-page
включается на landscape/foldable (`ReaderManager`).

### 7.3 Загрузки

```
DownloadDialog / SmartDownloadUseCase
  → DownloadQueueRepository.addToQueue()
  → DownloadSchedulerWorker (constraints, off-peak, charging, Wi-Fi)
    → DownloadWorker (DownloadTask)
      → LocalMangaOutput (CBZ/папка)
      → DownloadSchedulerWorker.enqueue() снова
```

Особенности: `PausingReceiver` (broadcast pause/resume/skip), `MangaLock`
на mangaId, `BandwidthLimiter`, `EnforceStorageQuotaUseCase`,
`DeleteReadChaptersUseCase`.

### 7.4 Трекер

```
WorkScheduleManager → TrackWorker (periodic/one-shot)
  → GetTracksUseCase → TrackingRepository.updateTracks()
  → CheckNewChaptersUseCase
    → parser.getDetails() / CachingMangaRepository
    → TrackingRepository.saveUpdates()
    → TrackerNotificationHelper
    → при стратегии DOWNLOADED — DownloadWorker
```

### 7.5 Скробблинг

`ScrobblingModule` даёт набор `Scrobbler` (Shikimori, AniList, MAL).
`ScrobblerStorage` хранит токены в отдельных SharedPreferences на сервис.
Импорт/экспорт библиотеки есть только у Shikimori
(`ShikimoriImportUseCase` / `ShikimoriExportUseCase`).

### 7.6 Бэкапы

- `BackupRepository` пишет zip из секций:
  `index`, `history`, `categories`, `favourites`, `settings`,
  `reader_grid`, `bookmarks`, `sources`, `scrobbling`, `statistics`,
  `saved_filters`.
- `BackupService` / `RestoreService` — foreground services.
- `PeriodicalBackupService` — периодический бэкап в SAF-папку.
- `TelegramBackupUploader` — отправка через **собственного** бота пользователя.
- `AppBackupAgent` — Android full backup (сейчас содержит ошибку внедрения,
  см. review).

### 7.7 Обновление приложения

`AppUpdateRepository` → GitHub Releases API (`DezFix/Hanten`).
`AppUpdateFlow`:
- сравнивает `VersionId` с `BuildConfig.VERSION_NAME`;
- показывает changelog;
- качает APK в `cache/updates`;
- проверяет подпись `AppValidator.isTrustedApk` (SHA-256 сертификата);
- ставит через `FileProvider`.
Nightly-сборки проверку обновлений пропускают.

### 7.8 Новеллы

```
LocalNovelListFragment / Ranobes source
  → ReaderIntent.Builder.manga()
    → NovelReaderActivity (EPUB/HTML) или PdfReaderActivity (PDF)
      → NovelReaderViewModel / PdfReaderViewModel
      → NovelStorageManager (локальные файлы и папки)
```

- `novel/data/source` — Ranobes (RU), RanobeLib (RU) и Baka.in.ua (UA);
  остальные языки намеренно не добавлены.
- `novel/data/epub` — парсер EPUB, `novel/data/pdf` — `PdfRenderer`.
- `LocalNovelSource` — локальные новеллы; `AppMode` переключает локальную
  вкладку между мангой и новеллами.
- Страницы новелл не грузятся как картинки: `MangaPageFetcher` возвращает
  `null`, а `ChaptersPagesSheet` скрывает вкладку страниц.

---

## 8. Hilt, потоки, ошибки

Модули: `core/AppModule.kt`, `core/network/NetworkModule.kt`,
`mihon/MihonModule.kt`, `scrobbling/ScrobblingModule.kt`.
Entry points для мест без Hilt: `AppRouterEntryPoint`,
`BaseActivityEntryPoint`, `PrefetchCompanionEntryPoint`,
`SyncAdapterEntryPoint`, `SyncProvider`.

Паттерн ViewModel:

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository,
) : BaseViewModel() {
    val onError = MutableEventFlow<Throwable>()
    val data = repository.observe()
        .withErrorHandling()
        .stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, emptyList())
}
```

Паттерн Worker:

```kotlin
@HiltWorker
class MyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MyRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        repository.doWork()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        e.printStackTraceDebug()
        Result.failure()
    }
}
```

---

## 9. Сборка, тесты, CI

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # нужен KEYSTORE_FILE/пароли
./gradlew test                   # JVM unit tests
./gradlew check                  # lint + tests (сейчас падает на lint-ошибках)
./gradlew connectedDebugAndroidTest
```

- JDK 17, Android SDK 36 / build-tools 36.0.0.
- Release-подпись: env `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
  `KEY_PASSWORD`; fingerprint в `CI.md` и `AppValidator`.
CI есть у **двух** проектов, но это разные репозитории и разные workflow:

| Проект | Workflows | Что делают |
|---|---|---|
| `Hanten` (app) | `.github/workflows/debug.yml`, `release.yml`, `issue_moderator.yml` | PR/push → unit tests + lint + debug APK; tag `v*` → tests + lint + signed release + GitHub Release; модерация issue. Java 17, SDK 36. |
| `hanten-parsers` | `.github/workflows/ci.yml` | компиляция парсеров на push/PR; чистые JVM-тесты `util`/`model.search`; ручной прогон RU/UA live-sweep. Java 17. |

Важно: parser CI намеренно ограничен RU/UK. Остальные языки в
`hanten-parsers` — наследие upstream, они не входят в поддерживаемый scope
и не проверяются отдельно.

- App CI запускает `testDebugUnitTest`, `lintDebug` и сборку APK.
- Parser CI не гоняет live-тесты на каждом PR: они ручные
  (`workflow_dispatch`), потому что часть RU-хостов geo-block'ает CI.

---

## 10. Куда добавлять код

| Задача | Куда смотреть |
|---|---|
| Новая настройка | `core/prefs/AppSettings.kt`, `res/xml/pref_*.xml`, `res/values/strings.xml` |
| Новый экран | `<feature>/ui`, `AndroidManifest.xml`, `AppRouter` |
| Новый запрос к БД | `core/db/entity`, `core/db/dao`, миграция + `DATABASE_VERSION` |
| Новая фича | `<feature>/{data,domain,ui}` |
| Новый источник | `hanten-parsers/site/<lang>/`, затем bump `parsers` в `libs.versions.toml` |
| Новый движок парсеров | `hanten-parsers/core/` + `site/<engine>/` |
| Новый трекер | `scrobbling/<service>/`, `ScrobblerService`, `ScrobblingModule` |
| Новый бэкап-раздел | `BackupSection`, `BackupRepository`, `backups/data/model` |
| Новый WorkManager-work | `<feature>/work`, `WorkScheduleManager` |
| Новый foreground service | `AndroidManifest.xml`, `CoroutineIntentService` |

---

## 11. Инварианты и грабли

1. **Не теряйте `CancellationException`** — только `runCatchingCancellable`.
2. **Не держите транзакцию БД во время сети** — трекеры, парсеры, Telegram
   должны быть снаружи `withTransaction`.
3. **ID источника стабильны между зеркалами** — `generateUid`/relative URL,
   иначе история и избранное «потеряются» при смене домена.
4. **Миграции только вверх**; downgrade-миграции не работают и опасны.
5. **Секреты не в бэкап и не в git**: пароли, Telegram-токены, OAuth-токены.
6. **Release-логи**: `Log.d/v` с URL/токенами — только под `BuildConfig.DEBUG`.
7. **Chapters order**: парсеры и UI рассчитывают на стабильный порядок глав;
   при сомнении используйте `getPreferredBranch` и номер главы.
8. **Mihon-расширения — это код в процессе**: не грузите непроверенные APK
   без проверки подписи.
9. **Reader/Download cache**: не удаляйте чужие `*.tmp` в общем каталоге
   загрузок; для очистки есть `LocalMangaRepository.cleanup()`.
10. **`local.properties`** не коммитится; ключи и токены — только env/секреты.

---

## 12. Что читать дальше

- `AGENTS.md` — стиль кода, команды, паттерны.
- `CONTRIBUTING.md` — процесс PR и правила.
- `CI.md` — подпись, релиз, keystore.
- `DESIGN.md` — дизайн-система (не всё реализовано).
- `hanten-parsers/CONTRIBUTING.md` — как писать парсеры.
- `docs/CODE_REVIEW.md` — найденные баги и мёртвый код.
- `docs/ROADMAP.md` — чего не хватает относительно конкурентов.
