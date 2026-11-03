package hanten.wre.app.novel.ui.reader

import hanten.wre.app.novel.data.model.NovelReadingMode

object NovelHtmlTemplate {

	fun buildDocument(
		title: String,
		initialChapterHtml: String,
		settings: NovelReaderSettings,
		initialProgress: Float = 0f,
		safeTopPx: Int = 0,
		safeBottomPx: Int = 0,
		density: Float = 1f,
		language: String = "en",
	): String {
		val cssVars = settings.toCssVariables(safeTopPx, safeBottomPx, density)
		val isPaged = settings.readingMode == NovelReadingMode.PAGED
		val startProgress = initialProgress.coerceIn(0f, 1f)

		val pagedStyle = if (isPaged) {
			"""
			html, body {
				height: 100vh;
				overflow: hidden;
				margin: 0;
				padding: 0;
			}
			#reader-container {
				position: fixed;
				inset: 0;
				overflow-x: auto;
				overflow-y: hidden;
				overscroll-behavior: contain;
				-webkit-overflow-scrolling: touch;
			}
			#content {
				column-width: var(--column-width, 100vw);
				column-gap: var(--page-gap, 0px);
				column-fill: auto;
				height: calc(100vh - var(--reader-safe-top) - var(--reader-safe-bottom) - var(--reader-page-margin));
				margin: var(--reader-safe-top) 0 calc(var(--reader-safe-bottom) + var(--reader-page-margin)) 0;
				padding: 0 calc(var(--page-gap, 0px) / 2);
				box-sizing: border-box;
			}
			"""
		} else {
			"""
			html, body {
				margin: 0;
				padding: 0;
				min-height: 100vh;
			}
			#content {
				width: 100%;
				padding: var(--reader-safe-top) var(--reader-page-margin) calc(var(--reader-safe-bottom) + var(--reader-page-margin)) var(--reader-page-margin);
				margin: 0;
				box-sizing: border-box;
			}
			"""
		}

		return """
		<!DOCTYPE html>
		<html lang="$language">
		<head>
			<meta charset="utf-8">
			<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
			<title>$title</title>
			<style id="reader-dynamic-styles">
				$cssVars
			</style>
			<style>
				$pagedStyle
				body {
					background-color: var(--reader-bg);
					color: var(--reader-text);
					font-size: var(--reader-font-size);
					font-family: var(--reader-font-family);
					line-height: var(--reader-line-height);
					transition: background-color 0.2s ease, color 0.2s ease;
					-webkit-text-size-adjust: 100%;
					user-select: text;
					overflow-wrap: break-word;
					hyphens: var(--reader-hyphens);
					-webkit-hyphens: var(--reader-hyphens);
					widows: 2;
					orphans: 2;
				}
				p {
					margin: 0 0 var(--reader-paragraph-margin) 0;
					text-indent: var(--reader-indent);
					text-align: var(--reader-text-align);
					word-wrap: break-word;
					text-rendering: optimizeLegibility;
				}
				h1 + p, h2 + p, h3 + p, h4 + p,
				blockquote p, li p, figcaption p {
					text-indent: 0;
				}
				h1, h2, h3, h4, h5, h6 {
					color: var(--reader-text);
					margin: 1.5em 0 0.8em 0;
					text-align: left;
					text-indent: 0;
					break-after: avoid;
					-webkit-column-break-after: avoid;
				}
				img, figure, table, pre, blockquote, svg {
					break-inside: avoid;
					-webkit-column-break-inside: avoid;
					page-break-inside: avoid;
				}
				img {
					max-width: 100%;
					max-height: 90vh;
					height: auto;
					object-fit: contain;
					display: block;
					margin: 1.2em auto;
					border-radius: 6px;
				}
				.chapter-heading {
					text-align: center;
					text-indent: 0;
					font-size: 1.35em;
					margin: 0 0 1.2em 0;
					break-after: avoid;
					-webkit-column-break-after: avoid;
				}
				.chapter-divider {
					border-top: 1px dashed rgba(128, 128, 128, 0.4);
					margin: 3em 0 1.5em 0;
					padding-top: 1.5em;
					text-align: center;
				}
				.chapter-divider h2 {
					font-size: 1.3em;
					opacity: 0.9;
				}
			</style>
		</head>
		<body>
			<div id="reader-container">
				<div id="content">
					$initialChapterHtml
				</div>
			</div>

			<script>
				const isPagedMode = $isPaged;
				const initialProgress = $startProgress;
				const PARAGRAPH_SELECTOR = '#content p, #content blockquote, #content h1, #content h2, #content h3, ' +
					'#content h4, #content h5, #content h6, #content figure';
				const AUTO_SCROLL_PAUSE_MS = 1200;
				const PROGRESS_NOTIFY_INTERVAL_MS = 1000;
				let isNearBottomNotified = false;
				let lastProgressNotifyTs = 0;
				let pageStep = window.innerWidth;

				let autoScrollSpeed = ${settings.autoScrollSpeed};
				let autoScrollRunning = false;
				let autoScrollRafId = 0;
				let autoScrollLastTs = 0;
				let autoScrollCarry = 0;
				let autoScrollPauseUntil = 0;

				function notifyProgress(percent) {
					const now = Date.now();
					if (now - lastProgressNotifyTs < PROGRESS_NOTIFY_INTERVAL_MS) return;
					lastProgressNotifyTs = now;
					if (window.AndroidNovelBridge) {
						window.AndroidNovelBridge.onScrollProgress(percent);
					}
				}

				function relayoutPaged() {
					if (!isPagedMode) return;
					const container = document.getElementById('reader-container');
					const content = document.getElementById('content');
					if (!container || !content) return;
					const size = container.clientWidth;
					if (size <= 0) return;
					const rootStyle = getComputedStyle(document.documentElement);
					const margin = parseFloat(rootStyle.getPropertyValue('--reader-page-margin')) || 0;
					const fontSize = parseFloat(rootStyle.getPropertyValue('--reader-font-size')) || 18;
					const gap = margin * 2;
					const divisor = Math.max(1, Math.min(2, Math.ceil(size / (34 * fontSize))));
					const columnWidth = Math.max(1, size / divisor - gap);
					content.style.setProperty('--page-gap', gap + 'px');
					content.style.setProperty('--column-width', columnWidth + 'px');
					pageStep = size;
				}

				function pageCount() {
					const container = document.getElementById('reader-container');
					if (!container || pageStep <= 0) return 1;
					return Math.max(1, Math.round(container.scrollWidth / pageStep));
				}

				function currentPageIndex() {
					const container = document.getElementById('reader-container');
					if (!container || pageStep <= 0) return 0;
					return Math.max(0, Math.round(container.scrollLeft / pageStep));
				}

				function notifyScroll() {
					if (isPagedMode) {
						const total = pageCount();
						const cur = Math.min(total, currentPageIndex() + 1);
						if (window.AndroidNovelBridge) {
							window.AndroidNovelBridge.onPageChanged(cur, total);
							window.AndroidNovelBridge.onScrollProgress(cur / total);
						}
					} else {
						const scrollY = window.scrollY || window.pageYOffset;
						const maxScroll = (document.documentElement.scrollHeight || document.body.scrollHeight) - window.innerHeight;
						const percent = maxScroll > 0 ? (scrollY / maxScroll) : 0;
						notifyProgress(Math.min(1.0, Math.max(0.0, percent)));
						// Check near bottom (within 800px)
						if (maxScroll - scrollY < 800) {
							if (!isNearBottomNotified) {
								isNearBottomNotified = true;
								if (window.AndroidNovelBridge) {
									window.AndroidNovelBridge.onNearBottom();
								}
							}
						} else {
							isNearBottomNotified = false;
						}
					}
				}

				if (isPagedMode) {
					const container = document.getElementById('reader-container');
					if (container) {
						container.addEventListener('scroll', notifyScroll, { passive: true });
					}
				} else {
					window.addEventListener('scroll', notifyScroll, { passive: true });
				}

				// Touch / Tap handling. Use pointerup with a small threshold so swipes
				// do not accidentally turn a page or switch a chapter.
				let tapStartX = 0;
				let tapStartY = 0;

				function atPageEnd() {
					const container = document.getElementById('reader-container');
					if (!container) return true;
					return container.scrollLeft >= container.scrollWidth - container.clientWidth - 2;
				}

				function atPageStart() {
					const container = document.getElementById('reader-container');
					if (!container) return true;
					return container.scrollLeft <= 2;
				}

				function turnPage(direction) {
					if (!isPagedMode) {
						if (window.AndroidNovelBridge) {
							if (direction < 0) window.AndroidNovelBridge.requestPreviousChapter();
							else window.AndroidNovelBridge.requestNextChapter();
						}
						return;
					}
					const container = document.getElementById('reader-container');
					if (!container) return;
					if (direction > 0 && atPageEnd()) {
						if (window.AndroidNovelBridge) window.AndroidNovelBridge.requestNextChapter();
						return;
					}
					if (direction < 0 && atPageStart()) {
						if (window.AndroidNovelBridge) window.AndroidNovelBridge.requestPreviousChapter();
						return;
					}
					container.scrollBy({ left: direction * pageStep, behavior: 'smooth' });
				}

				document.addEventListener('pointerdown', function(e) {
					tapStartX = e.clientX;
					tapStartY = e.clientY;
				}, { passive: true });

				document.addEventListener('pointerup', function(e) {
					if (Math.abs(e.clientX - tapStartX) > 8 || Math.abs(e.clientY - tapStartY) > 8) return;
					if (e.target.closest('a, input, textarea, select')) return;
					const selection = window.getSelection();
					if (selection && selection.toString().length > 0) return;

					const width = window.innerWidth;
					const x = e.clientX;
					const leftZone = isPagedMode ? width * 0.3 : width * 0.25;
					const rightZone = isPagedMode ? width * 0.7 : width * 0.75;
					if (x < leftZone) {
						turnPage(-1);
					} else if (x > rightZone) {
						turnPage(1);
					} else {
						if (window.AndroidNovelBridge) window.AndroidNovelBridge.onCenterTap();
					}
				}, { passive: true });

				function appendChapter(title, chapterHtml) {
					const content = document.getElementById('content');
					if (!content) return;
					const divider = document.createElement('div');
					divider.className = 'chapter-divider';
					const heading = document.createElement('h2');
					heading.textContent = title;
					divider.appendChild(heading);
					content.appendChild(divider);

					const tempDiv = document.createElement('div');
					tempDiv.innerHTML = chapterHtml;
					while (tempDiv.firstChild) {
						content.appendChild(tempDiv.firstChild);
					}
					assignParagraphIds();
					if (isPagedMode) relayoutPaged();
					isNearBottomNotified = false;
				}

				function updateStyles(cssText) {
					captureAnchor();
					const styleTag = document.getElementById('reader-dynamic-styles');
					if (styleTag) {
						styleTag.innerHTML = cssText;
					}
					if (isPagedMode) {
						// Force reflow before recalculating page count.
						void document.body.offsetWidth;
						relayoutPaged();
					}
					restoreAnchor();
				}

				function scrollToPercent(percent) {
					const p = Math.min(1.0, Math.max(0.0, percent || 0));
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						if (!container) return;
						const pages = pageCount();
						const page = Math.max(0, Math.min(pages - 1, Math.round(p * (pages - 1))));
						container.scrollLeft = page * pageStep;
					} else {
						const maxScroll = (document.documentElement.scrollHeight || document.body.scrollHeight) - window.innerHeight;
						window.scrollTo(0, maxScroll * p);
					}
				}

				function restoreProgress(percent) {
					scrollToPercent(percent);
				}

				function setSafeArea(top, bottom) {
					const root = document.documentElement;
					root.style.setProperty('--reader-safe-top', Math.max(0, top | 0) + 'px');
					root.style.setProperty('--reader-safe-bottom', Math.max(0, bottom | 0) + 'px');
				}

				// Keep the same block at the top when CSS/safe-area changes cause reflow.
				let savedAnchor = null;

				function blockTop(block) {
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						const rect = block.getBoundingClientRect();
						const containerRect = container.getBoundingClientRect();
						return rect.left - containerRect.left + container.scrollLeft;
					}
					return block.getBoundingClientRect().top + (window.scrollY || window.pageYOffset);
				}

				function captureAnchor() {
					const blocks = getParagraphElements();
					if (!blocks.length) return;
					const position = isPagedMode
						? document.getElementById('reader-container').scrollLeft
						: (window.scrollY || window.pageYOffset);
					let index = 0;
					for (let i = 0; i < blocks.length; i++) {
						if (blockTop(blocks[i]) <= position + 1) index = i; else break;
					}
					const block = blocks[index];
					const top = blockTop(block);
					const size = isPagedMode ? block.getBoundingClientRect().width : block.getBoundingClientRect().height;
					savedAnchor = {
						index: index,
						fraction: size > 0 ? Math.max(0, Math.min(1, (position - top) / size)) : 0,
					};
				}

				function restoreAnchor() {
					const anchor = savedAnchor;
					savedAnchor = null;
					if (!anchor) return;
					const blocks = getParagraphElements();
					const block = blocks[Math.max(0, Math.min(blocks.length - 1, anchor.index))];
					if (!block) return;
					const top = blockTop(block);
					const size = isPagedMode ? block.getBoundingClientRect().width : block.getBoundingClientRect().height;
					const target = Math.max(0, top + size * anchor.fraction);
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						const page = Math.floor(target / pageStep);
						container.scrollLeft = page * pageStep;
					} else {
						window.scrollTo(0, target);
					}
				}

				// Paragraph anchors for bookmarks
				function getParagraphElements() {
					return Array.prototype.slice.call(document.querySelectorAll(PARAGRAPH_SELECTOR));
				}

				function assignParagraphIds() {
					const blocks = getParagraphElements();
					for (let i = 0; i < blocks.length; i++) {
						blocks[i].setAttribute('data-p', String(i));
					}
				}

				function getCurrentParagraph() {
					const blocks = getParagraphElements();
					if (!blocks.length) return -1;
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						if (!container) return 0;
						const containerRect = container.getBoundingClientRect();
						const centerX = container.scrollLeft + containerRect.width / 2;
						let index = 0;
						for (let i = 0; i < blocks.length; i++) {
							const rect = blocks[i].getBoundingClientRect();
							const left = rect.left - containerRect.left + container.scrollLeft;
							if (left <= centerX) index = i; else break;
						}
						return index;
					}
					const centerY = window.innerHeight / 2;
					let index = 0;
					for (let i = 0; i < blocks.length; i++) {
						if (blocks[i].getBoundingClientRect().top <= centerY) index = i; else break;
					}
					return index;
				}

				function scrollToParagraph(index) {
					assignParagraphIds();
					const blocks = getParagraphElements();
					if (!blocks.length) return false;
					const target = blocks[Math.max(0, Math.min(blocks.length - 1, Math.round(index || 0)))];
					if (!target) return false;
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						if (!container) return false;
						const containerRect = container.getBoundingClientRect();
						const offset = target.getBoundingClientRect().left - containerRect.left + container.scrollLeft;
						const page = Math.max(0, Math.floor(offset / pageStep));
						container.scrollLeft = page * pageStep;
					} else {
						const top = target.getBoundingClientRect().top + (window.scrollY || window.pageYOffset) - 8;
						window.scrollTo(0, Math.max(0, top));
					}
					return true;
				}

				function restoreBookmark(paragraph, percent) {
					assignParagraphIds();
					// scroll == 0 from older bookmarks means "no anchor", so fall back to the saved percent
					if (paragraph > 0 && scrollToParagraph(paragraph)) return true;
					if (paragraph === 0 && (percent || 0) <= 0.02) {
						window.scrollTo(0, 0);
						return true;
					}
					restoreProgress(percent || 0);
					return false;
				}

				// Auto-scroll: speed is measured in screen heights per minute, 0 = off
				function setAutoScrollSpeed(speed) {
					autoScrollSpeed = Math.max(0, Number(speed) || 0);
				}

				function startAutoScroll() {
					if (isPagedMode || autoScrollSpeed <= 0) return false;
					if (autoScrollRunning) return true;
					autoScrollRunning = true;
					autoScrollLastTs = 0;
					autoScrollCarry = 0;
					autoScrollPauseUntil = 0;
					autoScrollRafId = requestAnimationFrame(stepAutoScroll);
					return true;
				}

				function stopAutoScroll() {
					autoScrollRunning = false;
					if (autoScrollRafId) {
						cancelAnimationFrame(autoScrollRafId);
						autoScrollRafId = 0;
					}
				}

				function pauseAutoScroll() {
					autoScrollPauseUntil = performance.now() + AUTO_SCROLL_PAUSE_MS;
				}

				function stepAutoScroll(timestamp) {
					if (!autoScrollRunning) return;
					if (!autoScrollLastTs) autoScrollLastTs = timestamp;
					const dt = Math.min(100, timestamp - autoScrollLastTs);
					autoScrollLastTs = timestamp;
					if (timestamp >= autoScrollPauseUntil) {
						const maxScroll = (document.documentElement.scrollHeight || document.body.scrollHeight) - window.innerHeight;
						const current = window.scrollY || window.pageYOffset;
						if (maxScroll <= 0 || current >= maxScroll - 1) {
							stopAutoScroll();
							return;
						}
						autoScrollCarry += autoScrollSpeed * window.innerHeight / 60000 * dt;
						const step = Math.floor(autoScrollCarry);
						if (step > 0) {
							autoScrollCarry -= step;
							window.scrollTo(0, current + step);
						}
					}
					autoScrollRafId = requestAnimationFrame(stepAutoScroll);
				}

				if (!isPagedMode) {
					window.addEventListener('touchstart', pauseAutoScroll, { passive: true });
					window.addEventListener('wheel', pauseAutoScroll, { passive: true });
					window.addEventListener('mousedown', pauseAutoScroll, { passive: true });
				}

				window.restoreProgress = restoreProgress;
				window.applySettings = updateStyles;
				window.setSafeArea = setSafeArea;
				window.captureAnchor = captureAnchor;
				window.restoreAnchor = restoreAnchor;
				window.getCurrentParagraph = getCurrentParagraph;
				window.scrollToParagraph = scrollToParagraph;
				window.restoreBookmark = restoreBookmark;
				window.setAutoScrollSpeed = setAutoScrollSpeed;
				window.startAutoScroll = startAutoScroll;
				window.stopAutoScroll = stopAutoScroll;
				window.turnPage = turnPage;

				assignParagraphIds();
				relayoutPaged();
				window.addEventListener('resize', relayoutPaged);

				if (initialProgress > 0) {
					window.addEventListener('load', function() {
						setTimeout(function() {
							relayoutPaged();
							restoreProgress(initialProgress);
						}, 60);
					});
				}
			</script>
		</body>
		</html>
		""".trimIndent()
	}
}
