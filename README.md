<div align="center">

# Hanten — свободная читалка манги для Android

**Форк проектов [Kotatsu](https://github.com/KotatsuApp/Kotatsu) и [Futon](https://github.com/AppFuton/Futon) • Пакет `hanten.wre.app` • Прямые обновления из GitHub Releases**

**Hanten is a fork of Kotatsu and Futon — free and open-source manga reader for Android with built-in sources.**

![Android 6.0](https://img.shields.io/badge/android-6.0+-brightgreen) [![License](https://img.shields.io/github/license/DezFix/Hanten)](https://github.com/DezFix/Hanten/blob/main/LICENSE) [![GitHub Release](https://img.shields.io/github/v/release/DezFix/Hanten?sort=date&display_name=tag&style=flat&link=https%3A%2F%2Fgithub.com%2FDezFix%2FHanten%2Freleases%2Flatest)](https://github.com/DezFix/Hanten/releases/latest)

</div>

> **Происхождение:** [KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu) → [AppFuton/Futon](https://github.com/AppFuton/Futon) → **DezFix/Hanten** (`hanten.wre.app`).  
> Оригинальный Kotatsu создал отличную базу, Futon продолжил развитие — Hanten продолжает эту линию с фокусом на стабильность, реверс-инжиниринг источников и обновления прямо из приложения без стора и без Discord.

### Главные возможности

<div align="left">

* Каталоги из [kotatsu-parsers](https://github.com/DezFix/futon-parsers) (1200+ источников) + поддержка расширений [Tachiyomi/keiyoushi](https://github.com/keiyoushi/extensions)
* Поиск по названию, жанрам и фильтрам
* Избранное с пользовательскими категориями
* История, закладки, режим инкогнито
* Скачивание манги и чтение офлайн (CBZ тоже)
* Material You UI для телефонов, планшетов и десктопа
* Читалка: стандартный и вебтун-режимы, жесты, фильтры
* Лента обновлений, рекомендации
* Интеграция с Shikimori, AniList, MyAnimeList, Kitsu
* Защита приложения паролем/отпечатком
* Синхронизация данных между устройствами
* Поддержка Android 6.0+ (minSdk 23, targetSdk 36)
* **Обновление в один тап прямо из GitHub Releases** — без Google Play / F-Droid (проверка в Настройки → О программе → Проверить обновления)

</div>

### Установка и обновления

* **Скачать APK:** [Releases](https://github.com/DezFix/Hanten/releases/latest) → `app-release.apk`
* **Обновление внутри приложения:** `Настройки → О программе → Проверить обновления` → скачать и установить (`REQUEST_INSTALL_PACKAGES`). Никакого Discord.
* F-Droid/IzzyOnDroid — по желанию, но основной канал — GitHub Releases этого репозитория.

### Сборка без Android Studio

#### Требования

- **JDK 17** (Temurin 17)
- **Android SDK** (compileSdk 36, buildTools 35, minSdk 23)
- **Git** + **Gradle Wrapper** (в репо)

> Android Studio **не требуется**. Достаточно командной строки.

#### Клонирование

```bash
git clone https://github.com/DezFix/Hanten.git
# или если репо еще называется Futon:
git clone https://github.com/DezFix/Futon.git
cd Hanten
```

#### Варианты сборки

```bash
# debug (для разработки)
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk

# release (нужна подпись)
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk

# nightly (версия NYYYYMMDD)
./gradlew assembleNightly
```

#### Подпись release

Через переменные окружения (CI и локально одинаково):

```bash
export KEYSTORE_FILE=/path/to/hanten.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=hanten-key
export KEY_PASSWORD=...

./gradlew assembleRelease
```

Если переменных нет — Gradle спросит интерактивно. В GitHub Actions это секреты `KEYSTORE_FILE` (base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

#### Проверки

```bash
./gradlew lint
./gradlew test
./gradlew check
```

Подробнее: [CONTRIBUTING.md](./CONTRIBUTING.md), [CI.md](./CI.md), [AGENTS.md](./AGENTS.md).

### Скриншоты

<div align="center">
    <img src="./metadata/en-US/images/phoneScreenshots/1.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/2.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/3.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/4.png" alt="Mobile view" width="250"/>
</div>

### Локализация

Переводы — через Weblate (когда будет настроен для Hanten). Пока правьте `app/src/main/res/values*/strings.xml`.

### Участие

PR приветствуются. См. [CONTRIBUTING.md](./CONTRIBUTING.md).  
Источники манги правятся не здесь, а в [DezFix/futon-parsers](https://github.com/DezFix/futon-parsers) (форк `AppFuton/futon-parsers` / `Kotatsu-Redo/kotatsu-parsers-redo`).

### Отпечаток сертификата

```plaintext
# будет сгенерирован при первом релизе Hanten (см. CI.md)
# пример: EF:48:B2:2E:F2:C5:40:45:53:1F:6E:76:00:C2:7E:C3:D0:3B:71:22:1E:0B:05:FF:B6:8E:33:57:CF:8E:4D:40
```

### Лицензия

[![GNU GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

GPL-3.0 — копируйте и модифицируйте, сохраняя изменения и инструкции по сборке.

### DMCA

Разработчики не хранят и не распространяют контент. Приложение — как браузер. Все материалы берутся из открытых источников. Запросы DMCA — к владельцам сайтов-источников.

---

### Благодарности

<div align="left">

**Hanten стоит на плечах [Kotatsu](https://github.com/KotatsuApp/Kotatsu) и [Futon](https://github.com/AppFuton/Futon).**

Спасибо:

* Команде Kotatsu за оригинальный ридер
* Команде Futon за продолжение проекта
* Сообществам Kotatsu/Futon за тесты и переводы
* [Kotatsu-Redo](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo) за парсеры
* [Kototoro](https://github.com/Kototoro-app/Kototoro) за интеграцию расширений

Оригиналы: [KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu) • [KotatsuApp/kotatsu-parsers](https://github.com/KotatsuApp/kotatsu-parsers) • [AppFuton/Futon](https://github.com/AppFuton/Futon) • [AppFuton/futon-parsers](https://github.com/AppFuton/futon-parsers)

</div>
