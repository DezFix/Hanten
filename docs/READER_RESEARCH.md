# Как читают книги: разбор открытых читалок и что взять для новелл-ридера Hanten

Дата: 2026-11-03. Лицензия проекта Hanten: **GPL-3.0**.

> Статус: рекомендации P0-1…P0-4 внедрены в 2.6.2 (геометрия paged, книжная
> типографика, якорная позиция, тапы/клавиши, WebView без zoom-out).

Источники — исходники (shallow clone в `%TEMP%\opencode\reader-research`, ревизии ниже), не статьи.
Сборки не запускались, код приложения не менялся.

| Проект | Ревизия на момент чтения |
|---|---|
| LegadoTeam/legado | `330e606`, 2026-11-01 |
| koreader/koreader | `3d40440`, 2026-11-03 |
| readium/kotlin-toolkit | `89b96c2`, 2026-10-16 |
| foobnix/LibreraReader | `40c5484`, 2026-11-02 |
| Anxcye/anx-reader | `0c7fc1a`, 2026-10-17 |
| johnfactotum/foliate-js | `78914ae`, 2026-05-01 |
| troyeguo/koodo-reader | `dbcb745`, 2026-11-01 |
| geometer/FBReaderJ | `e83aec9`, 2017-06-11 (мертв) |

---

## 1. Сводная таблица

| Приложение | Репозиторий | Лицензия | Стек | Активность | Что реально стоит взять |
|---|---|---|---|---|---|
| Legado / 阅读 | `github.com/LegadoTeam/legado` (продолжение `gedoor/legado`; в `gedoor/legado` остался только README-анонс) | GPL-3.0 | Kotlin/Java, свой движок на `Canvas` | Очень высокая, 8500+ коммитов | Модель позиции «символ в главе → страница», постраничная навигация с предзагрузкой соседних страниц, зоны тапов 3×3 с настраиваемыми действиями, режимы анимации, набор типографских настроек |
| KOReader | `github.com/koreader/koreader` (+ движок `koreader/crengine`) | AGPL-3.0 | Lua + C++ (crengine) | Очень высокая | Две зоны тапа + меню по центру, `page_positions` (страница → xpointer), разбиение на страницы по «строкам-слайсам» с `RN_SPLIT_AVOID` (widows/orphans), обоснование «книжной» вёрстки (epub.css) |
| Readium Kotlin toolkit | `github.com/readium/kotlin-toolkit` | BSD-3-Clause | Kotlin, WebView + CSS multi-column | Высокая, коммерческая поддержка | Готовая математика WebView-пагинатора: `round(scrollX/clientWidth)`, `scrollToItem(item*width)`, `progression = scrollX/contentWidth`; CSS-API `--USER__*`/`--RS__*`; `PaginationListener(pageIndex, totalPages, locator)` |
| foliate-js | `github.com/johnfactotum/foliate-js` | MIT | Чистый JS, `<iframe>` + CSS columns | Средняя, но используется в Foliate, Librera 10, Anx Reader | Формула ширины колонки и отступа, `page = floor((start+end)/2/size)`, прерываемая анимация `rAF`, переписывание `vw/vh` и `page-break-*` в CSS книги |
| Librera | `github.com/foobnix/LibreraReader` | GPL-3.0 | Java, MuPDF + свои View | Средняя (заморожена автором, но коммиты идут) | Режимы `BOOK / SCROLL / MUSICIAN` (автоскролл), `DefaultHyphenator` + `HyphenPattern` (TeX-шаблоны), настраиваемые зоны тапа |
| Anx Reader | `github.com/Anxcye/anx-reader` | MIT | Flutter + WebView + **foliate-js** | Высокая | Мост «JS → Flutter»: прогресс как `cfi + fraction + chapterPage/chapterTotal + bookPage/bookTotal` |
| Cool Reader / crengine | движок в `koreader/crengine` | GPL-2.0 | C++ | Поддерживается через KOReader | То же, что у KOReader: `lvtextfm.cpp` (переносы/выключка), `lvpagesplitter.cpp` (разбиение) |
| Koodo Reader | `github.com/koodo-reader/koodo-reader` | AGPL-3.0 | Electron/React, свой рендерер | Высокая | Идеи по UI; код брать нельзя (AGPL) |
| FBReaderJ | `github.com/geometer/FBReaderJ` | GPL-2.0 | Java, `ZLTextArea` | Мертв с 2017 | Только историческая модель «курсор по абзацам» |

Вывод по архитектуре: **Hanten уже на правильном стеке.** Два современных мультиплатформенных ридера (Librera 10 по release notes F-Droid и Anx Reader по коду) рендерят reflowable-книги в WebView через foliate-js. Переписывать на нативный движок не нужно — нужно починить геометрию, позицию и типографику.

---

## 2. Детальный разбор

### 2.1 Legado — самый близкий по задаче

**Где движок.** `app/src/main/java/io/legado/app/ui/book/read/page/`:

```
page/ReadView.kt                 — тапы, жесты, анимация, «upContent»
page/PageView.kt                 — отрисовка одной страницы
page/entities/TextChapter.kt     — глава = список страниц + позиции
page/entities/TextPage.kt        — страница = список строк
page/entities/TextLine.kt        — строка = список колонок-символов с x-координатами
page/provider/ChapterProvider.kt — размеры вьюпорта, паддинги, шрифты, line spacing
page/provider/TextChapterLayout.kt — перенос строк, выключка, висячая пунктуация
page/provider/TextPageFactory.kt — навигация по страницам и предзагрузка соседних
page/delegate/*PageDelegate.kt   — 5 режимов листания
page/AutoPager.kt                — автоскролл
```

**Как считается страница.** Никакого `column-count`. `TextChapterLayout` режет текст по символам, измеряя каждый `Paint.measureText`, и складывает `TextLine` в `TextPage`, пока суммарная высота не превысит `visibleHeight`:

- `ChapterProvider.upViewSize()`: `visibleWidth = viewWidth - paddingLeft - paddingRight`, `visibleHeight = viewHeight - paddingTop - paddingBottom`, где паддинги — из настроек (по умолчанию 16 dp по бокам, 6 dp сверху/снизу).
- Межстрочный интервал: `lineSpacingExtra = ReadBookConfig.lineSpacingExtra / 10f` (по умолчанию 12 → коэффициент 1.2), который умножается на высоту строки в `lineSpacingFor(line)`. То есть это множитель, а не приращение в пикселях.
- Абзац: `paragraphIndent = "　　"` (два идеографических пробела), `paragraphSpacing = 2` dp, `textFullJustify = true`.
- Двухстраничный режим: `doublePage` включается по `viewWidth > viewHeight` (или на планшете) и только не в scroll-режиме; тогда `visibleWidth = viewWidth/2 - padding`.
- Дебаунс поворота: `upViewSize()` при изменении только высоты ждёт **300 мс** перед пересчётом (`handler.postDelayed(300)`), чтобы не перелицовывать книгу на каждом кадре поворота.

**Как выглядит «книга».** Выключка делается не «растянуть пробелы как получится», а распределением остатка по словам/межбуквенным интервалам с расчётом стартовой позиции строки (`addCharsToLineFirst` → `LineColumnLayout.justifiedFirst`, `applyJustify` пишет `textLine.wordSpacing` или `extraLetterSpacing`). Есть «висячая пунктуация» (`hangingPunctuation`, `hangingPunctuationWidth`) — запятая/точка выносится за поле, чтобы правое поле не рвалось.

**Позиция чтения.** Каноническая позиция — не процент, а **смещение в символах внутри главы**: `ReadBook.durChapterIndex` + `ReadBook.durChapterPos`. Страница вычисляется из позиции бинарным поиском:

```kotlin
// TextChapter.kt
fun getPageIndexByCharIndex(charIndex: Int): Int {
    val bIndex = pages.fastBinarySearchBy(charIndex, 0, pageSize) { it.chapterPosition }
    ...
}
```

При смене шрифта/размера окна/ориентации позиция остаётся той же — поэтому нет прыжка. Прогресс в UI считается как `page/total` уже после вёрстки.

**Листание.** `TextPageFactory` умеет отдавать не только `curPage`, но `nextPage`, `prevPage` и `nextPlusPage` (страница +2) — анимации нужны соседние страницы заранее, иначе на границе главы виден рывок. Переход главы — не перезагрузка вьюхи, а `ReadBook.moveToNextChapter(upContent = true)` с уже свёрстанной соседней главой (`nextTextChapter`/`prevTextChapter`).

`ScrollPageDelegate.calcNextPageOffset()` — важная деталь для scroll-режима: «следующая страница» прокручивает не на высоту экрана, а на **верх последней видимой строки**, чтобы не разрезать строку пополам:

```kotlin
val lastLineTop = visiblePage.lines.last().lineTop.toInt()
return -(lastLineTop - ChapterProvider.paddingTop)
```

**Тапы.** `ReadView.onSingleTapUp()` разбирает 3×3 сетку прямоугольников (`tlRect`, `tcRect`, `trRect`, `mlRect`, `mcRect`, …), каждому сопоставлено действие из настроек (`AppConfig.clickActionTL` и т. д.). Действия: `0` — меню, `1` — следующая страница, `2` — предыдущая, `3/4` — главы, `7` — закладка, и т. д. **Дефолты** (`AppConfig.kt:57-65`): левый столбец и верх — назад, правый столбец и низ — вперёд, центр — меню. То есть ровно та простая модель, которую просит пользователь, но с правом переназначить.

**Клавиши.** `ReadBookActivity.onKeyDown`: `isPrevKey/isNextKey` (включая PageUp/PageDown/Space) → страница; `KEYCODE_VOLUME_UP/DOWN` → страница при `AppConfig.volumeKeyPage`, с дебаунсом и поддержкой long-press. В scroll-режиме те же события двигают на страницу, а не на главу.

**Фуллскрин.** `BaseReadBookActivity.upSystemUiVisibilityO()`: `IMMERSIVE or IMMERSIVE_STICKY or LAYOUT_FULLSCREEN`, отдельно `hideStatusBar` и `hideNavigationBar`, а `toolBarHide` управляет только панелью меню. Ключевое: скрытие системных баров и скрытие UI — **разные флаги**, но оба в одном месте.

**Автоскролл.** `AutoPager`: `scrollOffsetRemain += height / readTime * elapsed`, дробный остаток переносится на следующий кадр (нет накопления ошибки округления). В постраничном режиме вместо листания рисуется «шторка» — следующий экран открывается построчно, без анимации перелистывания.

### 2.2 KOReader + crengine — эталон вёрстки

**Где движок.** Вёрстка — C++ crengine (`koreader/crengine`), UI — Lua:

- `frontend/apps/reader/modules/readerview.lua` — вьюпорт, `getTapZones()`, `recalculate()`;
- `frontend/apps/reader/modules/readerpaging.lua` — постраничный режим, `page_positions`, `setupTouchZones`;
- `frontend/apps/reader/modules/readerrolling.lua` — прокрутка;
- `frontend/ui/widget/textboxwidget.lua` — перенос строк и выключка;
- `crengine/src/lvtextfm.cpp`, `crengine/src/lvpagesplitter.cpp`, `crengine/src/hyphman.cpp`.

**Разбиение на страницы.** `lvpagesplitter.cpp` работает с «линиями-слайсами»: одна линия — строка текста, картинка, или вертикальный отступ блока. Линии помечаются `RN_SPLIT_AVOID` (из CSS `widows`, `orphans`, `break-inside: avoid`, обёртки вокруг обтекаемых объектов), и группы «не разрывать» переносятся на следующую страницу целиком:

> «Some of these lines can be flagged to indicate they should stick together: … lines of text when CSS widows/orphans, lines of text surrounded by floats …» — `lvpagesplitter.cpp`

Это тот же результат, который в CSS-колонках даёт `break-inside: avoid` + `widows/orphans`, — и его надо просто включить в шаблоне.

**Выключка.** `textboxwidget.lua`: остаток ширины делится на число «расширяемых» глифов (пробелов), остаток распределяется по одному пикселю, чтобы не было субпиксельного размытия:

```lua
local space_to_fill = line.targeted_width - xshaping.width
space_add_w = math.floor(space_to_fill / xshaping.nb_can_extend)
space_add1_nb = space_to_fill - space_add_w * xshaping.nb_can_extend
```

Плюс `height_adjust` (высота виджета округляется до кратного `line_height`) — иначе последняя строка страницы «пляшет» на пиксель.

**Позиция.** `ReaderPaging.page_positions` — словарь «номер страницы → xpointer (DOM-позиция)». При reflow страницы пересчитываются, а позиция восстанавливается по xpointer, а не по номеру. Процент — производная величина.

**Тапы.** `ReaderView.getTapZones()` отдаёт **ровно две** зоны: `forward` и `backward`, с пресетами `left_right`, `top_bottom`, `bottom_top` и поддержкой RTL. Всё остальное (меню, TOC, настройки) — через центр/жесты, а не через дополнительные зоны.

### 2.3 Readium Kotlin toolkit — та же архитектура, что у Hanten

**Где движок.** `readium/navigator/src/main/java/org/readium/r2/navigator/`:

- `R2WebView.kt` — WebView, который сам считает страницы;
- `R2BasicWebView.kt` — `progression`;
- `epub/EpubNavigatorFragment.kt` — листание и `PaginationListener`;
- `epub/EpubSettings.kt` + `readium/navigators/web/reflowable/.../css/ReadiumCssProperties.kt` — настройки → CSS-переменные;
- `readium/navigator/src/main/assets/readium/readium-css/*.css` — базовые стили.

**Математика страницы** (`R2WebView.kt`) — ровно то, чего не хватает Hanten:

```kotlin
// текущая страница — округление, а не деление
mCurItem = (scrollX.toDouble() / clientWidth.toDouble()).roundToInt()

// переход: destX кратен ширине клиента
val destX = width * item
if (smoothScroll) smoothScrollTo(destX, 0, velocity) else scrollTo(destX, 0)
```

`clientWidth` берётся из `computeHorizontalScrollExtent()` с комментарием, что ширина контента **не всегда кратна** ширине вьюпорта, поэтому округление обязательно. Прогресс: `progression = scrollX / contentWidth` (в постраничном режиме — по всему ресурсу, не по номеру страницы).

**Листание.** `EpubNavigatorFragment.goForward()`: если внутри ресурса есть куда листать — `webView.scrollRight(animated)`, иначе `resourcePager.setCurrentItem(currentItem + 1, animated)`. Разные механизмы для «страница» и «глава», но один API для вызывающего.

**События.** `PaginationListener.onPageChanged(pageIndex, totalPages, locator)` — UI получает и номер страницы (для «12 / 37»), и `locator` (для восстановления позиции). Это ровно тот компромисс, который нужен: проценты/страницы для интерфейса, locator — для правды.

**CSS-слой.** Настройки не пишутся в HTML, а транслируются в переменные: `--USER__colCount`, `--USER__lineLength`, `--USER__paraIndent`, `--USER__paraSpacing`, `--USER__bodyHyphens`, `--RS__pageGutter`. База (`ReadiumCSS-before.css`) уже содержит книжное:

```css
body { widows: 2; orphans: 2 }
dt, figure, h1, h2, h3, h4, h5, h6, tr { break-inside: avoid }
caption, dt, h2..h6, hr { break-after: avoid }
```

### 2.4 foliate-js — референс для WebView-пагинатора

`paginator.js`. Ключевые формулы (комментарии в коде сохранены):

```js
// ширина колонки и число колонок
const divisor = Math.min(maxColumnCount, Math.ceil(size / maxInlineSize))
const columnWidth = (size / divisor) - gap

// компенсация: внешний отступ должен визуально совпадать с межколоночным
const gap = -g / (g - 1) * size   // g — желаемый gap в долях ширины

// номер страницы — по центру вьюпорта, а не по левому краю
get page() { return Math.floor(((this.start + this.end) / 2) / this.size) }
get pages() { return Math.round(this.viewSize / this.size) }
```

Листание — своя анимация вместо `scroll-behavior: smooth`:

```js
#scrollTo(offset, reason, smooth) {
  if ((reason === 'snap' || smooth) && this.hasAttribute('animated'))
    return animate(el[scrollProp], offset, 300, easeOutQuad, x => el[scrollProp] = x)
      .then(() => { this.#scrollBounds = [...]; this.#afterScroll(reason) })
  el[scrollProp] = offset; this.#afterScroll(reason)
}
```

и защита от «двойного листания» на границе главы:

```js
async #turnPage(dir, distance) {
  if (this.#locked) return
  this.#locked = true
  const shouldGo = await (prev ? this.#scrollPrev(distance) : this.#scrollNext(distance))
  if (shouldGo) await this.#goTo({ index: this.#adjacentIndex(dir), anchor: prev ? () => 1 : () => 0 })
  if (shouldGo || !this.hasAttribute('animated')) await wait(100)
  this.#locked = false
}
```

Две детали, которые прямо применимы к EPUB в Hanten:

1. `columnize()` ставит `column-fill: auto`, фиксированную высоту, `overflow: hidden`, `overflow-wrap: break-word` и сбрасывает `position/max-height/min-width` — иначе CSS книги ломает колонки.
2. `open(book)` переписывает CSS книги: `-epub-` → без префикса, `vw/vh` → px, `page-break-*` → `-webkit-column-break-*`, `break-*: page` → `break-*: column`. Без этого `100vh` в стилях издателя даёт колонку высотой в экран и «прыжок» на каждой странице.

**Кто это использует.** Anx Reader: `assets/foliate-js/src/paginator.js` (свой форк `book.js`), мост `window.flutter_inappwebview.callHandler('onRelocated', {...})` с полями `cfi, fraction, chapterCurrentPage, chapterTotalPages, bookCurrentPage, bookTotalPages`. Librera 10 по release notes F-Droid тоже перешла на WebView + foliate-js; **в публичном master `libReflow` на момент проверки нет**, так что это утверждение — из release notes, не из кода.

### 2.5 Остальные

- **Librera** (`DocumentController.java`): `READING_MODE_BOOK`, `READING_MODE_SCROLL`, `READING_MODE_MUSICIAN` (автоскролл с регулируемой скоростью), `DefaultHyphenator` + `HyphenPattern` (TeX-шаблоны переносов на Java), `TapZoneDialog` для настройки зон.
- **FBReaderJ**: `ZLTextArea` + `ZLTextParagraphCursor`, позиция = (абзац, слово, символ). Модель хорошая, код 2017 года и GPL-2.0 — только идеи.
- **Koodo Reader**: Electron/React, AGPL-3.0, свой рендерер. Для Android-WebView ничего нового.

---

## 3. Диагноз Hanten (по коду, с точками)

Ниже — не «мне кажется», а конкретные места. Это и объясняет четыре претензии пользователя.

| # | Симптом | Причина в коде | Файл:строка |
|---|---|---|---|
| 1 | Текст «как стихи», не как книга | У `p` нет `text-indent`, есть `margin-bottom: 1em`, выключка по умолчанию `left`, нет `hyphens`, нет `widows/orphans`. Короткие абзацы источников выглядят как строфы | `NovelHtmlTemplate.kt:84-89`, `NovelReaderSettings.kt:23,21` |
| 2 | Страницы «уезжают», при листании прыжок | Колонка шириной `100vw`, но `#content` имеет `padding: 0 var(--reader-page-margin)`, значит фактическая ширина колонки = `100vw − 2·margin`. Листание и расчёт страниц идут шагом `window.innerWidth` → дрейф `2·margin` за страницу, ошибка накапливается | `NovelHtmlTemplate.kt:37,42`, `:204,207`, `:151-152,272` |
| 3 | Двойная анимация, рывок | `scroll-snap-type: x mandatory` + `scrollBy({behavior:'smooth'})`: браузер сначала плавно скроллит, потом снапит — два разных движения | `NovelHtmlTemplate.kt:33`, `:204` |
| 4 | В paged-режиме тап вправо на последней странице ничего не делает | В постраничной ветке обработчика тапа нет вызова `requestNextChapter`/`requestPreviousChapter` — только `scrollBy` | `NovelHtmlTemplate.kt:197-211` |
| 5 | Текст не заполняет экран / мелкий | `loadWithOverviewMode = true` + `useWideViewPort = true`: при любом переполнении по ширине WebView отдаляет страницу (zoom-out). Плюс видимый вертикальный скроллбар | `NovelReaderActivity.kt:160-163`, `activity_novel_reader.xml:15` |
| 6 | Прыжок при показе/скрытии панелей, смене шрифта, повороте | После изменения геометрии позиция восстанавливается процентом (`restoreProgress(percent)`), а процент — от высоты документа; после reflow это другое место | `NovelReaderActivity.kt:268-272`, `:607-618` |
| 7 | Смена главы = рывок и мигание | Каждая глава — полная перезагрузка документа `loadDataWithBaseURL`, затем `setTimeout(restoreProgress, 60)` | `NovelReaderActivity.kt:445`, `NovelHtmlTemplate.kt:422-428` |
| 8 | Прогресс «сбрасывается» в бесконечной прокрутке | При дописывании главы `currentChapterIndex` меняется и вызывается `saveReadingProgress(0f)`; при этом процент считается по всему документу (все дописанные главы) | `NovelReaderViewModel.kt:300-320,421-427`, `NovelHtmlTemplate.kt:158-161` |
| 9 | Закладка не туда после смены шрифта | Закладка хранит индекс абзаца, но восстановление после reflow делает `restoreProgress(percent)` вместо `scrollToParagraph` | `NovelHtmlTemplate.kt:342-352` |
| 10 | Громкость меняет главу даже в paged | `onKeyDown` всегда `switchChapter(delta)`, режим не учитывается | `NovelReaderActivity.kt:640-656` |
| 11 | Фуллскрин непредсказуем | `applySystemBarsVisibility()` меняет только системные бары, но не `isUiVisible`; `onNovelSettingsChanged` не вызывает `updateReaderSafeArea()` → safe-area остаётся старой | `NovelReaderActivity.kt:546-555`, `:590-621` |
| 12 | Картинки EPUB не грузятся | `baseUrl` = `https://futon.reader/`, а `chapter.url` = путь вида `OEBPS/text/ch1.xhtml`; относительный `src="images/1.jpg"` резолвится в корень, а не в каталог главы | `NovelReaderActivity.kt:441-445` |
| 13 | EPUB без стилей издателя | В `#content` кладётся **сырой XHTML** (`<html><head>…<body>`), `head` и `body` отбрасываются, CSS книги не подключается, `lang` отсутствует → `hyphens` не сработает | `NovelHtmlTemplate.kt:117`, `EpubParser.kt:140-151` |
| 14 | Картинка разрезается границей страницы | Нет `break-inside: avoid` для `img/figure/table/pre`, нет `max-height` | `NovelHtmlTemplate.kt:95-101` |

Отдельно: `NovelReaderBridge.onScrollProgress(percent: Float)` — параметр `Float` в JS-мосту. Работает не на всех прошивках; безопаснее `Double`/`String`. Не блокер, но лучше не полагаться.

---

## 4. Рекомендации

### 4.1 Быстрые победы (только WebView-слой, без смены архитектуры)

**(а) Книжная типографика.** Правки в `NovelHtmlTemplate.kt` (блок `<style>`) и `NovelReaderSettings.toCssVariables()`:

```css
body {
  margin: 0;
  text-rendering: optimizeLegibility;
  font-kerning: normal;
  font-variant-numeric: oldstyle-nums proportional-nums;
  overflow-wrap: break-word;
  hyphens: var(--reader-hyphens);          /* auto | manual */
  -webkit-hyphens: var(--reader-hyphens);
  widows: 2; orphans: 2;
}
p {
  margin: 0;                                /* не 1em: книжный абзац без отбивки */
  text-indent: var(--reader-indent);        /* 1.25em по умолчанию */
  text-align: justify;                       /* дефолт вместо left */
  line-height: var(--reader-line-height);
}
h1 + p, h2 + p, h3 + p, h4 + p,
blockquote p, li p, figcaption p, .no-indent { text-indent: 0; }
h1, h2, h3, h4, h5, h6 {
  text-align: left; text-indent: 0;
  break-after: avoid; -webkit-column-break-after: avoid;
  margin: 1.2em 0 .6em;
}
img, figure, table, pre, blockquote, svg {
  break-inside: avoid; -webkit-column-break-inside: avoid;
  page-break-inside: avoid;
}
img { max-width: 100%; max-height: 90vh; height: auto; object-fit: contain; }
```

Обязательно: `<html lang="ru">` (или язык книги) — иначе `hyphens: auto` не включится. Язык брать из `manga`/`Locale`; для EPUB — из OPF `dc:language`.

Почему так, а не «на глаз»: KOReader в меню прямо описывает разницу двух стилей — `epub.css` («justified text and indentation … more like a paper book than like a web page») и `html5.css` («left aligned paragraphs without indentation and with spacing between them … may give them the look of a web page»). Сейчас Hanten использует второе. Пользователь просит первое.

**(б) Починить геометрию постраничного режима.** Единственный источник правды — ширина страницы, посчитанная один раз:

```css
html, body { height: 100%; margin: 0; overflow: hidden; }
#reader-container {
  position: fixed; inset: 0;
  overflow-x: auto; overflow-y: hidden;
  overscroll-behavior: contain;              /* без pull-to-refresh и glow */
}
#reader-container::-webkit-scrollbar { display: none; }

#content {
  box-sizing: border-box;
  height: calc(100% - var(--reader-safe-top) - var(--reader-safe-bottom));
  margin-top: var(--reader-safe-top);
  padding: 0 calc(var(--page-gap) / 2);       /* поле страницы = половина gap */
  column-width: var(--column-width);          /* ставится из JS */
  column-gap: var(--page-gap);
  column-fill: auto;
  overflow: hidden;
}
```

```js
// один раз на ресайз/смену настроек
function relayout() {
  const c = document.getElementById('reader-container');
  const margin = pageMarginPx();                  // --reader-page-margin
  const gap = 2 * margin;                         // поле слева + справа = margin
  const size = c.clientWidth;                     // ширина страницы = ширина вьюпорта
  const maxInline = 34 * fontSizePx();            // максимум ~34em на колонку
  const divisor = Math.max(1, Math.min(2, Math.ceil(size / maxInline)));
  content.style.setProperty('--page-gap', gap + 'px');
  content.style.setProperty('--column-width', (size / divisor - gap) + 'px');
  pageStep = size;                                // шаг = ровно ширина вьюпорта
}
function goToPage(n) { animateScroll(container, 'scrollLeft', n * pageStep); }
function currentPage() {
  return Math.floor((container.scrollLeft + container.clientWidth / 2) / pageStep);
}
```

Арифметика, которая обязана сходиться (иначе дрейф):
- content-box = `size − gap` (padding `gap/2` с каждой стороны при `box-sizing: border-box`);
- ширина колонки = `size / divisor − gap`; для `divisor = 1` это `size − gap` — ровно content-box, для `divisor = 2` две колонки плюс `gap` дают тот же `size − gap`;
- шаг = `column-width + gap` = **`size`**.

Три правила, которые нельзя нарушать (все — из Readium/foliate):
* шаг прокрутки = ширина страницы, а не «примерно экран»;
* номер страницы — `floor((start + end)/2 / size)`, а не `scrollLeft / innerWidth`;
* `column-width` + `column-gap` = ширина страницы (гарантия, что колонка не «шире экрана»).

Отдельно: `padding` на самом multicol-контейнере (`#content`) — это **не** поля страницы: он сжимает content-box и ломает равенство шага. Поля даёт только `gap/2` снаружи колонок, как выше. Именно эта ошибка сейчас и даёт дрейф `2·pageMargin` на страницу.

**(в) Своя анимация листания вместо `behavior:'smooth'` + snap.** 180–250 мс, `easeOutCubic`, отменяемая на `touchstart`:

```js
let animId = 0;
function animateScroll(el, prop, to, ms = 200) {
  cancelAnimationFrame(animId);
  const from = el[prop], t0 = performance.now();
  const ease = t => 1 - Math.pow(1 - t, 3);
  const step = now => {
    const p = Math.min(1, (now - t0) / ms);
    el[prop] = from + (to - from) * ease(p);
    if (p < 1) animId = requestAnimationFrame(step);
  };
  animId = requestAnimationFrame(step);
}
container.addEventListener('touchstart', () => cancelAnimationFrame(animId), { passive: true });
```

**(г) Границы глав в paged.** На последней странице тап вправо должен вызывать `AndroidNovelBridge.requestNextChapter()`; на первой — `requestPreviousChapter()`. Иначе получаются «мёртвые» тапы (сейчас именно так). Перед вызовом — проверить `atEnd`/`atStart`:

```js
const atEnd   = () => container.scrollLeft >= container.scrollWidth - container.clientWidth - 2;
const atStart = () => container.scrollLeft <= 2;
```

**(д) Единый тап-обработчик по `pointerup`, а не `click`.** `click` в WebView приходит с задержкой и «съедается» выделением текста. Правильно: `pointerdown` запоминает точку, `pointerup` считает смещение; если смещение > `8 px` — это скролл/свайп, тап не срабатывает. Так свайп в scroll-режиме перестанет случайно переключать главу.

**(е) WebView-настройки.** В `setupWebView()`:
```kotlin
useWideViewPort = false          // без авто-zoom-out
loadWithOverviewMode = false
textZoom = 100                   // игнорировать системный масштаб шрифта
isVerticalScrollBarEnabled = false
isHorizontalScrollBarEnabled = false
overScrollMode = View.OVER_SCROLL_NEVER
```
и убрать `android:scrollbars="vertical"` из `activity_novel_reader.xml`.

**(ж) EPUB: база и стили.** `baseUrl` = `https://futon.reader/` + каталог главы (`chapter.url.substringBeforeLast('/') + '/'`). Плюс вырезать из XHTML `<head>`, `<?xml?>`, `<body>` и вставлять только содержимое `body` (или, лучше, собрать `srcDoc` с `<style>` издателя, прогнанным через замены foliate: `vw/vh → px`, `page-break-* → -webkit-column-break-*`).

### 4.2 Средние изменения

**(1) Позиция — якорь, а не процент.** Хранить `{chapterId, blockIndex, blockFraction, textOffset}`. `textOffset` — смещение в тексте главы (как `durChapterPos` в Legado), `blockIndex` — для обратной совместимости с текущими закладками.

```js
function getAnchor() {
  const blocks = getBlockElements();                      // p, h1..h6, blockquote, figure
  const y = isPaged ? container.scrollLeft : scrollY;
  // последний блок, начинающийся не позже y
  let lo = 0, hi = blocks.length - 1, idx = 0;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const top = blockTop(blocks[mid]);                    // в координатах контента
    if (top <= y + 1) { idx = mid; lo = mid + 1; } else hi = mid - 1;
  }
  const b = blocks[idx], top = blockTop(b), h = blockSize(b);
  return { block: idx, frac: h > 0 ? Math.min(1, Math.max(0, (y - top) / h)) : 0 };
}
```

Восстановление — тем же якорем **после** reflow и **после** изменения safe-area. Процент остаётся только для UI и для истории (обратная совместимость). Это снимает пункты 6, 7, 9, 11 из диагноза.

**(2) Дописывание следующей главы вместо перезагрузки — в обоих режимах.** Сейчас `appendChapter()` работает только при прокрутке. Нужно:
* `#content` делится на секции `<section class="chapter" data-chapter-id>`; между ними `break-before: column` в paged и разделитель в scroll;
* `NovelReaderViewModel` префетчит N+1 и N−1 и отдаёт их в JS как «секции»;
* при пересечении границы JS сам переключает `currentChapter` (событие в мост), без `loadDataWithBaseURL`;
* хранить не больше 3 секций (текущая ±1), лишние удалять — иначе `scrollHeight` и память растут неограниченно.

Это то, что делает Legado (`nextTextChapter`/`prevTextChapter` + `nextPlusPage`) и Readium (ViewPager по ресурсам).

**(3) Модель прогресса.** `page` в UI = страница внутри главы; `total` = страниц в главе после вёрстки; общий прогресс книги = `chapterIndex + chapterFraction` (как `location.current/total` в Readium/Anx). Слайдер — по книге, счётчик «12 / 37» — по главе (сейчас в `textProgress` смешаны оба).

**(4) Клавиши.** paged → страница; scroll → страница (прокрутка на высоту экрана) с long-press → глава; volume и PageUp/PageDown/Space. В настройках — «инвертировать». Как в Legado `handleKeyPage`/`volumeKeyPage`.

**(5) Фуллскрин = один переключатель.** `isUiVisible` и `readerSettings.fullscreen` свести в одно состояние на старте: fullscreen ON → бары скрыты и панели скрыты; тап по центру показывает и то и другое; повторный тап — скрывает (и снова прячет бары). `updateReaderSafeArea()` вызывать из `onNovelSettingsChanged` и после `onApplyWindowInsets`.

**(6) Настройки, которых не хватает** (`NovelReaderSettings` + `NovelReaderConfigSheet` + `sheet_novel_reader_config.xml`): `textIndentEm` (0…3), `hyphens` (auto/off), `maxLineLengthEm` (0 = вся ширина, 30…45), `pageMarginDp` (уже есть в модели, но нет в UI), `paragraphSpacingEm` (0…0.5), `pageTurnAnimation` (none/slide/fade), `volumeKeysAction` (page/chapter/off).

### 4.3 Что НЕ стоит делать

| Идея | Почему нет |
|---|---|
| Портировать текстовый движок Legado (`TextLine`/`TextChapter`/`TextChapterLayout` на Canvas) | 5–6 тысяч строк и годы отладки ради того, что Chromium уже делает в CSS-колонках. Плюс это ломает EPUB-картинки, таблицы, сноски |
| Встроить Readium toolkit целиком | Тянет `readium-shared`/`streamer`, свой `ViewPager` на каждый ресурс, свою модель публикации; Hanten работает с «MangaPage.url = HTML», а не с EPUB-манифестом. Легче перенести три формулы |
| Перейти на foliate-js как библиотеку | Это `<iframe>`-архитектура на каждый раздел + EPUB CFI; наш контент — уже готовый HTML от парсеров. Формулы и идеи — да, рантайм — нет |
| NDK-движок (crengine) | +10–20 МБ, сборка под 4 ABI, GPL-2.0/AGPL-соседство, и всё это ради переносов, которые CSS делает сам |
| Анимация «перелистывание страницы» (curl) в WebView | Дорого по кадрам, ломается на длинных страницах и при смене шрифта; slide/fade на `transform` даёт 95 % ощущения за 5 % работы |
| Отдельный WebView на главу | Дублирует память, синхронизация скролла и позиции сложнее, чем один документ с секциями |

---

## 5. План 2.7 (приоритеты, файлы, приёмка)

Порядок — по соотношению «эффект / риск». P0 закрывает четыре претензии пользователя.

### P0-1. Геометрия и листание в paged-режиме
**Файлы:** `novel/ui/reader/NovelHtmlTemplate.kt` (CSS-блок `pagedStyle`, функции `notifyScroll`, `scrollToPercent`, `restoreProgress`, обработчик тапа), `novel/ui/reader/NovelReaderSettings.kt` (`toCssVariables`: добавить `--reader-indent`, `--reader-hyphens`, `--page-gap`).
**Что:** убрать `scroll-snap-type`; ввести `--column-width`/`--page-gap` из JS (см. 4.1б); шаг листания = `pageStep`; номер страницы по центру вьюпорта; `column-fill: auto`; `overscroll-behavior: contain`.
**Приёмка:**
1. 20 листаний вперёд и 20 назад: граница колонки совпадает с границей экрана (проверка по скриншоту с линейкой), текст не «съезжает» и не обрезается.
2. Счётчик страниц не меняется, пока не изменились настройки/размер окна.
3. Тап вправо на последней странице открывает следующую главу с 1-й страницы; тап влево на первой — предыдущую с последней; «мёртвых» тапов нет.
4. Быстрые двойные тапы не дают двойного перелистывания (флаг `locked`).

### P0-2. Книжная типографика по умолчанию
**Файлы:** `NovelHtmlTemplate.kt` (стили `p`, `h1..h6`, `img`, `body`), `NovelReaderSettings.kt` (дефолты: `textAlignment = JUSTIFY`, `paragraphMarginEm = 0f`, новый `textIndentEm = 1.25f`, `hyphens = true`), `NovelReaderConfigSheet.kt` + `sheet_novel_reader_config.xml` + `values/strings.xml` (слайдер «Абзацный отступ», переключатель «Переносы»), `NovelReaderActivity.kt` (передавать `lang` в шаблон).
**Приёмка:**
1. Роман с диалогами (RanobeLib) и EPUB выглядят как книжная страница: красная строка, выключка по обоим краям, без пустых строк между абзацами.
2. Первый абзац после заголовка — без отступа.
3. При `hyphens=off` текст остаётся выключенным, но без переносов; при `on` в русском тексте появляются переносы по слогам.
4. Заголовки не по центру (кроме титула главы, если он отдельным блоком).

### P0-3. Якорная позиция и восстановление
**Файлы:** `NovelHtmlTemplate.kt` (`getAnchor`/`setAnchor`, заменить `restoreProgress` в местах восстановления), `NovelReaderActivity.kt` (`updateReaderSafeArea`, `onNovelSettingsChanged`, `applyPendingBookmarkRestore`), `NovelReaderViewModel.kt` (`saveReadingProgress` — сохранять `scroll` = индекс блока + `percent` внутри блока), `bookmarks/domain/Bookmark.kt` (поле `percent` уже есть — трактовать как долю внутри абзаца).
**Приёмка:**
1. Поворот экрана: абзац, который был вверху, остаётся вверху (±1 строка).
2. Смена размера шрифта на +4: позиция сохраняется.
3. Показ/скрытие панелей: текст не «прыгает».
4. Открытие закладки после смены шрифта попадает в тот же абзац.

### P0-4. Тапы и клавиши
**Файлы:** `NovelHtmlTemplate.kt` (pointer-обработчик вместо `click`; зоны 25/50/25 в scroll, 30/40/30 в paged), `NovelReaderActivity.kt` (`onKeyDown`: страница в paged, глава в scroll, long-press), `NovelReaderBridge.kt` (`onScrollProgress(Double)`).
**Приёмка:**
1. Свайп в scroll-режиме не переключает главу.
2. Тап по центру — панели; лево/право — ровно то, что заявлено в режиме.
3. Громкость в paged листает страницы, в scroll — главы; в настройках можно поменять.
4. Долгое удержание громкости не пролистывает 10 страниц подряд.

### P1-5. Бесшовные переходы глав (append в обоих режимах)
**Файлы:** `NovelHtmlTemplate.kt` (`appendChapter` + секции + `break-before: column`, удаление дальней секции), `NovelReaderViewModel.kt` (`loadNextChapterForInfiniteScroll` → общий `prefetchAdjacent`, не менять `currentChapterIndex` до фактического пересечения границы), `NovelReaderActivity.kt` (событие «граница пересечена»).
**Приёмка:** переход главы в paged и scroll без белого экрана, без перезагрузки WebView, без потери позиции; в истории не появляется «0 % следующей главы», пока пользователь реально не вошёл в неё.

### P1-6. Прогресс и слайдер
**Файлы:** `NovelReaderViewModel.kt`, `NovelReaderActivity.kt` (`textProgress`, `sliderProgress`), `NovelHtmlTemplate.kt` (`onProgress` с `{chapterPage, chapterTotal, bookFraction}`).
**Приёмка:** счётчик «12 / 37» — по главе; слайдер — по книге; при дописывании главы слайдер не дёргается; после перезапуска позиция та же.

### P1-7. Фуллскрин
**Файлы:** `NovelReaderActivity.kt` (`toggleUiVisibility`, `applySystemBarsVisibility`, `onNovelSettingsChanged`, `onApplyWindowInsets`), `NovelReaderConfigSheet.kt`.
**Приёмка:** 6 сценариев (вкл/выкл настройки, тап по центру, поворот, возврат из фона, открытие шторки глав, сплит-скрин) — бары и панели в предсказуемом состоянии, safe-area пересчитана, текст под панели не заезжает.

### P2-8. Анимация листания и автоскролл в paged
**Файлы:** `NovelHtmlTemplate.kt`, `NovelReaderSettings.kt` (поле `pageTurnAnimation`), `NovelReaderConfigSheet.kt`.
**Что:** slide/fade на 180–220 мс (см. 4.1в). Автоскролл в paged — по идее `AutoPager`: «шторка», открывающая следующий экран построчно, вместо запрета («novel_auto_scroll_paged»).
**Приёмка:** 60 fps на среднем устройстве (нет `jank` в `gfxinfo`), анимацию можно прервать касанием, при выключенной анимации листание мгновенное.

### P2-9. EPUB: стили, картинки, сноски
**Файлы:** `EpubParser.kt` (отдавать `body` + собранный CSS + `dc:language`), `NovelReaderActivity.kt` (`baseUrl` с каталогом главы), `NovelHtmlTemplate.kt` (замены `vw/vh`, `page-break-*`).
**Приёмка:** EPUB с картинками показывает их; EPUB с CSS издателя не ломает колонки; сноска открывается и возвращает назад.

---

## 6. Лицензии: что можно копировать

Hanten — **GPL-3.0**. Это определяет всё.

| Источник | Лицензия | Копировать код в Hanten | Что делать |
|---|---|---|---|
| Legado | GPL-3.0 | **Да**, с сохранением авторства и GPL-3.0 | В шапке файла/в `NOTICE` указать `Copyright … Legado contributors, GPL-3.0`. Файл остаётся GPL-3.0 — совместимо |
| Librera | GPL-3.0 | **Да**, аналогично | Только для Java-фрагментов (например, `DefaultHyphenator`), но он и не нужен при CSS-переносах |
| KOReader, Koodo | **AGPL-3.0** | **Нет** | AGPL при объединении требует лицензировать всю программу как AGPL (в т. ч. сетевую оговорку §13). Для GPL-3.0-проекта это смена лицензии — недопустимо. Берём **только идеи и алгоритмы**, переписываем самостоятельно |
| crengine (движок KOReader / Cool Reader) | GPL-2.0 | **Нет** | GPL-2.0-only несовместима с GPL-3.0 при копировании кода. Только идеи (`lvpagesplitter.cpp` как схема) |
| Readium toolkit, Readium CSS | BSD-3-Clause | **Да** | Сохранить copyright notice и текст лицензии в `NOTICE`/шапке. Совместимо с GPL-3.0 |
| foliate-js | MIT | **Да** | Сохранить copyright + текст MIT. Формулы пагинатора можно переносить почти дословно |
| Anx Reader | MIT | **Да** | Только JS-мост и модель прогресса; Flutter-часть не применима |
| FBReaderJ | GPL-2.0 (по данным проекта; файла LICENSE в клоне нет) | Осторожно | GPL-2.0 **несовместима** с GPL-3.0 при копировании кода. Только идеи |
| Koodo Reader | AGPL-3.0 | **Нет** | Только идеи |

Практическое правило для этого проекта:
* **алгоритмы, формулы, названия полей, структура данных** — не охраняются авторским правом, берите свободно из любого источника;
* **код** — только из GPL-3.0 / MIT / BSD-3-Clause, с уведомлением;
* **AGPL-источники** — читать, понимать, писать своё. Формулы (например `page = floor((start+end)/2/size)`) — это математика, её можно использовать, но не копировать файл целиком и не переименовывать «слегка».

Также: не переносить названия приложений, иконки, шрифты и тексты строк из чужих проектов — только свои.

---

## 7. Ответы на претензии пользователя

**«Текст должен выглядеть как книга, заполнять экран, а не как выровненные по центру стихи».**
Причина: у абзацев нет красной строки и выключки, зато есть отбивка `1em` — короткие абзацы источников читаются как строфы; плюс `useWideViewPort`/`loadWithOverviewMode` могут отдалить страницу, если что-то вылезло по ширине. Лечение: книжный набор из 4.1а (indent 1.25em, `text-align: justify`, `hyphens: auto` + `lang`, `margin: 0`, `widows/orphans`, `break-inside: avoid`), выключить авто-zoom WebView, спрятать скроллбары, дать настройку «Ширина текста: вся ширина / колонка 34em». Референс — KOReader `epub.css` и дефолты Legado (отступ в два знака, 2 dp между абзацами, выключка включена).

**«Прокрутка и листание должны быть плавными и не прыгать».**
Три независимые причины, лечить все три: (1) шаг листания не равен ширине колонки — дрейф на `2 × pageMargin` за страницу (п. 2 диагноза); (2) `scroll-snap` + `behavior:'smooth'` дают двойную анимацию; (3) позиция восстанавливается процентом после каждого reflow. Решение: `--column-width` + `--page-gap`, шаг = ширина страницы, своя rAF-анимация 200 мс с отменой по касанию, позиция — якорь (абзац + доля), а не процент. Плюс не перезагружать WebView при смене главы, а дописывать секцию.

**«Зоны тапа должны остаться простыми: влево/вправо — глава в scroll, листание в paged, центр — UI».**
Именно так и надо, и именно так сейчас не работает: в paged-ветке обработчика нет перехода на следующую главу (мёртвый тап на последней странице), а `click` в WebView приходит после свайпа и после выделения. Решение: `pointerdown/pointerup` с порогом 8 px, зоны 25/50/25 (scroll) и 30/40/30 (paged), на границе главы — `requestNextChapter`/`requestPreviousChapter`. Никаких 3×3 сеток, как в Legado, и никаких жестов «два пальца = глава» — только то, что просили. Дополнительно: громкость = страница в paged и глава в scroll; долгое удержание = глава.

**«Фуллскрин должен работать предсказуемо».**
Сейчас `fullscreen` прячет только системные бары, `isUiVisible` живёт отдельно, а `onNovelSettingsChanged` не пересчитывает safe-area — отсюда «текст то под панелью, то с отступом». Решение: одно состояние «UI виден», один переключатель; при включении fullscreen на входе — скрыты и бары, и панели; тап по центру показывает/скрывает всё вместе; `updateReaderSafeArea()` вызывается из всех точек изменения геометрии (`onApplyWindowInsets`, `onNovelSettingsChanged`, `toggleUiVisibility`), а позиция после изменения отступов восстанавливается якорем.

---

### Что не проверено

Не запускал приложение и не профилировал WebView: выводы о дрейфе страниц и двойной анимации — из чтения кода (математика `100vw` vs `padding` vs шаг `innerWidth`), их надо подтвердить на устройстве скриншотами до/после. Утверждение про Librera 10 + foliate-js взято из release notes F-Droid: в публичном master модуля `libReflow` нет.
