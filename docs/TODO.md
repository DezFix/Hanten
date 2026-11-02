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

`./gradlew :app:compileDebugKotlin`, `:app:lintDebug`, `:app:assembleDebug`
(включая `checkDebugDuplicateClasses`) — успешно. Юнит-тесты не запускались.

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
