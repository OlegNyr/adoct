---
name: jira-confluence
description: >
  Работа с Jira/Confluence в проекте AsciiDocTools: конвертация Confluence↔AsciiDoc, атрибут
  :confluency-id: и round-trip публикации, какие атрибуты .adoc влияют на публикацию (заголовок,
  метки, картинки), встроенный MCP-сервер (jira_*/confluence_* тулы, ростер/шаблоны/workflow) и его
  запуск (плагин / CLI / GraalVM native). Используй ВСЕГДА, когда задача касается экспорта из
  Confluence, публикации .adoc обратно, значения :confluency-id:, меток страниц или MCP-тулов
  Jira/Confluence. Триггеры: "confluency-id", "confluence-id", "опубликовать в Confluence",
  "экспорт из Confluence", "round-trip", "MCP", "jira_", "confluence_", "ростер/шаблоны/workflow".
---

# Jira / Confluence — конвенции AsciiDocTools

Движок (`:adoct-confluence`) конвертирует Confluence (HTML storage) ↔ AsciiDoc. Экспорт тянет дерево
страниц в `.adoc`, публикация заливает `.adoc` обратно. Связь страница↔файл держится на атрибуте
заголовка **`:confluency-id:`** (пишется именно так, с буквой «y»).

## `:confluency-id:` — что можно записывать

Атрибут в шапке `.adoc` (после строки `= Заголовок`). При публикации значение трактуется так:

| Значение | Поведение |
|---|---|
| **Число** (`:confluency-id: 12345`) | Обновить эту страницу Confluence по id. |
| **URL** | Резолвится в числовой id и **переписывается обратно** в файл (кэш). Поддержаны обе формы: `…?pageId=NNN` и «человеческая» `…/display/SPACE/Title`. |
| **`ignore`** (`:confluency-id: ignore`) | Файл **пропускается** при публикации. |
| **Отсутствует** | Для корневого `index.adoc` — id берётся из целевого URL публикации (головная страница); для остальных файлов в подпапках — создаётся **новая** дочерняя страница, её новый id дописывается в файл. |

Ту же резолюцию URL→id движок применяет и при экспорте (можно дать `…/display/SPACE/Title` вместо
числа). Кэш резолва живёт в `links.json` (только в debug-режиме экспорта).

При экспорте id страницы автоматически пишется в шапку как `:confluency-id:`, чтобы публикация
делала round-trip в ту же страницу.

## Другие атрибуты `.adoc`, влияющие на публикацию

- **`= Заголовок`** (level-0 header) → заголовок страницы Confluence.
- **`:keywords: метка1, метка2`** → метки (labels) страницы.
- **`:imagesdir: attache`** → база для локальных картинок/вложений (по умолчанию `attache`).
- Картинки `image::name[]` и вложения заливаются как attachments; ссылки на существующие локальные
  файлы превращаются в ссылки на вложения.

## Экспорт (Confluence → AsciiDoc)

- В IDE: **`Alt+Shift+I`** (Confluence export) — сервис `ConvertDocsUrlToAdoc`. Вводится URL страницы
  (число, `?pageId=`, или `/display/SPACE/Title`).
- Рекурсивно тянет дерево: каждая дочерняя страница — в подпапку `<родитель>/<заголовок ребёнка>/`.
- Галки: брать дочерние страницы; брать вложения/картинки. Без вложений — картинки остаются ссылками.
- Документы >700 строк бьются на файлы по `==` заголовкам (`SpliteratorAdoc`), иначе один `index.adoc`.
- `source/` (сырые `body.storage.html` и т.п.) пишется только в debug; пустые `attache/`/`files/` удаляются.

## Публикация (AsciiDoc → Confluence)

- В IDE: сервис `PublishDocsToConfluence` (importer **`Alt+I`** для выбора). Заливает дерево `.adoc`.
- Корневой `index.adoc` = головная страница (parentId из целевого URL). Файлы в подпапках — дочерние.
- Round-trip держится на `:confluency-id:` (см. выше). Файл с `:confluency-id: ignore` пропускается.
- Движок: `AdocPublisher` парсит `.adoc` через AsciidoctorJ, рендерит в storage XHTML
  (`StorageRenderer`), обновляет тело, заливает картинки-вложения, проставляет метки из `:keywords:`.

## MCP-сервер (Jira + Confluence из ассистента)

Встроенный MCP-сервер (модуль `:adoct-mcp`) поднимается плагином на старте IDE (HTTP, JSON-RPC).
**62 тула** (Jira + Confluence), персона-промпт `product_owner`. Аутентификация — **PAT** (Server/DC).

Шпаргалка по группам тулов:
- **Jira задачи/agile:** `jira_search` (JQL), `jira_get_issue`, `jira_create_issue`,
  `jira_update_issue`, `jira_get_transitions`+`jira_transition_issue`, `jira_add_comment`,
  спринты/доски (`jira_list_sprints`, `jira_get_board_backlog`…), связи/worklog/watchers.
- **Jira команда/шаблоны/workflow:** `jira_list_team` (ростер), `jira_list_assignable_users`
  (живой список), `jira_assign_issue`, `jira_list_templates` (свободный текст — модель сама собирает
  `jira_create_issue`), `jira_get_workflow` (PlantUML state), `jira_get_project_statuses`.
- **Confluence:** `confluence_search` (CQL), `confluence_get_page` (есть `format=adoc` и `fast=true` —
  отдаёт страницу в AsciiDoc, in-memory, без скачивания вложений), `confluence_get_page_diff`
  (`format=adoc`), создание/удаление/перемещение, комментарии, метки, вложения.
- **Движок ⭐:** `confluence_export_tree_to_adoc`, `confluence_publish_adoc`.

## Запуск MCP

- **Плагин** (по умолчанию): Settings → Tools → *AsciiDocTools MCP* — включение, адрес/порт,
  проект Jira / пространство Confluence по умолчанию, **ростер команды**, **шаблоны задач** (свободный
  текст), **диаграмма состояний** (PlantUML). Изменения перезапускают сервер.
- **CLI** (`:adoct-mcp-cli`): stdio (по умолчанию) или `--http --port N`. Конфиг — `--config <json>`
  и/или env `MCP_*` (`MCP_HOST`/`MCP_TOKEN`, `MCP_PORT`…). `./gradlew :adoct-mcp-cli:installDist`.
- **GraalVM native:** `./gradlew :adoct-mcp-cli:nativeCompile --no-configuration-cache` → бинарь
  `build/native/nativeCompile/adoct-mcp` (мгновенный старт; **61 тул** — без `confluence_publish_adoc`,
  т.к. asciidoctorj/JRuby несовместим с native; публикуй через плагин/JVM-CLI).

## Настройки подключения

- **Серверы + PAT:** Settings → Tools → *Confluence* (таблица host/token) — общий список эндпоинтов
  для экспорта/публикации и MCP. PAT — токен Server/Data Center.
- В `.adoc` указывать абсолютные id/URL; пространство и parent выводятся из целевой страницы публикации.

## Подводные камни

- Атрибут — **`confluency-id`** (с «y»), не `confluence-id`. В коде константа `ID_ATTRIBUTE`.
- Комментарии и пользовательские строки в репозитории часто на **русском** — соблюдай язык файла.
- Новые HTML-теги → `NodeTag` в `parser/build/tag/`; новые макросы → `NodeMacro` в
  `parser/build/macro/` (порядок в реестре важен — первый подходящий обработчик выигрывает).
