# Документация Hanten

В workspace два независимых проекта:

- **Hanten** — Android-приложение (этот репозиторий), CI в
  `Hanten/.github/workflows/`;
- **hanten-parsers** — библиотека парсеров источников, CI в
  `hanten-parsers/.github/workflows/ci.yml`: компиляция + чистые JVM-тесты,
  RU/UA live sweep — вручную. Поддерживаемый scope — только русские и
  украинские источники.

С чего начать:

| Документ | Для кого | Что внутри |
|---|---|---|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | новый разработчик | карта репозитория, слои, БД, сеть, парсеры, потоки reader/download/tracker/backup, куда добавлять код |
| [CODE_REVIEW.md](./CODE_REVIEW.md) | мейнтейнер, ревьюер | баги P0–P2, мёртвый код, безопасность, расхождения документации, порядок фиксов |
| [ROADMAP.md](./ROADMAP.md) | продукт/мейнтейнер | чего не хватает относительно Mihon/Kotatsu/Komikku/Panels/Suwayomi, приоритеты |
| [SECURITY.md](./SECURITY.md) | мейнтейнер, релиз-инженер | secret-scan, история токенов, бэкапы, сеть, Mihon, чеклист перед релизом |
| [FUTON_PORT.md](./FUTON_PORT.md) | мейнтейнер | что забрать из Futon, какие источники разбирать отдельно, отчёт агента |
| [NOVEL_ROADMAP.md](./NOVEL_ROADMAP.md) | продукт/мейнтейнер | паритет новелл с мангой, улучшения читалки, этапы 2.6–2.8 |
| [../AGENTS.md](../AGENTS.md) | все | стиль кода, команды, паттерны Hilt/Flow/Worker |
| [../CI.md](../CI.md) | релиз-инженер | подпись, keystore, GitHub Actions |
| [../DESIGN.md](../DESIGN.md) | дизайнер/UI | дизайн-система (целевая, не всё реализовано) |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | контрибьютор | процесс PR |
| [../../hanten-parsers/CONTRIBUTING.md](../../hanten-parsers/CONTRIBUTING.md) | автор парсеров | как писать и тестировать источник |

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
