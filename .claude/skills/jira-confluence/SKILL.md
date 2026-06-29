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
**64 тула** (Jira + Confluence), персона-промпт `product_owner`. Аутентификация — **PAT** (Server/DC).

Шпаргалка по группам тулов:
- **Jira задачи/agile:** `jira_search` (JQL), `jira_get_issue`, `jira_create_issue`,
  `jira_update_issue`, `jira_get_transitions`+`jira_transition_issue`, `jira_add_comment`,
  спринты/доски (`jira_list_sprints`, `jira_get_board_backlog`…), связи/worklog/watchers.
- **Jira команда/шаблоны/workflow:** `jira_list_team` (ростер), `jira_list_assignable_users`
  (живой список), `jira_assign_issue`, `jira_list_templates` (свободный текст — модель сама собирает
  `jira_create_issue`), `jira_get_workflow` (PlantUML state), `jira_get_project_statuses`.
- **Confluence:** `confluence_search` (CQL-каскад: ищет по **заголовку и тексту** — `title=`, затем
  `title~`, затем `text~`; **по умолчанию по всем пространствам**, `spaceKey` ограничивает только если
  задан; в результатах — ключ пространства), `confluence_list_spaces` / `confluence_get_default_space`
  (где и в каком пространстве идёт поиск), `confluence_get_page` (есть `format=adoc` и `fast=true` — отдаёт страницу
  в AsciiDoc, in-memory, без скачивания вложений), `confluence_get_page_diff` (`format=adoc`),
  создание/удаление/перемещение, комментарии, метки, вложения.
- **Движок ⭐:** `confluence_export_tree_to_adoc`, `confluence_publish_adoc`.

## Рабочие сценарии (playbooks)

### Создание задачи — сначала шаблон и команда

Шаблоны и ростер задаются в настройках; **перед** созданием задачи возьми их и собери поля сам:

1. **Шаблоны:** `jira_list_templates` → возьми текст нужного шаблона (Story / Bug / Spike …). Сервер
   шаблон **не парсит** — это свободный текст; ты сам извлекаешь из него issueType, заготовку summary,
   тело description (чек-лист / Definition of Done), метки и т.п.
2. **Команда:** `jira_list_team` (ростер: username / имя / роль) — кого можно назначить; либо
   `jira_list_assignable_users` (живой список assignable из Jira по проекту).
3. **Состояния (опц.):** `jira_get_workflow` (диаграмма PlantUML, как принято у команды) и/или
   `jira_get_project_statuses` (живые статусы по типам задач) — чтобы понимать, куда задача поедет.
4. **Создание:** `jira_create_issue` (projectKey подставится из настроек, если не задан; issueType,
   summary, description — по шаблону; при необходимости labels / priority / components).
5. **Назначение:** `jira_assign_issue <issueKey> <username>` — username из ростера (п.2).
6. **Связи/спринт (опц.):** `jira_link_to_epic`, `jira_create_issue_link`, `jira_add_issues_to_sprint`.

### Как работать с Jira

- **Сначала контекст:** `jira_search` (JQL, напр. `project = ABC AND status != Done`),
  `jira_get_issue` — прежде чем менять.
- **Бэклог:** формулируй истории и критерии приёмки; эпики разбивай на задачи (create + link_to_epic).
- **Движение по статусам:** `jira_get_transitions` → `jira_transition_issue` — переход задаётся **id
  перехода**, а не именем статуса (доступные переходы зависят от текущего статуса и workflow).
- **Назначение/связи/спринты/worklog/watchers** — отдельными тулами; assignee — по username из ростера.
- Перед изменяющими действиями (создание/обновление/переход/назначение) **коротко проговаривай намерение**.

### Как работать с Confluence

- **Поиск/чтение:** `confluence_search` (по заголовку и тексту, по всем пространствам; передай
  `spaceKey`, чтобы сузить — принимает ключ или URL; в выдаче — пространство каждой страницы),
  `confluence_get_page`. Где искать по умолчанию — `confluence_get_default_space` (хост + дефолтное
  пространство) или `confluence_list_spaces`. Для подачи страницы в контекст —
  `confluence_get_page format=adoc fast=true` (in-memory, без скачивания вложений, один REST-запрос).
- **Правка с round-trip:** прочитал → отредактировал `.adoc` → `confluence_publish_adoc` (связь по
  `:confluency-id:`, см. выше). Точечно — `confluence_add_comment`, `confluence_add_labels`,
  `confluence_move_page`.
- **Оффлайн-пакет дерева:** `confluence_export_tree_to_adoc` (рекурсивно в подпапки).
- **Сравнение версий:** `confluence_get_page_diff` (с `format=adoc` — тела версий в AsciiDoc).

## Запуск MCP

- **Плагин** (по умолчанию): Settings → Tools → *AsciiDocTools MCP* — включение, адрес/порт,
  проект Jira / пространство Confluence по умолчанию, **ростер команды**, **шаблоны задач** (свободный
  текст), **диаграмма состояний** (PlantUML). Изменения перезапускают сервер.
- **CLI** (`:adoct-mcp-cli`): stdio (по умолчанию) или `--http --port N`. Конфиг — `--config <json>`
  и/или env `MCP_*` (`MCP_HOST`/`MCP_TOKEN`, `MCP_PORT`…). `./gradlew :adoct-mcp-cli:installDist`.
- **GraalVM native:** `./gradlew :adoct-mcp-cli:nativeCompile --no-configuration-cache` → бинарь
  `build/native/nativeCompile/adoct-mcp` (мгновенный старт; **63 тула** — без `confluence_publish_adoc`,
  т.к. asciidoctorj/JRuby несовместим с native; публикуй через плагин/JVM-CLI).

## Настройки подключения

- **Серверы + PAT:** Settings → Tools → *Confluence* (таблица host / тип / по умолчанию / token) —
  общий список эндпоинтов для экспорта/публикации и MCP. PAT — токен Server/Data Center. **Тип**
  (Jira / Confluence / Bitbucket) определяется по адресу хоста автоматически и переопределяется
  вручную; галка **«по умолчанию»** помечает дефолтный хост для своего типа. Вызов тула без явного
  `host` уходит на дефолтный хост нужного типа (`jira_*` → Jira-хост, `confluence_*` → Confluence-хост)
  — поэтому при нескольких серверах Jira-вызов не попадёт ошибочно на хост Confluence.
- В `.adoc` указывать абсолютные id/URL; пространство и parent выводятся из целевой страницы публикации.

## Подводные камни

- Атрибут — **`confluency-id`** (с «y»), не `confluence-id`. В коде константа `ID_ATTRIBUTE`.
- Комментарии и пользовательские строки в репозитории часто на **русском** — соблюдай язык файла.
- Новые HTML-теги → `NodeTag` в `parser/build/tag/`; новые макросы → `NodeMacro` в
  `parser/build/macro/` (порядок в реестре важен — первый подходящий обработчик выигрывает).
