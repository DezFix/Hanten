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
				width: 100vw;
				height: 100vh;
				overflow-x: scroll;
				overflow-y: hidden;
				scroll-snap-type: x mandatory;
				-webkit-overflow-scrolling: touch;
			}
			#content {
				column-width: 100vw;
				column-gap: 0px;
				column-fill: auto;
				height: calc(100vh - var(--reader-safe-top) - var(--reader-safe-bottom) - 2 * var(--reader-page-margin));
				margin: calc(var(--reader-safe-top) + var(--reader-page-margin)) 0 calc(var(--reader-safe-bottom) + var(--reader-page-margin)) 0;
				padding: 0 var(--reader-page-margin);
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
				padding: calc(var(--reader-safe-top) + var(--reader-page-margin)) var(--reader-page-margin) calc(var(--reader-safe-bottom) + var(--reader-page-margin)) var(--reader-page-margin);
				max-width: 800px;
				margin: 0 auto;
				box-sizing: border-box;
			}
			"""
		}

		return """
		<!DOCTYPE html>
		<html>
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
				}
				p {
					margin: 0 0 var(--reader-paragraph-margin) 0;
					text-align: var(--reader-text-align);
					word-wrap: break-word;
					text-rendering: optimizeLegibility;
				}
				h1, h2, h3, h4 {
					color: var(--reader-text);
					margin: 1.5em 0 0.8em 0;
					text-align: center;
				}
				img {
					max-width: 100%;
					height: auto;
					display: block;
					margin: 1.2em auto;
					border-radius: 6px;
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
				let isNearBottomNotified = false;

				function notifyScroll() {
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						if (!container) return;
						const cur = Math.round(container.scrollLeft / window.innerWidth) + 1;
						const total = Math.round(container.scrollWidth / window.innerWidth) || 1;
						if (window.AndroidNovelBridge) {
							window.AndroidNovelBridge.onPageChanged(cur, total);
							window.AndroidNovelBridge.onScrollProgress(cur / total);
						}
					} else {
						const scrollY = window.scrollY || window.pageYOffset;
						const maxScroll = (document.documentElement.scrollHeight || document.body.scrollHeight) - window.innerHeight;
						const percent = maxScroll > 0 ? (scrollY / maxScroll) : 0;
						if (window.AndroidNovelBridge) {
							window.AndroidNovelBridge.onScrollProgress(Math.min(1.0, Math.max(0.0, percent)));
						}
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

				// Touch / Tap handling
				document.addEventListener('click', function(e) {
					// Ignore if user clicked a link or interacted with a form control
					if (e.target.closest('a, input, textarea, select')) return;

					// Ignore taps that finish a text selection
					const selection = window.getSelection();
					if (selection && selection.toString().length > 0) return;

					const width = window.innerWidth;
					const x = e.clientX;

					if (isPagedMode) {
						const leftZone = width * 0.3;
						const rightZone = width * 0.7;
						const container = document.getElementById('reader-container');

						if (x < leftZone) {
							// Previous page
							if (container) container.scrollBy({ left: -width, behavior: 'smooth' });
						} else if (x > rightZone) {
							// Next page
							if (container) container.scrollBy({ left: width, behavior: 'smooth' });
						} else {
							// Center tap: toggle UI
							if (window.AndroidNovelBridge) window.AndroidNovelBridge.onCenterTap();
						}
					} else {
						const leftZone = width * 0.25;
						const rightZone = width * 0.75;
						if (x < leftZone) {
							// Left zone: previous chapter
							if (window.AndroidNovelBridge) window.AndroidNovelBridge.requestPreviousChapter();
						} else if (x > rightZone) {
							// Right zone: next chapter
							if (window.AndroidNovelBridge) window.AndroidNovelBridge.requestNextChapter();
						} else {
							// Center tap: toggle UI
							if (window.AndroidNovelBridge) window.AndroidNovelBridge.onCenterTap();
						}
					}
				});

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
					isNearBottomNotified = false;
				}

				function updateStyles(cssText) {
					const styleTag = document.getElementById('reader-dynamic-styles');
					if (styleTag) {
						styleTag.innerHTML = cssText;
					}
				}

				function scrollToPercent(percent) {
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						if (container) {
							container.scrollLeft = (container.scrollWidth - window.innerWidth) * percent;
						}
					} else {
						const maxScroll = (document.documentElement.scrollHeight || document.body.scrollHeight) - window.innerHeight;
						window.scrollTo(0, maxScroll * percent);
					}
				}

				function restoreProgress(percent) {
					const p = Math.min(1.0, Math.max(0.0, percent || 0));
					if (p <= 0) return;
					if (isPagedMode) {
						const container = document.getElementById('reader-container');
						if (!container) return;
						const totalPages = Math.round(container.scrollWidth / window.innerWidth) || 1;
						const page = Math.round(p * totalPages) - 1;
						container.scrollLeft = Math.max(0, Math.min(totalPages - 1, page)) * window.innerWidth;
						return;
					}
					scrollToPercent(p);
				}

				function setSafeArea(top, bottom) {
					const root = document.documentElement;
					root.style.setProperty('--reader-safe-top', Math.max(0, top | 0) + 'px');
					root.style.setProperty('--reader-safe-bottom', Math.max(0, bottom | 0) + 'px');
				}

				window.restoreProgress = restoreProgress;
				window.applySettings = updateStyles;
				window.setSafeArea = setSafeArea;

				if (initialProgress > 0) {
					window.addEventListener('load', function() {
						setTimeout(function() {
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