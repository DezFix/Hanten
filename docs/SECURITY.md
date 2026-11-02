# Безопасность Hanten

Дата проверки: 2026-11-02. Проверены текущее дерево `Hanten` и
`hanten-parsers`, история app-репозитория и CI-workflows.

## TL;DR

- В **текущем коде** нет живых Telegram-токенов, приватных ключей,
  AWS/GitHub/OpenAI-ключей и keystore.
- В **истории app** остались старые Telegram-бот-токены. Их нужно
  отозвать; при желании — почистить историю через `git filter-repo`.
- В `hanten-parsers` есть захардкоженный `clientSecret` стороннего
  **японского** парсера (`Klz9`). Это вне RU/UK-scope, но секрет публичный.
- OAuth-секреты Shikimori/AniList в `constants.xml` — публичные
  client secrets мобильного приложения. Это не «утечка ключа сервера»,
  но любой может собрать APK/выдать себя за клиент. Где можно — PKCE.
- Остаточные риски: plain SharedPreferences для OAuth-токенов,
  `network_security_config` с user CA/cleartext, `isSSLBypassEnabled`,
  Mihon-расширения как код в процессе.

## 1. Secret scan

Проверялось:

```text
git grep по Telegram-токенам, AWS AKIA, ghp_/github_pat_, AIza,
sk-, xox[baprs]-, BEGIN ... PRIVATE KEY
git log -G "[0-9]{8,10}:[A-Za-z0-9_-]{35}"
git ls-files для *.jks, *.keystore, local.properties, sentry.properties
```

Результат:

| Место | Найдено | Статус |
|---|---|---|
| `Hanten/local.properties` | Telegram crash/backup токены | **удалены**, файл gitignored |
| История `Hanten` | старые Telegram-токены в 7 коммитах (2024-09…2025-08) | **требуют отзыва**; текущий код чист |
| `Hanten/app/src/main/res/values/constants.xml` | `shikimori_clientSecret`, `anilist_clientSecret` | публичные client secrets; принятый риск / PKCE |
| `Hanten` keystore | не найден | `*.jks`/`*.keystore` в `.gitignore` |
| `hanten-parsers` | `site/fmreader/ja/Klz9.kt`: `clientSecret = "..."` | вне RU/UK-scope; вынести/удалить вместе с источником |
| `hanten-parsers` cookies/tests | `src/test/resources/cookies.txt` | в `.gitignore`, не отслеживается |
| CI | секреты только через `${{ secrets.* }}` | ок |

## 2. Что делать с историческими токенами

1. Отозвать все старые токены у `@BotFather`.
2. Выпустить новые, хранить только в локальном secret store/CI secrets,
   не в `local.properties` в открытом виде.
3. Опционально: `git filter-repo --replace-text` или BFG, затем
   force-push. Учтите: это меняет SHA, ломает клоны и не удаляет токены
   у тех, кто уже склонировал репозиторий. Поэтому **отзыв важнее чистки**.
4. Добавить в CI secret-scan (gitleaks/trufflehog) с allowlist только для
   заведомо публичных client secrets.

## 3. App lock и пароли

- Пароль приложения: PBKDF2-HmacSHA256 (fallback SHA1 на старых Android)
  + случайная соль, 120k итераций; старый MD5 принимается и обновляется
  при успешном входе.
- Пароль/токены не попадают в app-backup (`dumpSettings`/restore) и
  исключены из Android cloud backup для default prefs.
- Остаток: OAuth-токены трекеров лежат в обычных SharedPreferences.
  Для усиления — `EncryptedSharedPreferences`/Keystore.
- Биометрия — `BIOMETRIC_WEAK`/Class 2; для параноидального режима можно
  поднять до Class 3, но это отрежет часть устройств.

## 4. Бэкапы

- `BackupRepository` не пишет в zip: пароль приложения, proxy login/password,
  incognito, Telegram token/chat id/bot name.
- `backup_content.xml`/`backup_rules.xml` исключают default prefs,
  cookies, `SHIKIMORI`/`ANILIST`/`MAL`.
- Бэкап по-прежнему **не шифруется**. Кто получил zip — получил библиотеку,
  историю и настройки. Для чувствительных библиотек нужен пароль на архив.
- Telegram-бэкап уходит через бота пользователя; токен бота — в Keystore/
  prefs, не в APK.

## 5. Сеть

- `network_security_config` разрешает cleartext и user CA. Это нужно для
  прокси/отладки, но позволяет MITM, если пользователь добавил CA.
- `isSSLBypassEnabled` полностью отключает проверку сертификата. Держать
  только как явную экспериментальную опцию с предупреждением.
- `CurlLoggingInterceptor` (debug) редактирует `bot<token>`, query-секреты
  и заголовки. В release логи URL/токенов приглушены в WebView-коде.
- `RateLimitInterceptor` и CloudFlare-обработка не логируют токены.

## 6. Mihon-расширения

- Расширение — произвольный код в процессе приложения. Сейчас:
  - проверяется подпись по fingerprint'ам настроенных репозиториев;
  - проверяется `LIB_VERSION_MIN/MAX`;
  - если ни один репозиторий не настроен, проверка пропускается
    (сравнивать не с чем), но Android всё равно не даст обновить APK
    другим ключом.
- Рекомендация: показывать «непроверенное расширение» в UI и требовать
  явное подтверждение, а не молча пропускать.

## 7. Приватность и телеметрия

- Sentry/Bugsink включаются только при opt-in; DSN — публичный ключ.
- Opt-out Sentry применяется после перезапуска; при следующей правке —
  закрывать клиент сразу при выключении тумблера.
- `SourceErrorReporter` отправляет `error.message` (до 500 символов).
  Если в сообщении URL с токеном — возможна утечка; стоит прогнать через
  тот же redactor, что и debug curl-лог.
- `QUERY_ALL_PACKAGES` нужен для Mihon-расширений; для Google Play это
  политический риск, для GitHub-дистрибуции — ок.

## 8. Чеклист перед релизом

- [ ] Отозвать старые Telegram-токены, проверить, что новых нет в git.
- [ ] Прогнать `gitleaks detect --no-git` и `gitleaks detect` (история).
- [ ] Проверить, что `local.properties`/`sentry.properties`/`*.jks` не в индексе.
- [ ] Собрать release и проверить `aapt dump permissions` / manifest.
- [ ] Проверить подпись APK (`apksigner verify --print-certs`).
- [ ] Проверить, что backup zip не содержит `tg_token`, `password`, `secret`.
- [ ] Проверить, что CI не печатает секреты в логах.
- [ ] Для публичного репозитория — решить, чистить ли историю от старых токенов.
- [ ] Для усиления supply-chain: запинить GitHub Actions на commit SHA,
      а не на major-теги (`@v4`), особенно сторонние actions.
