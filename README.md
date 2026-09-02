<div align="center">

# Hanten — свободная читалка манги для Android

**Форк проектов [Kotatsu](https://github.com/KotatsuApp/Kotatsu) и [Futon](https://github.com/AppFuton/Futon) • Пакет `hanten.wre.app` • Прямые обновления из GitHub Releases**

![Android 6.0](https://img.shields.io/badge/android-6.0+-brightgreen)
[![Sources count](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2FKotatsu-Redo%2Fkotatsu-parsers-redo%2Frefs%2Fheads%2Fmaster%2F.github%2Fsummary.yaml&query=total&label=manga%20sources&color=%23E9321C)](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo)
[![License](https://img.shields.io/github/license/DezFix/Hanten)](https://github.com/DezFix/Hanten/blob/devel/LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/DezFix/Hanten?sort=date&display_name=tag&style=flat)](https://github.com/DezFix/Hanten/releases/latest)

</div>

### Main Features

<div align="left">

* Online [manga catalogues](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo) (with 1200+ manga sources)
* Support for [Tachiyomi keiyoushi](https://github.com/keiyoushi/extensions) extensions
* Search manga by name, genres and more filters
* Favorites organized by user-defined categories
* Reading history, bookmarks and incognito mode support
* Download manga and read it offline. Third-party CBZ archives are also supported
* Clean and convenient Material You UI, optimized for phones, tablets and desktop
* Standard and Webtoon-optimized customizable reader, gesture support on reading interface
* Notifications about new chapters with updates feed, manga recommendations (with filters)
* Integration with manga tracking services: Shikimori, AniList, MyAnimeList, Kitsu
* Password / fingerprint-protected access to the app
* Automatically sync app data with other devices on the same account
* Support for older devices running Android 6.0+

</div>

### Development Setup

#### Prerequisites

- **JDK 17** (recommended: [Temurin](https://adoptium.net/temurin/releases/) distribution)
- **Android SDK** (compile SDK 36, build tools 35.0.0, minimum SDK 23)
- **Android Studio** (recommended) or Android SDK command-line tools

#### Building the Project

1. **Clone the repository:**
   ```bash
   git clone https://github.com/DezFix/Hanten.git
   cd Hanten
   ```

2. **Build debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build release APK:**
   ```bash
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`

   *Note: Requires keystore setup via environment variables or `local.properties`*

For detailed contribution guidelines, see [CONTRIBUTING.md](./CONTRIBUTING.md).

### In-App Screenshots

<div align="center">
    <img src="./metadata/en-US/images/phoneScreenshots/1.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/2.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/3.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/4.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/5.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/6.png" alt="Mobile view" width="250"/>
</div>

<br>

<div align="center">
    <img src="./metadata/en-US/images/tenInchScreenshots/1.png" alt="Tablet view" width="400"/>
    <img src="./metadata/en-US/images/tenInchScreenshots/2.png" alt="Tablet view" width="400"/>
</div>

### Contributing

Pull requests are welcome. See [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

### Certificate fingerprints

```plaintext
E0:FF:B8:DB:6A:CE:CC:B7:C9:F9:C7:8C:A4:B1:33:A0:E3:88:EC:E4:4C:6C:E1:87:9E:D9:2C:33:F5:76:5D:35
```

### License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

<div align="left">

You may copy, distribute and modify the software as long as you track changes/dates in source files. Any modifications
to or software including (via compiler) GPL-licensed code must also be made available under the GPL along with build &
install instructions.

</div>

### DMCA disclaimer

<div align="left">

The developers of this application do not have any affiliation with the content available in the app and does not store
or distribute any content. This application should be considered a web browser, all content that can be found using this
application is freely available on the Internet. All DMCA takedown requests should be sent to the owners of the website
where the content is hosted.

</div>

---

### Acknowledgments

<div align="left">

**Hanten is built upon the exceptional work of the [Kotatsu](https://github.com/KotatsuApp/Kotatsu) project.**

We are deeply grateful to:

* **The original Kotatsu developers** for creating such an outstanding manga reader and making it open source
* **The Kotatsu community** for their contributions, testing, and support
* **All translators** who helped localize Kotatsu through [Weblate](https://hosted.weblate.org/engage/kotatsu/)
* **[Kotatsu-Redo](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo)** for continuing parser development and maintenance
* **[Kototoro](https://github.com/Kototoro-app/Kototoro)** for the Tachiyomi extension integration guide

This project stands on the shoulders of giants. The Kotatsu team's dedication to creating a feature-rich, user-friendly manga reader has provided an incredible foundation for Hanten to build upon.

**Thank you to everyone who contributed to Kotatsu — your work continues to benefit the manga reading community!**

For the original Kotatsu project, please visit: [github.com/KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu)

</div>
