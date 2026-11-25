# Документация Hanten

Hanten состоит из двух репозиториев:

- **Hanten** — Android-приложение (этот репозиторий), CI в
  `.github/workflows/`;
- **[hanten-parsers](https://github.com/DezFix/hanten-parsers)** — библиотека парсеров источников (подключается через JitPack), CI в
  `.github/workflows/ci.yml`: компиляция + чистые JVM-тесты,
  RU/UA live sweep — вручную. Поддерживаемый scope — только русские и
  украинские источники.

С чего начать:

| Документ | Для кого | Что внутри |
|---|---|---|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | новый разработчик | карта репозитория, слои, БД, сеть, парсеры, потоки reader/download/tracker/backup, куда добавлять код |
| [SECURITY.md](./SECURITY.md) | мейнтейнер, релиз-инженер | секреты, бэкапы, сеть, Mihon, приватность, чеклист перед релизом |
| [LEGAL.md](./LEGAL.md) | все | позиция проекта по контенту и DMCA |
| [../CHANGELOG.md](../CHANGELOG.md) | все | изменения по версиям |
| [../AGENTS.md](../AGENTS.md) | все | стиль кода, команды, паттерны Hilt/Flow/Worker |
| [../CI.md](../CI.md) | релиз-инженер | подпись, keystore, GitHub Actions, выпуск релиза по тегу |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | контрибьютор | процесс PR |
| [hanten-parsers/CONTRIBUTING.md](https://github.com/DezFix/hanten-parsers/blob/master/CONTRIBUTING.md) | автор парсеров | как писать и тестировать источник |

Быстрые команды:

```bash
./gradlew assembleDebug          # debug APK
./gradlew test                   # unit tests
./gradlew check                  # lint + tests
```

Если меняете схему БД — поднимите `DATABASE_VERSION`, добавьте миграцию
**только вверх** и обновите `app/schemas/<version>.json`.
Если меняете парсеры — правьте `hanten-parsers` и поднимайте версию
`parsers` в `gradle/libs.versions.toml`.
