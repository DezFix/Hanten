# Changelog

All notable changes to this project are documented in this file.

The format is based on "Keep a Changelog" and follows semantic versioning where possible.

## 1.1.9
Date: 2026-09-05

### Highlights
- New source: ReComics (recomics.org, Remanga-compatible API)

### Fixes
- HoneyManga chapters: pagination instead of rejected pageSize=999999 (root cause of empty chapters)
- HoneyManga pages via v2 frames URL (old two-segment path returns 404)
- HoneyManga statuses, authors, covers fallback

### Maintenance
- Parsers v2026.09.05
- versionName 1.1.9 / versionCode 10109

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
