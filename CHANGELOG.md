# Changelog

All notable changes to this project are documented in this file.

The format is based on "Keep a Changelog" and follows semantic versioning where possible.

## 2.4.1
Date: 2026-09-22

### Починили источники (парсеры v2026.09.22)
- ReadManga, MintManga, SeiManga: авторы и статусы с новой вёрстки
- MangaLib: описания больше не сырым JSON, даты глав починены
- Remanga: авторы из карточки тайтла
- TomiloLib: главы грузятся параллельно, рейтинг и тома поправлены
- MangaLib.com: рейтинг с сайта
- ZenManga стал InkStory: новый API, каталог, страницы
- MangaWtf: поднят на новом API
- JoiMang: удалённое по жалобе правообладателя помечается недоступным сразу, без долгого зависания

## 2.4.0
Date: 2026-09-22

### Библиотека стала умнее
- Значки статуса на обложках: «Завершена», «Заброшена» и «Давно без обновлений» — видно сразу, не открывая тайтл
- Молчуны находятся сами: если новых глав нет 90+ дней, карточка помечается (работает только по уже загруженным главам)

### Статистика
- Над графиком появилась карточка итогов за период: время чтения, страницы и среднее в день

### Поделиться и шорткаты
- В «Поделиться» добавился QR-код со ссылкой на мангу: навёл камерой — открылось в приложении
- Долгое нажатие на иконку: быстрый доступ к Истории и Обновлениям, дальше — недавняя манга

### Первый запуск
- В приветствии теперь выбирается и вид библиотеки (сетка, список, подробный)
- Приветствие больше нельзя смахнуть случайно — закрывается только кнопкой «Готово»

## 2.3.7
Date: 2026-09-22

### Трекинг
- Импорт/выгрузка Shikimori: совпадение названий по обоим вариантам (оригинал + русское) — русские библиотеки больше не пропускаются целиком

## 2.3.6
Date: 2026-09-22

### Трекинг
- Импорт библиотеки из Shikimori в избранное с привязками трекинга
- Выгрузка библиотеки из приложения в Shikimori

### Чистка
- Удалены мертвые строки (ночные обновления, старые бэкапы, Kitsu-логин, RPC-заглушка и др.)

## 2.3.5
Date: 2026-09-22

### Трекинг
- Shikimori переехал на shikimori.io: API снова отвечает, вход работает
- Импорт библиотеки из Shikimori: вся коллекция в избранное с привязками трекинга (меню ⋮ на экране трекера)

### Рекомендации
- Две вкладки: «Подборка» и «В тренде» (топы твоих источников)

## 2.3.4
Date: 2026-09-21

### Трекинг
- Shikimori: запрашиваем scope user_rates (без него токен бесправный) и шлём credentials при обновлении токена — вход работает, refresh чинится сам

## 2.3.3
Date: 2026-09-21

### Трекинг
- Shikimori: починена загрузка профиля при протухшем токене (было JSON-падение) — токен обновляется сам, иначе просит войти заново

## 2.3.2
Date: 2026-09-21

### Трекинг
- Ошибки трекеров теперь улетают в анонимные отчёты со стектрейсом — чиним вслепую меньше
- AniList больше не падает, если сервер вернул ошибку вместо данных

## 2.3.1
Date: 2026-09-21

### Трекинг
- Свои OAuth-приложения Shikimori, AniList и MAL (были чужие ключи Kotatsu/Futon)
- Kitsu удалён (сервис мёртв): остались 3 трекера, старые привязки чистятся миграцией БД
- Парсинг ответов больше не падает на тайтлах без обложки и пустых полях
- Ошибки OAuth теперь человекочитаемые

### Время чтения
- Простой дольше 2 минут больше не портит среднюю скорость чтения

## 2.2.2
Date: 2026-09-20

### Новое
- Автобэкапы в Telegram через нашего бота @hanten_bot: копия бэкапа улетает в личку после каждого периодического бэкапа
- Один пункт «Анонимные отчёты об ошибках» вместо двух: падения и ошибки источников в одном тумблере, без личных данных
- Время чтения на карточке манги теперь на отдельной строке — не обрезается

### Своё лицо
- Иконки из IconKitchen применены везде (адаптивная, монохромная, сплэш, стор)
- Донат Ko-fi переведён на dezfix
- Переводы подключены к Weblate (проект hanten)

## 2.2.1
Date: 2026-09-20

### Новое
- Глобальная защита от «Too Many Requests»: при массовых проверках новых глав приложение само делает паузу по Retry-After и повторяет позже — одна на все источники
- Экран «О программе»: пункт со ссылкой на сайт приложения
- README полностью на русском

## 2.2.0
Date: 2026-09-20

### Новое
- Кнопка «Читать» всегда открывает первую главу (по минимальному номеру)
- Каталог MangaLib.com скрывает тайтлы с битыми обложками

### Стабильность и скорость
- Прогресс чтения сохраняется при сворачивании, а не только при закрытии
- Точное восстановление страницы после смерти процесса
- Отмена загрузки главы больше не превращается в ошибку
- Индексы базы данных + миграция 30 → 31: быстрее история, трекер, избранное
- Ограничен рост памяти в читалке, закрыты утечки фрагментов
- Куки без синхронного fsync на сетевом потоке, логи без секретов в релизе

### Починили источники (парсеры v2026.09.20.x)
- ZenManga, MangaWtf: переезд на inkstory.net (домены и API)
- AComics: полный редизайн — новые селекторы каталога, тегов, деталки, читалки
- MangaLib: кэш авторизации (каталог грузится секунды вместо минут), картинки с живых CDN, описания TipTap
- MangaLib.com: повтор при пустом списке глав, WebView-фолбэк, фильтр ранобэ у DgManga
- JoiMang: сначала plain HTTP, WebView только под чекпоинт
- HenChan: живой primary-домен; MangaMammy отключен

## 2.1.14
Date: 2026-09-17

### Новое
- Кнопка «Читать на сайте источника» теперь работает для всех источников, а не только избранных

## 2.1.13
Date: 2026-09-16

### Исправления
- В шаринг-ссылках снова настоящие названия (без транслита)
- На странице по ссылке: обложка, а если она не грузится — иконка приложения

## 2.1.12
Date: 2026-09-16

### Новое
- Шаринг вернулся на ссылки сайта: короткие, с названием и обложкой; на странице все 4 кнопки — в приложении, на сайте источника, скачать, Ko-fi

## 2.1.11
Date: 2026-09-16

### Новое
- Короткие шаринг-ссылки: название транслитом, без обложки в адресе — на странице наша иконка, превью в мессенджерах с названием

## 2.1.10
Date: 2026-09-16

### Новое
- Шаринг-ссылки несут название и обложку: страница по ссылке теперь показывает мангу даже с закрытых сайтов, кнопка «Читать на сайте» собирается сама

## 2.1.9
Date: 2026-09-16

### Новое
- Шаринг через новый адрес: короткие ссылки без кириллицы, в мессенджерах — превью с названием и описанием, открываются сразу в приложении
- Сайт dezfix.github.io: реальное описание, документация с правами и лицензиями, русский и английский языки
- Кнопка доната Ko-fi на сайте

### Исправления
- Старые длинные ссылки продолжают открываться (обратная совместимость)

## 2.1.8
Date: 2026-09-16

### Новое
- Карусель «Похожая манга»: если источник не даёт своих рекомендаций, приложение само подбирает до 3 похожих тайтлов по жанрам из того же источника, кнопка «Ещё» открывает полный список
- Ссылки «Поделиться в приложении» теперь ведут на dezfix.github.io: с приложением тайтл открывается сразу, без — страница со ссылками на приложение и сайт источника
- Сайт приложения: dezfix.github.io — описание, документация, открытие shared-ссылок

## 2.1.7
Date: 2026-09-15

### Исправления
- Премиум-манга семейства Grouple (ReadManga, MintManga, SelfManga, SeiManga, Usagi, AllHentai) больше не показывается в каталоге и не падает с ошибкой «Pages list not found» — закрытые главы скрываются, целиком платные тайтлы помечаются как недоступные
- Парсеры обновлены до v2026.09.11

## 2.1.6
Date: 2026-09-07

### Исправления
- Цифры оценки рядом со звёздами теперь видно
- TomiloLib: полный список глав (пагинация по флагу сервера)

## 2.1.5
Date: 2026-09-07

### Новое
- На экране манги рядом со звёздами теперь цифры оценки
- Новый диалог обновления: заголовок по центру, блок чейнджлога, кнопки с иконками

### Починили источники
- Readmanga, Mintmanga, Seimanga: вернули описания, оценки и статус (сайты сменили вёрстку); у Readmanga живой домен readmanga.me
- Senkuro: оценки из API
- Com-X: оценки в деталях
- TomiloLib: ретраи и постраничная загрузка глав
- WaManga: чтение оценки из API

## 2.1.4
Date: 2026-09-06

### Починили источники
- JoiMang: обход Vercel-защиты через WebView
- MangaLib.com: починка каталога и поиска

## 2.1.3
Date: 2026-09-06

### Новое
- Новые источники: Zenko, DgManga (украинские), Mangaddict (английский), MangaLib.com, JoiMang
- Рекомендации: интервал обновления «Вручную» (без автообновления) и обновление свайпом

### Починили источники
- Usagi скрыт из списка: сайт блокирует страницы манги антиботом («Ошибка =)» 500)

### Исправления
- Избранное: в фильтрах больше не показываются источники, с которых нет манги

## 2.1.2
Date: 2026-09-05

### Починили источники
- Com-X: каталог снова показывает мангу (сервер отдавал пустую страницу вместо списка)

## 2.1.1
Date: 2026-09-05

### Починили источники
- Com-X: каталог снова показывает мангу (добили обход защиты сайта)

## 2.1
Date: 2026-09-05

### Починили источники
- Com-X: каталог снова показывает мангу (обход защиты сайта)

## 2.0
Date: 2026-09-05

### Новое
- Новый источник: ReComics
- Экран «О программе»: иконки у пунктов и шапка с логотипом

### Починили источники
- Com-X: переписан под новый сайт (каталог снова показывает мангу)
- HoneyManga: у глав без названий теперь подписаны том и раздел
- MANGA Plus (русский): в списке теперь весь каталог, а не одна манга
- WaManga: повторная попытка при обрывах соединения
- MangaMammy: переезд на новое зеркало
- MangaOneLove: переезд на новые зеркала
- Яой-тян: добавлено новое зеркало

### Убрали мёртвые источники
- Mangazavr — сайт мёртв, рабочих зеркал нет
- WeebDex (все языки) — сервис закрылся

## 1.1.9
Date: 2026-09-05

### Новое
- Новый источник: ReComics

### Починили источники
- HoneyManga: главы грузятся все, починили страницы, статусы и обложки

## 1.1.8
Date: 2026-09-05

### Fixes
- HoneyManga chapters: tolerant parsing, fixed frames URL, empty-list diagnostics
- ChanParser: new im. markup chapters, no crash on missing container

### Maintenance
- Parsers 188cb1fd
- versionName 1.1.8 / versionCode 10108

## 1.1.7
Date: 2026-09-04

### Highlights
- Usagi served via a.zazaza.me mirror (same catalog, no anti-bot wall)

### Fixes
- HoneyManga: WebView fallback for API posts, correct frames URL, resilient chapters
- Manga-Chan chapter pages via fullimg lists

### Maintenance
- Parsers 877230aa
- versionName 1.1.7 / versionCode 10107

## 1.1.6
Date: 2026-09-04

### Highlights
- Source error reports: separate toggle, failures go to Bugsink with source context
- Update dialog restyled (icon, accent install button)

### Fixes
- Usagi single-flight requests, MangaChan im. domain

### Maintenance
- Parsers 641e0b59
- versionName 1.1.6 / versionCode 10106

## 1.1.5
Date: 2026-09-04

### Fixes
- Anti-bot 404 stubs no longer disguised as login prompts (honest errors instead)

### Maintenance
- Parsers 5df05138
- versionName 1.1.5 / versionCode 10105

## 1.1.4
Date: 2026-09-04

### Highlights
- Own simple ad blocker (domain-based, third-party only, never breaks pages)
- Alternatives tab searches all known titles (RU/EN/KR)

### Fixes
- Browser: adblock toggle in menu, no filtering on Cloudflare checks
- RU sources: dropped dead BestManga and parked mirrors, refreshed working mirrors

### Maintenance
- Parsers 29ed80e1
- versionName 1.1.4 / versionCode 10104

## 1.1.3
Date: 2026-09-03

### Highlights
- In-app update: APK download with progress and direct install, signature check
- New launcher icon

### Fixes
- Usagi anti-bot: full browser headers, retry only transient codes

### Maintenance
- AdBlock: EasyList + EasyPrivacy + RU AdList
- Parsers b7bacfd6
- versionName 1.1.3 / versionCode 10103

## 1.1.2
Date: 2026-09-03

### Highlights
- In-app update: APK download with progress dialog and direct install, signature check
- New launcher icon

### Fixes
- Usagi anti-bot: retry only transient codes, no IP-hammering retries

### Maintenance
- Parsers 60aeb077
- versionName 1.1.2 / versionCode 10102

## 1.1.1
Date: 2026-09-03

### Highlights
- Usagi anti-bot bypass: modern Chrome user agent plus retries on protection stubs
- Recommendations update interval setting (hour / day / 3 days / week)
- Own changelog in About screen

### Fixes
- Fixed dead captcha-discard receiver (manifest action mismatch)
- Removed remaining Futon/Kotatsu leftovers: deep links, OAuth redirects, colors, internal names
- Database renamed to hanten-db with automatic migration from futon-db

### Maintenance
- Tag blacklist and sources badge served from our repos (DezFix/filters, DezFix/hanten-parsers)
- Telegram backup bot and foreign sync servers disabled (no own backend yet)
- README: RU/UK sources priority, up-to-date fingerprint and links
- versionName 1.1.1 / versionCode 10101

## 1.1.0
Date: 2026-09-03

### Highlights
- Own splash logo (Hanten happi) instead of Futon artwork
- Startup update check: dialog with changelog from GitHub releases, open APK or release page, per-version skip
- Parsers rebased onto Kotatsu-Redo base (Futon source set, incl. Senkuro) — about 80 sources added

### Fixes
- Fixed R8 duplicate class (`CSSBackground`) between parsers library and app
- Fixed encoding corruption in Russian strings

### Maintenance
- Single stable release keystore (validator, docs and CI secrets aligned)
- Russian translation: 60+ settings and menu strings
- versionName 1.1.0 / versionCode 10100

## 1.0.1
Date: 2026-09-02

### Highlights
- Switched to our parser library (`DezFix/hanten-parsers`)
- WebView request interception API for parsers
- Cloudflare interception and update-checking config keys per source

### Fixes
- Keep 2-arg `evaluateJs` delegating to 3-arg for internal parser callers

### Maintenance
- Automated release pipeline with signed APKs
