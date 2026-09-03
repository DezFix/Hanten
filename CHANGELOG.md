# Changelog

All notable changes to this project are documented in this file.

The format is based on "Keep a Changelog" and follows semantic versioning where possible.

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
