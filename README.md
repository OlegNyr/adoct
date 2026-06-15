# AsciiDocTools

Сборка и запуск плагина:
1. Скачать репозиторий.
2. Открыть проект в IntelliJ IDEA.
3. Импортировать проект Gradle.
4. Запустить проект панель Gradle > AsciiDocTools > Tasks > intellij platform > runIde.
5. Дождаться открытия IDE.
6. Создать или открыть тестовый проект.
7. Открыть панель инструмента импорта файлов - Alt+I

<!-- Plugin description -->
# 2025.3.0
Умеет конвертировать в AsciiDoctor из экспорта в Confluence 
<!-- Plugin description end -->

## Установка через кастомный репозиторий плагинов

Плагин распространяется не через JetBrains Marketplace, а через собственный публичный
репозиторий на GitVerse Pages.

1. **Settings → Plugins → ⚙ → Manage Plugin Repositories → +**
2. Добавьте URL:
   ```
   https://andrbars.gitverse.site/adoct/updatePlugins.xml
   ```
3. Установите **AsciiDocTools** на вкладке Marketplace. Обновления приходят автоматически
   (плановый опрос или **Check for updates**).

## Выпуск релиза

CI (`.gitverse/workflows/publish.yaml`) собирает и публикует всё на пуш тега `vX.Y.Z`:

1. Поднять `pluginVersion` в `gradle.properties` (строго возрастающий, монотонный).
2. Закоммитить и поставить тег, совпадающий с версией:
   ```
   git tag v$(grep '^pluginVersion' gradle.properties | cut -d= -f2 | xargs)
   git push origin --tags
   ```
3. CI соберёт плагин (`-Drelease=true`), выложит `.zip` в **Releases** (для людей) и
   положит `.zip` + `updatePlugins.xml` на **Pages** (на них смотрит IDE).

Откат: перезапустить workflow вручную (`workflow_dispatch`) на нужном теге — он
перегенерит `updatePlugins.xml` под ту версию. Старые ассеты в Releases не удаляются.

Детали и нерешённые `[ПРОВЕРИТЬ]`-пункты — в `GITVERSE_PUBLISHING.md`.

## Rules for LLM 

.gigacode/rules/CODESTYLE.md
.gigacode/rules/JAVAPRACTIC.md
.gigacode/rules/PRD.md