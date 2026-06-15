# GitVerse Publishing — инструкция для Claude

> Этот файл — runbook. Claude должен пройти по нему и настроить публикацию плагина
> в **собственный кастомный репозиторий плагинов IntelliJ** через GitVerse.
> Решения уже приняты (раздел 0) — не пересматривай их, а реализуй.
> Там, где написано **[ПРОВЕРИТЬ]** — не угадывай, а сверься с актуальной докой
> GitVerse или спроси пользователя; от этих пунктов зависит работоспособность схемы.

---

## 0. Контекст и принятые решения (не пересматривать)

- Это **open-source IntelliJ Platform плагин**.
- Распространение — через **собственный кастомный репозиторий плагинов**, НЕ через
  JetBrains Marketplace. Причина: плагин сырой, версий будет много, модерация не нужна.
- Репозиторий **публичный, без аутентификации** (IDE не умеет basic-auth для кастомных
  репозиториев — поэтому всё должно отдаваться анонимно).
- Хостинг — **GitVerse** (gitverse.ru), серверы в РФ. Это важно: рабочие IDEA из
  банковского контура должны дотягиваться до URL, а внешний GitHub оттуда может быть закрыт.
- Целевая архитектура:
  - **`updatePlugins.xml` → GitVerse Pages** (статика, публично, всегда указывает на
    последнюю версию).
  - **`.zip` плагина → GitVerse Releases** (release-ассеты дают долговременный публичный
    URL). НЕ коммитить zip в git и НЕ использовать CI-артефакты (`upload-artifact`):
    они живут только 30 суток и ограничены 500 Мб — под хостинг не годятся.
  - **CI на пуш тега** собирает плагин и публикует и то, и другое.

---

## 1. Pre-flight (делает пользователь в UI GitVerse — Claude должен попросить)

Эти шаги требуют Сбер ID и веб-интерфейса, агент их сам не сделает. Claude должен
явно перечислить их пользователю и дождаться подтверждения:

1. Репозиторий плагина создан на GitVerse и **публичный**.
2. Включён CI/CD: `Настройки → Репозиторий → CI/CD → Обновить`. Облачного раннера
   `ubuntu-latest` достаточно.
3. Включён Pages на отдельной ветке публикации (например `pages`). Запиши итоговый
   адрес сайта — он вида `https://<username>.gitverse.site/<repository>` **[ПРОВЕРИТЬ
   точный домен — в части обзоров встречается `.gitverse.page`]**.
4. **[ПРОВЕРИТЬ — критично]** Уточни, какой публичный URL получают release-ассеты и
   доступны ли они на скачивание **анонимно** (без токена). Если ассеты требуют
   авторизацию — вся схема ломается, см. раздел 6, пункт «запасной план».

---

## 2. Что Claude настраивает в самом репозитории

1. Убедись, что подключён **IntelliJ Platform Gradle Plugin**
   (`org.jetbrains.intellij.platform`) и таск `buildPlugin` кладёт zip в
   `build/distributions/`. Если плагина нет — добавь и сконфигурируй.
2. Считай **реальные** значения из проекта (не выдумывай):
   - `id` плагина — из `plugin.xml` (`<id>`).
   - версия — из `gradle.properties` (`pluginVersion`) и/или `<version>` в `plugin.xml`.
   - диапазон сборок — `pluginSinceBuild` / `pluginUntilBuild`.
   `id` и `version` в `updatePlugins.xml` обязаны **совпадать** с тем, что внутри плагина.
3. Приведи `gradle.properties` к единому источнику правды для этих значений
   (`pluginGroup`, `pluginName`, `pluginVersion`, `pluginSinceBuild`, `pluginUntilBuild`).

---

## 3. Генерация `updatePlugins.xml`

`updatePlugins.xml` — это **указатель на последнюю версию**, а не история. Он содержит
один `<plugin>`-блок на наш плагин, ссылающийся на release-ассет текущего тега.

Создай скрипт `scripts/gen-update-plugins.sh`, который читает значения из
`gradle.properties` и переменных окружения CI и пишет `updatePlugins.xml`. Шаблон:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Значения подставляются из gradle.properties и окружения CI
PLUGIN_ID="${PLUGIN_ID:?}"            # из plugin.xml <id>
VERSION="${VERSION:?}"                # = тег без префикса v
SINCE_BUILD="${SINCE_BUILD:?}"
UNTIL_BUILD="${UNTIL_BUILD:-}"        # пусто = без верхней границы (см. раздел 5)
ASSET_URL="${ASSET_URL:?}"           # публичный URL zip-ассета релиза

UNTIL_ATTR=""
[ -n "$UNTIL_BUILD" ] && UNTIL_ATTR=" until-build=\"$UNTIL_BUILD\""

cat > updatePlugins.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="$PLUGIN_ID" url="$ASSET_URL" version="$VERSION">
    <idea-version since-build="$SINCE_BUILD"${UNTIL_ATTR}/>
  </plugin>
</plugins>
EOF
```

`ASSET_URL` формируется в CI из тега и имени собранного zip **[ПРОВЕРИТЬ точный паттерн
URL ассета у GitVerse]** — типовой вид по аналогии с GitHub:
`https://gitverse.ru/<owner>/<repo>/releases/download/<tag>/<file>`. Не хардкодь —
вытащи из вывода Release Action, если он его отдаёт.

---

## 4. CI workflow `.gitverse/workflows/publish.yaml`

Синтаксис GitVerse CI = GitHub-Actions-подобный. Триггер — **пуш тега `v*`**.
Шаблон (адаптировать; имена actions помечены **[ПРОВЕРИТЬ]**):

```yaml
name: publish-plugin

on:
  push:
    tags: ['v*']
  workflow_dispatch: {}   # ручной перезапуск / откат

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK
        uses: actions/setup-java@v4   # [ПРОВЕРИТЬ доступность на GitVerse]
        with:
          distribution: temurin
          java-version: '21'          # подставить версию, под которую собирается плагин

      - name: Build plugin
        run: ./gradlew buildPlugin --no-daemon

      - name: Read metadata
        run: |
          echo "VERSION=${GITHUB_REF_NAME#v}" >> "$GITHUB_ENV"   # [ПРОВЕРИТЬ имя ref-переменной в контексте GitVerse]
          echo "PLUGIN_ID=$(grep -oP '(?<=<id>).*?(?=</id>)' src/main/resources/META-INF/plugin.xml)" >> "$GITHUB_ENV"
          echo "SINCE_BUILD=$(grep '^pluginSinceBuild' gradle.properties | cut -d= -f2 | xargs)" >> "$GITHUB_ENV"
          echo "UNTIL_BUILD=$(grep '^pluginUntilBuild' gradle.properties | cut -d= -f2 | xargs)" >> "$GITHUB_ENV"

      - name: Create release & upload zip
        uses: <gitverse-release-action>   # [ПРОВЕРИТЬ — "GitVerse Release Action", точный slug в доке]
        with:
          tag: ${{ github.ref_name }}
          files: build/distributions/*.zip
        # сохрани полученный публичный URL ассета в ASSET_URL

      - name: Generate updatePlugins.xml
        run: |
          export ASSET_URL="<url-из-предыдущего-шага>"
          bash scripts/gen-update-plugins.sh

      - name: Publish to Pages branch
        run: |
          # положить updatePlugins.xml в корень ветки публикации Pages (например pages)
          # и запушить. [ПРОВЕРИТЬ механику Pages у GitVerse: ветка vs workflow-сборка]
          echo "TODO: commit updatePlugins.xml to pages branch"
```

Замечания для агента:
- Точные имена `setup-java`, ref-переменных и Release Action **сверь с докой GitVerse**
  перед финализацией — не оставляй заглушки в рабочем файле.
- Если Release Action не отдаёт готовый URL ассета — собери его из паттерна (раздел 3)
  и проверь скачивание `curl -I` без авторизации.

---

## 5. Правила версионирования и build-range (зашить в процесс)

- `pluginVersion` — строго возрастающий semver. IDE решает «есть ли апдейт» сравнением
  строк версий, поэтому порядок должен быть монотонным.
- **`pluginUntilBuild` держать пустым или с большим запасом.** Самый частый баг
  churn-плагинов: узкий `until-build` → пользователи на свежей IDE тихо перестают
  получать обновления. Предпочтительно вообще не задавать верхнюю границу, пока не
  упрёшься в реальную несовместимость API.
- Релиз = bump версии → тег `vX.Y.Z` → push. Дальше CI сам.
- Старые release-ассеты **не удалять**: откат = перенаправить `updatePlugins.xml` на
  предыдущий ассет (через `workflow_dispatch` или ручную правку XML на ветке Pages).

---

## 6. Что обязательно ПРОВЕРИТЬ при настройке (не предполагать)

1. **Точный домен Pages** и способ включения ветки публикации.
2. **Анонимный публичный URL release-ассета** — критично. Проверь `curl -I <url>`
   из чистого окружения без токена: должен отдавать 200 и сам файл.
   *Запасной план,* если ассеты только под авторизацией: класть zip-ки прямо в ветку
   Pages рядом с XML (примет раздувание git) ИЛИ проверить GitVerse Registry на
   анонимное скачивание. Но приоритет — Releases.
3. **Jekyll на Pages.** Pages по умолчанию собирает через Jekyll, который может
   своевольничать с файлами. Убедись, что `updatePlugins.xml` отдаётся байт-в-байт
   (добавь `.nojekyll` в корень ветки публикации или отключи Jekyll-обработку).
   Проверь, что IDE получает валидный XML, а не страницу-обёртку.
4. **CI-артефакты ≠ хостинг.** `upload-artifact` живёт 30 суток и ограничен 500 Мб —
   использовать только для передачи между шагами пайплайна, не как источник для IDE.

---

## 7. Критерии приёмки

Настройка считается рабочей, если:

1. Пуш тега `vX.Y.Z` создаёт релиз с zip-ассетом плагина.
2. На Pages обновляется `updatePlugins.xml`, указывающий на этот ассет, с `id`,
   `version` и `since-build`, совпадающими с самим плагином.
3. В IDEA: `Settings → Plugins → ⚙ → Manage Plugin Repositories → +` с URL до
   `updatePlugins.xml` → плагин виден и ставится.
4. Следующий тег → IDEA предлагает обновление (после планового опроса или ручного
   `Check for updates`).

---

## 8. Гигиена (обязательно)

Плагин **публичный**. В коде, ресурсах и дефолтах не должно быть ничего внутреннего:
внутренних эндпоинтов, имён сервисов, конфигов/секретов OTP, банковских URL. Полигон —
это про обкатку версий, а не про утечку внутрянки через open-source.
