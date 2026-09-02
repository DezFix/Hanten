## Hanten contribution guidelines

> **Hanten** — форк [Kotatsu](https://github.com/KotatsuApp/Kotatsu) и [Futon](https://github.com/AppFuton/Futon), пакет `hanten.wre.app`. Развивается на GitHub без Discord.

+ Если хотите **исправить баг/фичу с issue** — назначьте её на себя и/или оставьте комментарий.
+ Если хотите **новую фичу** — сначала откройте issue/discussion, чтобы согласовать.
+ **Новые источники манги** — правятся не здесь, а в [DezFix/futon-parsers](https://github.com/DezFix/futon-parsers) (форк `AppFuton/futon-parsers` / `Kotatsu-Redo/kotatsu-parsers-redo`).
+ **Чат сообщества** — GitHub Discussions / Issues этого репо (Discord удалён).
+ **Обновления приложения** — доставляются напрямую из GitHub Releases (`Настройки → О программе → Проверить обновления`, `hanten.wre.app`).

Принципы:

+ **Производительность важнее красоты кода.**
+ Не трогайте `README` и инфо-файлы без необходимости (кроме опечаток).
+ Не добавляйте зависимости без нужды — размер APK критичен.
+ Объясняйте изменения в PR.
+ Сборка без Android Studio: `./gradlew assembleDebug` (JDK 17, compileSdk 36, minSdk 23).
