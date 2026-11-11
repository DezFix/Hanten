# План 2.6.7

Цель: два зеркальных режима (манга/новеллы), без регрессий чтения и офлайна.
Источник задач — `docs/CODE_REVIEW_2.6.6` (ревью ниже в чате) + запрос на меню
скачивания как у манги.

## 1. Скачивание новелл как у манги

- [x] `DetailsMenuProvider`: вместо отдельного AlertDialog — тот же
  `DownloadDialogFragment`, что у манги (`router.showDownloadDialog`).
- [x] `DownloadDialogFragment`/`DownloadDialogViewModel`: режим новеллы —
  те же макросы выбора глав (вся книга / ветка / первые N / непрочитанные),
  без формата и папки.
- [x] `NovelDownloadWorker` (WorkManager, `@HiltWorker`) — качает HTML глав и
  картинки в `NovelOfflineRepository`, отдаёт прогресс, отменяется.
- [x] `NovelDownloadsActivity` + ViewModel: активные загрузки сверху
  (прогресс, отмена), ниже — уже скачанные книги с главами.
- [x] Snackbar «Начато/Добавлено» ведёт на экран загрузок новелл, а не манги.

## 2. Источники новелл (P0 из ревью)

- [x] Убрать дубли имён: в `NovelParserSource` остаётся только `RANOBE_ME`.
- [x] Удалить мёртвые `RanobeLibRepository`, `RanobesRepository`,
  `BakaUaRepository`; ветки в `MangaRepository.Factory`.
- [x] Счётчики в `MangaSourcesRepository` (`allMangaSources` +
  `allNovelSources`) снова консистентны.
- [x] RanobeLib-текст: `RanobeLibContentParser` → `NovelContentParser` в
  `novel/data`, применяется в читалке и в загрузке. JSON больше не показывается.

## 3. Читалка (P1)

- [x] Прогресс при бесконечной прокрутке считается по текущей главе, а не по
  всему документу.
- [x] Троттлинг прогресса в постраничном режиме (сейчас запись в БД на каждый
  scroll-эвент).
- [x] Размер шрифта: `sp × density`, а не `px`.
- [x] EPUB: base URL = каталог главы, один zip на книгу, закрытие в `onCleared`.
- [x] Экранирование `title`/`language` в HTML-шаблоне.
- [x] PDF: процент `(page+1)/count`, incognito, закрытие прошлого рендерера и
  `Page`.
- [x] `importNovel`: санитайз имени файла, без тихой перезаписи.

## 4. Перед релизом

- [x] `:app:testDebugUnitTest`, `:app:lintDebug`.
- [x] Версия 2.6.7 / 20607, коммит, пуш, тег `v2.6.7`.

## Не входит

- P2 из ревью (закладки, лимит истории, сетевой конфиг, CI-воркфлоу).
- Перенос novel-парсеров в `hanten-parsers` (отдельный релиз парсеров).
