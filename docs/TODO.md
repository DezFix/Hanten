# TODO: текущий заход

Дата: 2026-11-02. Цель: закрыть P1/P2 из `FUTON_PORT.md`, проверить,
закоммитить, запушить и собрать debug APK.

| # | Задача | Агент | Файлы | Статус |
|---|---|---|---|---|
| 1 | AppMode-фильтры + Explore perf | agent A | `explore/ui`, `search/ui/multi`, `search/ui/suggestion`, `favourites/ui/list`, `history/ui` | готово |
| 2 | Smart Resume | agent B | `reader/domain`, `reader/ui/ReaderViewModel`, `core/prefs`, `settings/reader`, `pref_reader.xml` | готово |
| 3 | Brotli + fastFallback | agent C | `core/network/NetworkModule.kt`, `app/build.gradle`, `gradle/libs.versions.toml`, удалена заглушка `okhttp3/brotli` | готово |
| 4 | Импорт кастомных тем | agent D | `core/prefs`, `core/ui/BaseActivity`, `settings/Appearance*`, `ThemeChooserPreference`, layouts | готово |
| 5 | Проверка, сборка, commit, push, debug APK | main | — | готово |

## Что сделано по задачам

- **AppMode.** Explore показывает только источники текущего режима; локальная
  кнопка открывает `LocalNovelSource` в novel-режиме; поиск, подсказки,
  избранное и история фильтруются по `isNovel`. Explore: `setItemViewCacheSize(20)`,
  payload-биндинг без перезагрузки иконок, `distinctUntilChanged`.
  Ограничение (как в upstream): фильтрация избранного/истории идёт после
  выборки страницы по `limit`, поэтому при плотном «чужом» режиме возможна
  короткая или пустая первая страница.
- **Smart Resume.** `SmartResumeResolver` + `ChapterCompletionMode`, настройки
  в `pref_reader.xml`, порог в процентах или страницах, ожидание обновлений
  глав, fallback на сохранённую позицию при ошибке/офлайне. Портированы
  защита `content.value != contentSnapshot`, `chapterIndex < 0` в
  `computePercent`, `loadSingleChapter(keepCurrentOnEmpty)`.
- **Brotli.** Заглушка `okhttp3/brotli/Brotli.kt` удалена, добавлена
  зависимость `okhttp-brotli`, в base client — `BrotliInterceptor` сразу
  после `cache(cache)`. `fastFallback(true)` добавлен, хотя в OkHttp 5 он
  уже включён по умолчанию. Stub `okhttp3/zstd/Zstd.kt` оставлен: артефакт
  `okhttp-zstd` не подключён.
- **Темы.** `ImportedTheme`/`ImportedThemeStore` (SharedPreferences, JSON),
  `ColorScheme.CUSTOM` (скрыт из общего списка, доступен только с API 31+),
  импорт JSON через SAF в Appearance, карточки импортированных тем в
  `ThemeChooserPreference`, применение через `DynamicColors` с
  content-based source (`primary`). Роли кроме `primary`/`onPrimary` не
  применяются — ограничение upstream-формата.

## Проверка

`./gradlew :app:compileDebugKotlin`, `:app:lintDebug`, `:app:testDebugUnitTest`,
`:app:assembleDebug`, `:app:assembleRelease` — успешно. Debug и release APK
проверены на эмуляторе (Android 15): каталоги всех трёх новелл-источников,
обложки, карточка произведения, чтение главы, переключение режима.

## Исправления после первого теста (2.5.0)

- **RanobeLib 403 на обложках.** CDN отдаёт картинки только с `Referer`
  сайта-источника. `MangaSourceHeaderInterceptor` теперь передаёт любой
  `MangaSource` (не только `MangaParserSource`), а `CommonHeadersInterceptor`
  подставляет `Referer: https://<domain>/` для `NovelParserSource`.
- **Ranobes «ошибка сети».** Сайт переехал на `ranobes.com` и сменил разметку.
  `RanobesRepository` переписан под текущий сайт: каталог `/ranobe/`,
  топ `/popular.html`, главы `/chapters/{slug}/page/N/`, текст `#arrticle`;
  старые ссылки `ranobes.net`/`ranobes.top` перенаправляются на `.com`.
  Поиск по названию на сайте недоступен — работает локальная фильтрация.
- **Baka.in.ua: одинаковые обложки.** Парсер брал `h2 a` (5 промо-блоков) и
  ближайший `img` родителя. Теперь разбираются карточки `article` каталога,
  у каждой своя обложка и название.
- **Путаница «манга/новеллы».** В режиме новелл заголовок списка и подсказка
  поиска теперь «Источники новелл» / «Поиск новелл» (ru/uk/en), а не «манга».

## Группировка источников в парсере (2.5.0)

Онлайн-новеллы теперь есть и в репозитории парсеров
(`DezFix/hanten-parsers`, коммит `8c73a6e6`): отдельная группа
`site/novel/ru` и `site/novel/uk` с `ContentType.NOVEL`
(RANOBES, RANOBELIB, BAKA_UA). KSP-сводка считает мангу и новеллы
раздельно (`total` / `novels`).

Приложение пока использует свои `NovelParserSource` и репозитории в
`novel/data/source`. Перед обновлением `parsers` в
`gradle/libs.versions.toml` нужно:

1. выпустить новую версию парсеров (тег `v*`), дождаться сборки JitPack;
2. удалить `NovelParserSource` и `novel/data/source/*`, перевести все
   проверки на `MangaParserSource.contentType == ContentType.NOVEL`;
3. не забыть `MangaSourcesRepository` (авто-включение новелл, каталог) и
   `FaviconFetcher` (ветка `BaseNovelRepository` станет не нужна).

Без шага 2 обновление парсеров даст два источника с одинаковыми именами.

## Не входит в этот заход

- Новые онлайн-источники новелл из раздела «на отдельную разборку»
  (Litnet, RanobeHub, Author.Today и т.п.).
- Watermark/иероглифы, onboarding, spotlight.
- EN/CN/JP-источники.
- Snapshot-тесты, Onyx theme, Compose-navbar, Paparazzi.
- Полный `okhttp-zstd` и проверка Mihon-расширений на сжатие.

## Правила для агентов

- только назначенные файлы, не откатывать существующие правки;
- не делать commit/push/release, не запускать Gradle-тесты;
- сохранять пакет `hanten.wre.app`;
- новые строки — в отдельные XML (`smart_resume.xml`,
  `imported_theme.xml`), не в общий `strings.xml`, чтобы не конфликтовать.
