<div align="center">

# Hanten — свободная читалка манги для Android

**Форк проектов [Kotatsu](https://github.com/KotatsuApp/Kotatsu) и [Futon](https://github.com/AppFuton/Futon) • Пакет `hanten.wre.app` • Прямые обновления из GitHub Releases**

**В приоритете — поддержка русскоязычных и украиноязычных источников.**

**Сайт приложения: [dezfix.github.io](https://dezfix.github.io)**

![Android 6.0](https://img.shields.io/badge/android-6.0+-brightgreen)
[![Sources count](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2FDezFix%2Fhanten-parsers%2Frefs%2Fheads%2Fmaster%2F.github%2Fsummary.yaml&query=total&label=manga%20sources&color=%23E9321C)](https://github.com/DezFix/hanten-parsers)
[![License](https://img.shields.io/github/license/DezFix/Hanten)](https://github.com/DezFix/Hanten/blob/devel/LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/DezFix/Hanten?sort=date&display_name=tag&style=flat)](https://github.com/DezFix/Hanten/releases/latest)

</div>

### Основные возможности

<div align="left">

* Онлайн-[каталоги манги](https://github.com/DezFix/hanten-parsers) (130+ источников, включая Senkuro) с приоритетной поддержкой русских и украинских источников
* Поддержка расширений [Tachiyomi keiyoushi](https://github.com/keiyoushi/extensions)
* Поиск манги по названию, жанрам и другим фильтрам
* Избранное с пользовательскими категориями
* История чтения, закладки и режим инкогнито
* Скачивание манги для чтения офлайн. Поддерживаются сторонние CBZ-архивы
* Удобный интерфейс в стиле Material You, оптимизирован для телефонов, планшетов и ПК
* Настраиваемая читалка с режимами для обычной манги и вебтуна, поддержка жестов
* Уведомления о новых главах с лентой обновлений, рекомендации манги (с фильтрами)
* Интеграция с сервисами отслеживания: Shikimori, AniList, MyAnimeList, Kitsu
* Защита доступа к приложению паролем / отпечатком пальца
* Автоматическая синхронизация данных между устройствами на одном аккаунте
* Поддержка старых устройств на Android 6.0+

</div>

### Установка и разработка

#### Что нужно

- **JDK 17** (рекомендуется дистрибутив [Temurin](https://adoptium.net/temurin/releases/))
- **Android SDK** (compile SDK 36, build tools 35.0.0, minimum SDK 23)
- **Android Studio** (рекомендуется) или инструменты командной строки Android SDK

#### Сборка проекта

1. **Клонировать репозиторий:**
   ```bash
   git clone https://github.com/DezFix/Hanten.git
   cd Hanten
   ```

2. **Собрать debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   Результат: `app/build/outputs/apk/debug/app-debug.apk`

3. **Собрать release APK:**
   ```bash
   ./gradlew assembleRelease
   ```
   Результат: `app/build/outputs/apk/release/app-release.apk`

   *Примечание: нужна настройка подписи через переменные окружения или `local.properties`*

Подробные правила участия — в [CONTRIBUTING.md](./CONTRIBUTING.md).

### Скриншоты

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

### Как помочь проекту

Pull request'ы приветствуются. Правила — в [CONTRIBUTING.md](./CONTRIBUTING.md).

### Отпечатки сертификата

```plaintext
D3:F2:AB:2D:82:AF:A0:AB:02:D5:F6:83:98:26:84:7D:85:0B:39:F8:02:65:77:7D:EB:DE:AB:E6:79:E3:CC:67
```

### Лицензия

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

<div align="left">

Вы можете копировать, распространять и изменять программу при условии отслеживания изменений/дат в исходных файлах. Любые модификации или ПО, включающее (через компилятор) код под лицензией GPL, также должны распространяться под GPL вместе с инструкциями по сборке и установке.

</div>

### Отказ от ответственности (DMCA)

<div align="left">

Разработчики приложения не связаны с контентом, доступным в приложении, и не хранят и не распространяют контент. Приложение следует рассматривать как веб-браузер: весь контент, который можно найти с его помощью, свободно доступен в интернете. Все запросы на удаление по DMCA следует направлять владельцам сайтов, на которых размещен контент.

</div>

---

### Благодарности

<div align="left">

**Hanten построен на великолепной работе проекта [Kotatsu](https://github.com/KotatsuApp/Kotatsu).**

Мы искренне благодарны:

* **Разработчикам оригинальной Kotatsu** — за замечательную читалку и открытый исходный код
* **Сообществу Kotatsu** — за вклад, тестирование и поддержку
* **Всем переводчикам**, которые помогали локализовать Kotatsu через [Weblate](https://hosted.weblate.org/engage/kotatsu/)
* **[Kotatsu-Redo](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo)** — за развитие и поддержку парсеров
* **[Kototoro](https://github.com/Kototoro-app/Kototoro)** — за гайд по интеграции расширений Tachiyomi

Этот проект стоит на плечах гигантов. Преданность команды Kotatsu созданию богатой и удобной читалки дала невероятный фундамент для Hanten.

**Спасибо всем, кто вложился в Kotatsu — ваша работа продолжает служить сообществу читателей манги!**

Оригинальный проект Kotatsu: [github.com/KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu)

</div>
