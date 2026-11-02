# Code review Hanten 2.4.3

Дата: 2026-11-02. Ревизия: `devel` / `551f6cb25` (Release 2.4.3).
Формат: найденное, доказательство, почему это важно, как чинить.

> Границы ревью: прочитаны ключевые пути приложения (БД, сеть, парсеры,
> reader, download, tracker, scrobbling, backups, Mihon, update, settings,
> manifest, CI). Построчно все ~85 000 строк Kotlin не проверялись.
> Дополнительно использованы Android Lint (отчёт от 2026-09-23),
> `compileDebugKotlin` и `testDebugUnitTest`.
>
> Парсеры: проект поддерживает **только RU/UK** источники. Парсеры других
> языков в `hanten-parsers` не входят в scope ревью.

---

## 0. Состояние сборки

| Проверка | Результат |
|---|---|
| `./gradlew :app:compileDebugKotlin --offline` | OK |
| `./gradlew :app:testDebugUnitTest --offline` | BUILD SUCCESSFUL |
| `./gradlew :app:lintDebug --offline` | не стартует offline: нет `androidx.test:rules:1.7.0` в кэше |
| старый `lint-results-debug.txt` | 22 error, 1170 warning; `lint.abortOnError=true` |
| App CI | `debug.yml`: `testDebugUnitTest` + `lintDebug` + `assembleDebug`; `release.yml` запускает их перед signed APK |
| Parser CI | добавлен `.github/workflows/ci.yml`: compile + pure JVM tests; RU/UA live sweep — только вручную |

Вывод: код компилируется и unit-тесты проходят, но `./gradlew check`
на текущем дереве, скорее всего, упадёт на lint. App CI этого не замечает,
потому что workflows вызывают только `assembleDebug`/`assembleRelease`.
Parser CI в текущем виде тоже не проверяет актуальный код.

---

## 1. Сводка

| # | Severity | Проблема | Файл |
|---|---|---|---|
| 1 | **P0** | Импорт библиотеки Shikimori всегда импортирует 0 тайтлов | `ShikimoriImportUseCase.kt` |
| 2 | **P0** | `AppBackupAgent` не получает Hilt-зависимость → auto-backup падает | `AppBackupAgent.kt` |
| 3 | **P0** | Telegram-токен попадает в zip-бэкап и в облачный backup | `BackupRepository.kt`, `backup_content.xml` |
| 4 | **P0** | В `local.properties` лежат живые Telegram-токены | `local.properties` |
| 5 | **P0** | Список sync-серверов содержит чужие и HTTP-адреса | `constants.xml`, `SyncSettings.kt` |
| 6 | **P0** | Проверка подписи Mihon-расширений написана, но не подключена | `InstalledExtensionSignatureValidator.kt` |
| 7 | **P1** | `Migration24To23` — downgrade-миграция, дропающая `chapters` | `Migration24To23.kt` |
| 8 | **P1** | Отмена `WebViewExecutor.evaluateJs` вешает mutex навсегда | `WebViewExecutor.kt` |
| 9 | **P1** | Битая страница считается скачанной; очередь теряется при сбое | `DownloadWorker.kt`, `DownloadSchedulerWorker.kt` |
| 10 | **P1** | Сеть трекеров внутри `database.withTransaction` | `MigrateUseCase.kt` |
| 11 | **P1** | Ярлыки пишут в БД на каждое изменение истории | `AppShortcutManager.kt` |
| 12 | **P1** | Флаг ассимиляции источников не сбрасывается при ошибке | `MangaSourcesRepository.kt` |
| 13 | **P1** | Stale-страницы в reader и отрицательный прогресс | `ReaderViewModel.kt`, `ProgressUpdateUseCase.kt` |
| 14 | **P1** | Время последней страницы теряется в статистике | `StatsCollector.kt` |
| 15 | **P1** | `@Relation` без `@Transaction` в `SuggestionDao` | `SuggestionDao.kt` |
| 16 | **P1** | Копирование `futon-db` неатомарно и глотает ошибки | `MangaDatabase.kt` |
| 17 | **P1** | Пароль приложения — несолёный MD5 в SharedPreferences | `ProtectSetupViewModel.kt` |
| 18 | **P1** | Битая строка manifest + 48 дублей host | `AndroidManifest.xml` |
| 19 | **P1** | Lint-ошибки: plurals cs/lt, annotation, PropertyEscape | `values-cs`, `values-lt`, `PagedContentParser.kt` |
| 20 | **P2** | Unconditional release-логи с URL | `WebViewRequestInterceptorExecutor.kt` и др. |
| 21 | **P2** | Мёртвый/опасный код и 82 неиспользуемых ресурса | см. раздел 7 |
| 22 | **P1** | Parser CI в `master` отсутствует, найденные workflow устарели; app CI не гоняет tests/lint | `.github/workflows` обоих репозиториев |

---

## 1a. Статус фиксов

Исправлено в рабочем дереве (без коммитов, тестов и релизов):

- Shikimori import: `ShikimoriImportUseCase` теперь сохраняет `rate` при
  совпадении, `matched` больше не пустой.
- `AppBackupAgent`: Hilt-зависимости через `@EntryPoint`, один экземпляр
  БД, auto-backup не падает.
- Секреты: `dumpSettings()`/restore не пропускают Telegram-токен/chat id/
  bot name и пароли; `backup_content.xml`/`backup_rules.xml` исключают
  prefs с токенами, cookies и scrobbler-токены.
- `local.properties`: токены удалены, добавлено пояснение.
- Sync: чужие HTTP-серверы удалены, `SyncSettings` не падает на пустом
  списке.
- Mihon: проверка подписи подключена, `LIB_VERSION_MAX` проверяется.
- Удалена `Migration24To23`.
- `WebViewExecutor.evaluateJs`: отмена больше не вешает mutex.
- Download: пропущенная страница не считается скачанной; transient-ошибки
  дают `Result.retry()`; scheduler ждёт любую активную работу.
- `MigrateUseCase`: сеть трекеров вынесена из транзакции.
- `AppShortcutManager`: убрана запись в БД на каждое обновление истории.
- `MangaSourcesRepository`: флаг ассимиляции сбрасывается при ошибке,
  external-источники не попадают в «выключенные».
- Reader/progress: stale-снимок проверяется целиком, отрицательный прогресс
  отсекается.
- `StatsCollector`: последний интервал перед паузой сохраняется.
- `SuggestionDao.getByIds`: `@Transaction`.
- `MangaDatabase`: копирование `futon-db` через temp + rename.
- `BackupUtils`: `SimpleDateFormat` заменён на `DateTimeFormatter`.
- App lock: PBKDF2 + соль вместо MD5, старый MD5 принимается и обновляется
  при успешном входе.
- Manifest: битый host удалён, host-only дубли в одном фильтре схлопнуты,
  `package=` убран; plurals cs/lt получили `many`.
- `PagedContentParser`: убрана неверная `@VisibleForTesting`.
- `MangaLinkResolver`: внешние ссылки валидируются, `/m` и `hanten://manga`
  обрабатываются.
- `AppUpdateFlow`: имя APK санитизируется.
- `TelegramBackupUploader`: response закрывается, ошибка содержит HTTP-код.
- Мёртвый код: удалены дубль `evaluateFilterPredicate`, `webViewCached`,
  `AppValidator.isOriginalApp`, no-op `BaseApp.attachBaseContext`,
  лишний try/catch в `MihonModule`, дубль `calculateSecondsUntilOffPeak`.
- RU/UK-парсеры: в `NudeMoonParser` `first()` заменён на `firstOrNull()`
  (падение при отсутствии формы тегов). Остальные RU/UK-парсеры просмотрены
  поверхностно, без live-прогона.

Не исправлено (осознанно, отдельные задачи):

- Parser CI: workflow в `master` отсутствуют, старые версии ссылаются на
  чужой пакет; это отдельная правка `.github`.
- App CI: `check`/tests/lint не добавлены (по просьбе — без тестов).
- `Sentry` opt-out до перезапуска.
- Очередь загрузок всё ещё удаляется после enqueue; transient-ошибки теперь
  ретраятся WorkManager, но полноценная state-машина очереди — отдельно.
- Mihon trust check пропускает всё, если ни один репозиторий не настроен:
  сравнивать не с чем, а Android и так проверяет ключ при обновлении.

**Порт Futon v9.9 (novels)**

Перенесено (ветка `upstream/devel`, коммит `14809e041`):
- `novel/` — reader, EPUB/PDF, local storage, `LocalNovelSource`;
- `Ranobes` (RU), `RanobeLib` (RU), `Baka.in.ua` (UA) как онлайн-источники
  новелл;
- `AppMode` + переключатель «манга/новеллы» в меню;
- скрытие вкладки страниц и page-fetcher для новелл;
- RU/UK строки.

Не переносилось по решению: onboarding, watermark, mode-switcher spotlight,
остальные 6 онлайн-источников новелл (EN/CN и др.), smart resume/prefetch/
история, импорт тем. Это отдельные задачи.

---

## 2. P0 — исправить до следующего релиза

### 2.1 Shikimori import всегда возвращает 0

`app/src/main/kotlin/hanten/wre/app/scrobbling/shikimori/domain/ShikimoriImportUseCase.kt:57-110`

```kotlin
val resolved = supervisorScope {
    rates.map { rate ->
        async {
            val match = findMatch(titles, sources)
            if (match == null) null to rate   // rate != null, manga == null
            else match to null                // manga != null, rate == null
        }
    }.awaitAll()
}
val matched = resolved.mapNotNull { (manga, rate) ->
    if (manga != null && rate != null) manga to rate else null
}
```

В `resolved` **никогда** нет пары, где оба элемента non-null: либо `match`,
либо `rate`, но не оба. Поэтому `matched` всегда пуст, импорт всегда 0.
`skipped` тоже неверный: он берёт только `null to rate`, а успешные
совпадения теряются.

Фикс:

```kotlin
if (match == null) null to rate else match to rate
...
val matched = resolved.mapNotNull { (manga, rate) -> manga?.let { it to rate } }
val skipped = resolved.mapNotNull { (manga, rate) -> if (manga == null) rate else null }
```

Добавить unit-тест на `importLibrary()` с 2–3 rate и проверкой
`ImportResult.imported == matched.size`.

### 2.2 `AppBackupAgent` не инжектится Hilt

`app/src/main/kotlin/hanten/wre/app/backups/domain/AppBackupAgent.kt:28-30`

```kotlin
class AppBackupAgent : BackupAgent() {
    @Inject
    lateinit var mihonExtensionManager: Provider<MihonExtensionManager>
```

Класс **не** `@AndroidEntryPoint`, базового `Hilt_AppBackupAgent` нет.
Dagger генерирует `AppBackupAgent_MembersInjector`, но им никто не
пользуется. При `onFullBackup()`/`onRestoreFile()` обращение к
`mihonExtensionManager` бросит `UninitializedPropertyAccessException`.
Итог: Android auto-backup не работает и может падать.

Фикс (любой):

1. Взять зависимости через `EntryPointAccessors.fromApplication(...)`
   в `onFullBackup`/`onRestoreFile`.
2. Или не требовать `MihonExtensionManager` в `BackupRepository`, а
   передавать готовый список источников/названий.
3. Или сделать отдельный `@EntryPoint` для backup.

Заодно: `onFullBackup` создаёт 2–3 экземпляра `MangaDatabase`
(`AppBackupAgent.kt:50,55,88,90`) и не закрывает их. Лучше один экземпляр
на операцию.

### 2.3 Telegram-токен внутри бэкапа

`app/src/main/kotlin/hanten/wre/app/backups/data/BackupRepository.kt:279-286`

```kotlin
private fun dumpSettings(): String {
    val map = settings.getAllValues().toMutableMap()
    map.remove(AppSettings.KEY_APP_PASSWORD)
    map.remove(AppSettings.KEY_PROXY_PASSWORD)
    map.remove(AppSettings.KEY_PROXY_LOGIN)
    map.remove(AppSettings.KEY_INCOGNITO_MODE)
    return JSONObject(map).toString()
}
```

Не удаляются:

- `KEY_BACKUP_TG_TOKEN = "backup_periodic_tg_token"`
- `KEY_BACKUP_TG_CHAT = "backup_periodic_tg_chat_id"`
- `KEY_BACKUP_TG_BOT_NAME`

Токен бота уходит в `settings`-секцию zip, а этот zip отправляется
**этим же ботом** в Telegram и лежит в SAF-папке. Кто получил бэкап —
получил возможность читать/удалять все бэкапы. Плюс Android cloud backup
включает все sharedpref (`res/xml/backup_content.xml`), то есть токен
уходит и в облако.

Фикс:

- удалять все `*_token*`, `*_password*`, `*_secret*`, chat id;
- хранить токены через `EncryptedSharedPreferences`/Keystore;
- исключить `backup_periodic_tg_*` из `backup_content.xml` и
  `backup_rules.xml`;
- добавить тест: после `dumpSettings()` в JSON нет `tg_token`.

### 2.4 Живые токены в `local.properties`

В рабочем `Hanten/local.properties` лежат `tg_crash_bot_token`,
`tg_crash_chat_id`, `tg_backup_bot_token` в открытом виде. Файл
gitignored, но он есть на диске и мог попасть в бэкапы/скриншоты/архивы.

Действия:

1. Ротировать оба токена у `@BotFather`.
2. Хранить только в env/CI secrets.
3. Добавить проверку в release-сборку, что токены не вшиты в APK
   (искать `AAG`/`bot` в `strings.xml`/BuildConfig).
4. Не логировать URL Telegram API: `TelegramBackupUploader.urlOf()`
   содержит токен в path.

### 2.5 Чужие sync-серверы и HTTP

`app/src/main/res/values/constants.xml:42-48`

```xml
<!-- Foreign sync servers, unused while is_sync_enabled=false -->
<item>https://sync.futonapp.pages.dev</item>
<item>https://moe.shirizu.org</item>
<item>http://54.254.71.100</item>
<item>http://86.57.183.214:8081</item>
```

`is_sync_enabled=false` (`bools.xml:8`), но список остался, а
`SyncSettings.syncUrl` по умолчанию берёт первый элемент. Если кто-то
включит sync (или старый аккаунт), библиотека и история уйдут на
сторонний сервер, два адреса — по HTTP без TLS.

Фикс:

- удалить список или оставить только свой HTTPS-сервер;
- при `is_sync_enabled=false` вообще не показывать/не создавать аккаунт;
- README не должен обещать sync, пока сервера нет (см. §6).

### 2.6 Mihon: проверка подписи не подключена

`InstalledExtensionSignatureValidator.kt` реализован, но:

- нигде не вызывается (grep по `InstalledExtensionSignatureValidator` —
  только объявление);
- `MihonLoadResult.Untrusted` нигде не создаётся;
- `MihonExtensionLoader` грузит любой APK с feature
  `tachiyomi.extension` или именем пакета + metadata;
- `LIB_VERSION_MAX = 2.5` объявлен, но не проверяется
  (`MihonExtensionLoader.kt:46,257`).

Mihon-расширение — это код в процессе приложения с доступом к cookies,
токенам и БД. Без проверки подписи репозитория любая подмена APK
(или сторонний репозиторий) исполняется без предупреждения.

Фикс: при загрузке расширения сверять SHA-256 сертификата с
`signatureHash` из `RepoAvailableExtension`; при несовпадении возвращать
`MihonLoadResult.Untrusted` и не грузить классы. Проверять
`libVersion > LIB_VERSION_MAX`.

---

## 3. P1 — баги и риск потери данных

### 3.1 `Migration24To23` — мёртвая и опасная

`core/db/migrations/Migration24To23.kt:6-10`

```kotlin
class Migration24To23 : Migration(24, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `chapters`")
    }
}
```

Она зарегистрирована в `getDatabaseMigrations()` (`MangaDatabase.kt:162`).
Room строит путь миграций только вверх; 24→23 никогда не применяется.
Но если кто-то добавит `fallbackToDestructiveMigrationOnDowngrade` или
поменяет логику — таблица глав будет удалена. Удалить класс и запись.

### 3.2 Отмена WebView `evaluateJs` вешает все парсеры

`core/network/webview/WebViewExecutor.kt:84-179`

Ветка с `baseUrl != null` использует `suspendCoroutine` (не
`suspendCancellableCoroutine`). Если корутина отменяется (пользователь
ушёл с экрана, WorkManager отменил работу), continuation не
возобновляется, `finally` не выполняется, `mutex.withLock` не
освобождается. Все последующие `evaluateJs`/`tryResolveCaptcha`/парсеры
ждут этот mutex бесконечно.

Фикс: `suspendCancellableCoroutine`, `invokeOnCancellation` →
`handler.removeCallbacksAndMessages(null)`, `webView.stopLoading()`,
`continuation.cancel()`; либо `withTimeout` вокруг всей ветки.

### 3.3 Загрузки: пропущенная страница считается скачанной

`download/ui/worker/DownloadWorker.kt:243-266`

```kotlin
launch {
    semaphore.withPermit {
        runFailsafe { output.addPage(...) }   // может вернуть null
        send(pageIndex)                        // выполняется всегда
    }
}
```

`runFailsafe` возвращает `null` после исчерпания попыток или когда
пользователь нажал «пропустить». `send()` всё равно увеличивает
прогресс, `flushChapter()` пишет главу без страницы. Пользователь
получает «скачано», но в CBZ дырка.

Фикс: проверять результат; при `null` — не отправлять страницу, а
пометить главу неполной/повторить; как минимум не увеличивать
`downloadedChapters`.

### 3.4 Очередь загрузок теряет элементы

`download/ui/worker/DownloadSchedulerWorker.kt:41-49,119-128`

- «активной» считается только `WorkInfo.State.RUNNING`; `ENQUEUED` и
  `BLOCKED` игнорируются, поэтому при незавершённой работе можно
  поставить ещё одну и получить параллельные загрузки.
- `removeFromQueue(nextItem.id)` вызывается сразу после `enqueue`;
  если `DownloadWorker` упадёт/будет отменён, строка очереди уже удалена,
  автоматического повтора нет.
- `DownloadWorker` при ошибке возвращает `Result.failure()`, а не
  `Result.retry()`, поэтому WorkManager тоже не повторит.

Фикс: удалять из очереди только после успешного завершения (или хранить
состояние `RUNNING/FAILED`), проверять все незавершённые работы, а для
транзиентных ошибок возвращать `Result.retry()`.

### 3.5 Сеть внутри транзакции при миграции

`alternatives/domain/MigrateUseCase.kt:48-118`

Внутри `database.withTransaction` вызываются
`scrobbler.unregisterScrobbling`, `linkManga`,
`updateScrobblingInfo`, `scrobble` — сетевые запросы. Это держит
SQLite write lock на секунды и откатывает всю миграцию при ошибке сети.
Комментарии в `TrackingRepository` и `HistoryRepository` уже фиксируют
это правило, а здесь оно нарушено.

Фикс: собрать данные в транзакции, выйти из неё, затем делать сеть;
локальные изменения применять отдельными транзакциями.

### 3.6 Ярлыки пишут в БД на каждое изменение истории

`core/os/AppShortcutManager.kt:140-142,169`

`buildShortcutInfo(manga)` вызывает
`mangaRepository.storeManga(manga, replaceExisting = true)`.
`updateShortcutsImpl()` вызывается на каждое `onInvalidated(TABLE_HISTORY)`,
то есть после каждого чтения. Для 5 ярлыков это 5 upsert-транзакций
подряд. Плюс `storeManga` с `replaceExisting=true` перезаписывает
метаданные снапшотом из истории (может затереть поля, которых в
снапшоте нет).

Фикс: убрать `storeManga` из `buildShortcutInfo` (ярлык хранит
`mangaId`, данные уже есть), либо делать один batch-upsert.

### 3.7 `assimilateNewSources` не восстанавливается после ошибки

`explore/data/MangaSourcesRepository.kt:315-346`

```kotlin
if (isNewSourcesAssimilated.getAndSet(true)) { ... return false }
...
val new = getNewSources()
dao.insertIfAbsent(entities)
```

Флаг ставится **до** работы. Если `getNewSources()`/`getMihonMangaSources()`
бросит (например, расширение Mihon не загрузилось), новые источники
не появятся до перезапуска процесса или изменения списка расширений.
Фикс: `try/finally` со сбросом флага при ошибке; либо `compareAndSet` после
успешной вставки.

Рядом: `queryParserSources()` при `isDisabledOnly` всегда добавляет
все external-источники (`:147-153`), хотя они могут быть включены.
Пользователь видит их во вкладке «выключенные».

### 3.8 Reader: stale-страницы и отрицательный прогресс

`reader/ui/ReaderViewModel.kt:343-351`

```kotlin
val pages = content.value.pages
...
if (pages.size != content.value.pages.size) return@launchJob
```

Сравнивается только размер. Если глава сменилась на другую с тем же
числом страниц, используются старые `pages` → неверный `chapterId/page`
и переход не туда. Сравнивать `content.value`/chapterId или версию
контента.

`ReaderViewModel.computePercent()` (`:591-603`) и
`ProgressUpdateUseCase` (`:46-53`) считают
`ppc * chapterIndex` без проверки `chapterIndex >= 0`. Если глава не
найдена в выбранной ветке, прогресс становится отрицательным.
`coerceIn(0f, 1f)` или `return PROGRESS_NONE`.

### 3.9 Статистика теряет последний интервал

`stats/domain/StatsCollector.kt:84-93`

`onPause()` отправляет `entry.stats`, но не добавляет время с
`entry.lastUpdate` до `now`. Комментарий говорит «flush what was
accumulated since the last state change», но накопленное обновляется
только в `onStateChanged()`. Время, проведённое на последней странице
перед паузой, теряется. Фикс: перед `trySend` добавить
`min(now - lastUpdate, MAX_PAGE_DURATION_MS)`.

### 3.10 Room: `@Relation` без `@Transaction`

`SuggestionDao.kt:74-75`

KSP предупреждает:

```
The return value includes a data class with a @Relation... annotate with @Transaction
```

`getByIds()` возвращает `MangaWithTags` без `@Transaction`, возможны
несогласованные данные. Добавить `@Transaction`.

### 3.11 Неатомарное копирование `futon-db`

`core/db/MangaDatabase.kt:182-197`

`migrateDatabaseFile()` копирует основной файл, `-wal`, `-shm` и
глотает все ошибки через `runCatching`. Если копирование прервалось
(нет места, процесс убит), `hanten-db` уже существует, следующий запуск
не повторит копирование, Room откроет частичную БД. Риск потери
библиотеки.

Фикс: копировать во временный файл, `fsync`, затем атомарный rename;
ошибку логировать/показывать, а не глотать; удалять temp при старте.

### 3.12 Пароль приложения: MD5 без соли

`settings/protect/ProtectSetupViewModel.kt:42`, `ProtectViewModel.kt:37-39`,
`core/prefs/AppSettings.kt:263`

Пароль хранится как `md5(password)` в обычных SharedPreferences,
минимум 4 символа. Без соли и KDF: rainbow-таблицы, подбор, одинаковые
хеши на устройствах. Плюс sharedpref попадает в cloud backup.
Фикс: PBKDF2/Argon2/scrypt с солью, ключ/соль в Android Keystore,
исключить prefs из backup; не хранить сам хеш в открытом виде.

### 3.13 Manifest: битый host и дубли

`app/src/main/AndroidManifest.xml:1562`

```xml
<data android:host="СЂРµРјР°РЅРіР°.РѕСЂРі" />
```

Это mojibake от `remanga.org` (cp1251, прочитанный как UTF-8). Строка
невалидна для App Links, lint `AppLinkUrlError`. Правильный host уже
есть строкой выше (`remanga.org`). Удалить.

Дубли: 1198 записей host, 1150 уникальных. Повторы:
`webtoons.com` ×14, `mangaplus.shueisha.co.jp` ×9, `mangafire.to` ×7,
`lib.social` ×3, `panconcola.com`/`mangaonlineteam.com`/`mangarockteam.com`
×2 и т.д. Это раздувает manifest и замедляет merge; удалить дубли
(скрипт: сгруппировать по host, оставить первое вхождение).

### 3.14 Lint-ошибки, блокирующие `check`

- `values-cs/plurals.xml` и `values-lt/plurals.xml`: нет quantity
  `many` — 18 ошибок `MissingQuantity`. Добавить формы.
- `mihon/parsers/core/PagedContentParser.kt:16`: `@VisibleForTesting`
  на constructor property — нужен корректный target (`@get:`/`@field:`) или
  снять аннотацию.
- `local.properties`: `sdk.dir=C:/...` — lint `PropertyEscape` требует
  экранировать `:` (`C\:/...`). Файл локальный, но lint его читает.
- `explore/data/MangaSourcesRepository.kt:387`: отступ пробелами среди
  табов — `SuspiciousIndentation` (не функционально, но error).

После исправления добавить `./gradlew check` в CI.

### 3.15 Ссылки: валидация внешнего ввода

`core/parser/MangaLinkResolver.kt:38-54`

```kotlin
require(uri.pathSegments.singleOrNull() == "manga") { "Invalid url" }
uri.getQueryParameter("id")?.toLong()
val sourceName = requireNotNull(...)
```

`hanten://manga` (host=`manga`, path пустой), `/m` (worker short link),
`?id=abc`, отсутствующий `s` — всё это `IllegalArgumentException` /
`NumberFormatException`. Сейчас в `DetailsViewModel` ошибка ловится и
показывается, но контракт «невалидная ссылка → NotFoundException»
нарушен, а `hanten://manga` из манифеста гарантированно не работает.
Фикс: проверять host/path/query, `toLongOrNull`, `requireNotNull` →
`NotFoundException`.

---

## 4. P2 — качество, производительность, мусор

### 4.1 Release-логи

`WebViewRequestInterceptorExecutor.kt` логирует каждый URL, метод и
`filterScript` через `Log.v/d` без `BuildConfig.DEBUG`. Там могут быть
токены в query string. Аналогично `DownloadSchedulerWorker`,
`DownloadWorker`, `CloudFlareInterceptClient`, `MihonNetworkHelper`,
`ExternalExtensionRepoRepository`. Это шум, лишние строки в logcat и
утечка URL. Обернуть в `if (BuildConfig.DEBUG)` или удалить
(`TAG_VRF`-логи особенно).

### 4.2 Дубли и мёртвый код WebView

- `MangaLoaderContextImpl.evaluateFilterPredicate()` (`:147-185`) —
  полная копия `WebViewRequestInterceptorExecutor.evaluateFilterPredicate`
  и нигде не вызывается. Удалить.
- `MangaLoaderContextImpl` комментарий «This is the key part that was
  missing!» (`:132`) — убрать.
- `WebViewRequestInterceptorExecutor.webViewCached` пишется, но нигде
  не читается для получения WebView. Удалить поле.
- `WebViewRequestInterceptorExecutor` на каждый запрос делает
  `clearCache(true)` — сбрасывает HTTP-кэш WebView. Для парсеров это
  может быть дорого; кэш чистить только при смене домена/по требованию.

### 4.3 Telegram uploader

`backups/ui/periodical/TelegramBackupUploader.kt:118-127`

`consume()` при не-2xx и `ok=true` не закрывает response. Также ошибка
отправки в Telegram после успешного локального бэкапа вызывает
уведомление «создание бэкапа не удалось», хотя файл создан. Разделить
ошибки: «бэкап создан, Telegram не отправился».

### 4.4 Обновление приложения

- `AppUpdateFlow.kt:220`: `"Hanten-${release.tag}.apk"` — tag приходит
  из GitHub. Если тег содержит `/`/`..`, файл уйдёт за пределы
  `cache/updates`. Санитизировать (`[^A-Za-z0-9._-]` → `_`).
- `MainActivity.kt:389`: nightly пропускает проверку обновлений, а
  отдельного nightly-флоу нет (CI.md говорит, что nightly не собирается).
  Либо убрать nightly, либо добавить канал обновлений.
- `AppUpdateFlow.kt:248-272`: прогресс-диалог живёт в `lifecycleScope`
  Activity; при повороте/уходе job отменяется, файл удаляется в
  `AppUpdateDownloader`. Приемлемо, но стоит вынести в WorkManager,
  если нужны фоновые обновления.

### 4.5 Sentry: opt-out не мгновенный

`core/BaseApp.kt:76-78` инициализирует Sentry только при старте, если
отчёты включены. Если пользователь выключил тумблер в настройках,
Sentry остаётся активным до перезапуска. `Throwable.report()` в release
отправляет в Sentry, не проверяя `isErrorReportsEnabled` (полагается на
то, что Sentry выключен). Добавить `settings`-проверку в `report()` и
вызывать `Sentry.close()`/переинициализацию при смене настройки.

### 4.6 `SimpleDateFormat` как singleton

`backups/domain/BackupUtils.kt:16`

`private val dateTimeFormat = SimpleDateFormat(...)` — не потокобезопасен.
`createTempFile()` и `parseBackupDateTime()` могут вызываться из разных
корутин (ручной + периодический бэкап). Заменить на
`DateTimeFormatter`/`java.time` или `synchronized`.

### 4.7 Архивные/мёртвые файлы

- `app/src/nightly/` — вариант `nightly` не собирается (CI.md: workflow
  удалён), но код и ресурсы поддерживаются. Удалить вариант или
  вернуть workflow.
- `core/os/AppValidator.kt:21` — `isOriginalApp` нигде не используется.
- `mihon/MihonModule.kt:28-41` — `try/catch` с `throw e` не даёт эффекта.
- `MihonExtensionLoader.LIB_VERSION_MAX` не используется.
- `MihonLoadResult.Untrusted` не создаётся (см. P0).
- `BaseApp.attachBaseContext` — пустой override с комментарием «ACRA removed».
- `DownloadSchedulerWorker` — `calculateSecondsUntilOffPeak()` продублирован
  в instance и companion (`:201-219`, `:286-304`).
- `res/values-sw360dp/bools.xml` — `config_materialPreferenceIconSpaceReserved`.
- `debug/res/values/bools.xml` — `leak_canary_add_launcher_icon`,
  `wi_launcher_icon_enabled`.
- Пустой `FullStack.java` в корне workspace (вне git-репозитория Hanten);
  каталоги `TEMP/` и `release/` со старыми APK/HTML — вне репозитория,
  но занимают место.

### 4.8 Неиспользуемые ресурсы (Lint, 82 шт.)

Основные группы:

- цвета: `bottom_menu_active_indicator`, `bottom_menu_active_item`,
  `colored_button`, `surface_amoled`, `dim2`, `launcher_background`,
  `background_miku/asuka/mion/rikka/sakura/mamimi/kanade`, все `*_scrim`;
- drawable: `ic_action_name`, `ic_data_privacy`, `ic_discord`,
  `ic_gesture_vertical`, `ic_move_horizontal`, `ic_select_group`,
  `ic_sort_asc/desc`, `search_bar_background`, `tabs_background`,
  `ic_stat_name`;
- строки: `app_icon_url`, `github_download_warning`,
  `feed_will_update_soon`, `data_restored`, `about_app_translation`,
  `exclude_nsfw_from_history(+_summary)`, `onboard_text`,
  `new_sources_text`, `send`, `show_reading_indicators_summary`,
  `manga_error_description_pattern`, `reader_slider`,
  `network_unavailable_hint`, `images_procy_description`,
  `add_extension_repo`, `sort_order_asc/desc`, `unstable_feature(+_summary)`,
  `save_manga_confirm`, `show_slider`, `error_disclaimer_app_outdated`,
  `obtain`, `reading_s`, `read_on_s`, `invalid_token`,
  `github_mirror_native/kkgithub/ghproxy/ghproxy_net`, `installed`,
  `rescan_completed`, `rescan_started`;
- стили: `Widget_Hanten_SearchView`, `Widget_Hanten_Slider_Wide`,
  `ThemeOverlay_Hanten_MainToolbar`, `ThemeOverlay_Hanten_ActionMode`,
  `TextAppearance_Hanten_SearchView`, `TextAppearance_Widget_Menu`,
  `TextAppearance_Hanten_Preference_Secondary`;
- dimen: `margin_large`, `bookmark_item_height`, `m3_side_sheet_width`,
  `m3_card_corner`;
- values: `config_tinyAnimTime`, `splash_screen_duration`,
  `size_s`.

Удалять после проверки, что они не нужны runtime-рефлексии/темам.

### 4.9. CI двух проектов: что есть и что сломано

> Обновлено после фиксов: в app CI добавлены `testDebugUnitTest`/`lintDebug`,
> в `hanten-parsers` создан `.github/workflows/ci.yml` (compile + pure JVM
> tests + manual RU/UA sweep). Ниже — история проблемы и требования к CI.

Важно не путать два репозитория: app и parser library — это разные
проекты со своими workflow.

**Hanten (app), `.github/workflows/`:**

- `debug.yml` — PR в `main`/`devel`: `assembleDebug`, artifact 7 дней,
  комментарий в PR. Java 17, SDK 36.
- `release.yml` — tag `v*` или ручной запуск: сверяет версию в
  `AndroidManifest.xml` и `app/build.gradle`, собирает signed release,
  вытаскивает секцию из `CHANGELOG.md`, создаёт GitHub Release.
- `issue_moderator.yml` — автозакрытие issue по шаблону.

Сейчас: `debug.yml` запускает `testDebugUnitTest` + `lintDebug` +
`assembleDebug`, `release.yml` — те же проверки перед signed APK.
Instrumented tests по-прежнему не запускаются (нет стабильного emulator
в CI).

**hanten-parsers:**

> Сейчас: создан `.github/workflows/ci.yml` (compile + pure JVM tests),
> RU/UA live sweep — только `workflow_dispatch`. Старые workflow из
> `upstream` не используются: они ссылались на чужой пакет и Java 21.

- В `upstream/master` и `origin/feat/automate-parser-check` лежат
  `parser-check.yml`, `test-branch.yml`, `test-parsers.yml`.
- В текущем `master`/`origin/master` их **нет**: rebase-коммит
  `18adf2f8` удалил `.github/workflows`.
- Проверка GitHub API на 2026-11-02:
  - `DezFix/Hanten` — 3 активных workflow (`debug`, `release`,
    `issue moderator`);
  - `DezFix/hanten-parsers` — `total_count: 0`, CI нет;
  - `AppFuton/futon-parsers` — 4 workflow, но `parser-check` и
    `test-branch` в состоянии `disabled_manually`, активны только
    `test-parsers` и Copilot review.
- Найденные версии устарели:
  - Java 21 вместо проектных 17;
  - пути и import ссылаются на
    `io.github.landwarderer.futon.parsers`, а не на
    `hanten.wre.app.parsers`;
  - `test-parsers.yml` триггерится только на старый путь
    `src/main/kotlin/io/github/...`, поэтому на текущие PR не реагирует;
  - `parser-check.yml` ищет `@Broken`/домены в старом каталоге,
    то есть отчёт будет пустым или неверным;
  - тесты (`./gradlew test`) не запускаются, только `compileKotlin`.

Сделано: новый `ci.yml` использует Java 17, актуальные пути
`hanten.wre.app.parsers`, pure JVM tests и manual RU/UA sweep. App CI —
`debug.yml`/`release.yml` с tests+lint.

---

## 5. Безопасность и приватность (сводно)

| Риск | Где | Комментарий |
|---|---|---|
| Telegram bot token в бэкапе | `BackupRepository.dumpSettings` | P0 |
| Telegram token в `local.properties` | workspace | ротировать |
| Чужие sync-серверы, HTTP | `constants.xml` | P0 |
| Mihon-расширения без проверки подписи | `MihonExtensionLoader` | P0 |
| Пароль приложения MD5 без соли | `ProtectSetupViewModel` | P1 |
| OAuth-секреты в ресурсах | `constants.xml:12-16` | для public client это ожидаемо, но секреты можно извлечь; использовать PKCE, где поддерживается |
| `network_security_config` доверяет user CA | `res/xml/network_security_config.xml` | нужно для прокси/отладки, но повышает риск MITM |
| `isSSLBypassEnabled` | `NetworkModule` | глобальное отключение проверки сертификата; оставить только для debug/явного предупреждения |
| Auto backup всех sharedpref | `backup_content.xml`, `backup_rules.xml` | токены/пароли/настройки уходят в облако |
| `QUERY_ALL_PACKAGES` | manifest | нужно для Mihon-расширений, но политика Google Play; для GitHub-дистрибуции ок |
| Экспортированные Activity | `DetailsActivity`, `SettingsActivity`, `SyncAuthActivity`, extension store | принимают внешние Intent; `MangaLinkResolver` валидирует слабо (P1) |
| Release-логи URL | WebView/Download/Mihon | P2 |
| `allowBackup=true` + `restoreAnyVersion=true` | manifest | восстановление старых/чужих данных; проверить сценарии |

### 5.1. Результаты secret-scan (2026-11-02)

Проверено: текущее дерево обоих репозиториев, `git grep` по паттернам
Telegram/AWS/GitHub/Google/OpenAI/Slack, и история app-репозитория.

- В **текущем дереве** app нет Telegram/AWS/GitHub-токенов, приватных
  ключей и keystore. `local.properties` очищен, `.jks`/`.keystore` не
  отслеживаются.
- В **истории app** остались старые Telegram-токены (коммиты
  `26b512d42`, `3ef7c6adb`, `07e81f21c`, `e39e5bf9c`, `a7d4f3b78`,
  `afa18e086`, `9f965c526`, 2024-09…2025-08). Они удалены из текущего
  кода, но восстановимы из публичной истории. Нужно отозвать их у
  `@BotFather`; историю можно почистить `git filter-repo`, но это
  перепишет SHA и не уберёт копии у тех, кто уже клонировал.
- В **hanten-parsers** есть `site/fmreader/ja/Klz9.kt` с захардкоженным
  `clientSecret` стороннего сайта. Это вне RU/UK-scope, но секрет
  публичный; лучше вынести/удалить вместе с источником.
- `constants.xml` содержит `shikimori_clientSecret` и
  `anilist_clientSecret`. Для публичных мобильных OAuth-клиентов это
  не «секрет» в классическом смысле (извлекается из APK), но любой
  может выдать себя за приложение. Где поддерживается — использовать
  PKCE; иначе считать это принятым риском.
- OAuth-токены Shikimori/AniList/MAL хранятся в обычных SharedPreferences
  (`ScrobblerStorage`), не в Keystore. Из cloud backup они исключены,
  но на рутованном устройстве/в локальном бэкапе доступны.
- `network_security_config` разрешает cleartext и user CA; это нужно для
  прокси/отладки, но повышает риск MITM. `isSSLBypassEnabled` полностью
  отключает проверку сертификата — держать только как явную опцию.

---

## 6. Расхождения документации и кода

1. `README.md` перечисляет **Kitsu** в трекерах, но Kitsu удалён в 2.3.1
   (`CHANGELOG.md`). Убрать из README.
2. `README.md` обещает «Автоматическая синхронизация данных между
   устройствами на одном аккаунте», но `is_sync_enabled=false` и своего
   сервера нет. Либо убрать пункт, либо пометить «экспериментально/нужен
   свой сервер».
3. `AGENTS.md` говорит «Weblate for Hanten pending», но README/CHANGELOG
   уже используют Weblate.
4. `AGENTS.md` описывает `assembleNightly`, а `CI.md` — что nightly
   workflow удалён, вариант не используется.
5. `DESIGN.md` задаёт шрифт Inter и часть токенов; в приложении нет
   `res/font`, `fontFamily` не задан, часть цветов/стилей не используется.
   Документ стоит пометить как «целевой дизайн», а не «текущее состояние».
6. `CI.md` говорит «Local builds pick it up automatically via user env
   vars» — при отсутствии env `app/build.gradle` пытается читать
   `System.console()`, что в IDE/CI без консоли даёт `null` и падение
   подписи. Уточнить.

---

## 7. Мёртвый код и мусор — чеклист

- [x] Удалить `Migration24To23` и регистрацию.
- [x] Удалить `MangaLoaderContextImpl.evaluateFilterPredicate`.
- [x] Удалить `WebViewRequestInterceptorExecutor.webViewCached`.
- [x] Удалить `AppValidator.isOriginalApp`.
- [x] Подключить `InstalledExtensionSignatureValidator`, `Untrusted`,
      `LIB_VERSION_MAX`.
- [x] Удалить пустой `BaseApp.attachBaseContext`.
- [x] Удалить дублирующий `calculateSecondsUntilOffPeak` в
      `DownloadSchedulerWorker`.
- [ ] Удалить `app/src/nightly` или вернуть nightly CI.
- [ ] Почистить 82 unused resources (начать с drawable/color/style).
- [x] Удалить битый host и host-only дубли в одном фильтре; остальные
      повторы — разные схемы/фильтры и оставлены осознанно.
- [ ] Почистить `TEMP/`, `release/`, пустой `FullStack.java` в workspace.
- [ ] Удалить `url_telegram*` пустые строки и скрытый пункт, если
      Telegram-канал не планируется.

---

## 8. Порядок исправлений

1. **Сегодня**: Shikimori import, `AppBackupAgent`, токены в бэкапе и
   `local.properties`, sync-серверы, проверка подписи Mihon.
2. **До релиза**: `Migration24To23`, WebView cancellation, download
   queue/page, `MigrateUseCase` transaction, reader progress, stats.
3. **Следующий спринт**: manifest/plurals/lint, release-логи, MD5,
   авто-бэкап prefs, shortcut DB writes, `SimpleDateFormat`.
4. **Постоянно**: `./gradlew check` в CI, тесты на импорт/экспорт,
   миграции, download-очередь.

---

## 9. Что не проверялось

- Построчный аудит всех парсеров `hanten-parsers` — только RU/UK
  (34 файла), и то поверхностно; остальные языки вне scope.
- Реальные сетевые сценарии всех источников (нужен живой прогон).
- Instrumented tests (`connectedDebugAndroidTest`) — нет устройства.
- Полный `lintDebug` в онлайн-режиме (offline не хватило зависимости).
- Воспроизведение багов на устройстве: выводы основаны на коде,
  сгенерированных Hilt-классах, Lint и unit-тестах.
