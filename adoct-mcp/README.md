# adoct-mcp — встроенный MCP-сервер AsciiDocTools

MCP-сервер (Model Context Protocol) поверх Jira, Confluence и Bitbucket **Server/Data Center** и движка
конвертации Confluence ↔ AsciiDoc. Даёт AI-ассистенту роль **продукт-овнера с навыками
senior Java-разработчика**: управление продуктом, проектом и командой через задачи, доски,
спринты и документацию — плюс уникальный для нас экспорт/публикация документации в AsciiDoc.

## Чем отличается от других MCP-серверов

- **Живёт внутри IntelliJ-плагина.** Поднимается автоматически при старте IDE, слушает HTTP на
  `127.0.0.1:7337` (настраивается). Не нужно ставить отдельный процесс/Docker — ассистент
  подключается к уже работающей IDE.
- **Свой транспорт без внешнего MCP SDK.** Минимальный JSON-RPC 2.0 / MCP на JDK
  `com.sun.net.httpserver` (один эндпоинт `POST /mcp`: `initialize`, `tools/list`, `tools/call`,
  `prompts/*`, `ping`). Никакого servlet-контейнера, Reactor или второго Jackson — плагин остаётся
  лёгким, без конфликтов classloader.
- **Переиспользует доступы плагина.** Host + PAT берутся из настроек AsciiDocTools; для
  «однопроектной» инсталляции задаются дефолтный проект Jira и пространство Confluence. Каждый хост
  помечается **типом** (Jira / Confluence / Bitbucket — определяется по адресу, переопределяемо) и
  флагом **«по умолчанию»**: вызов без явного `host` уходит на дефолтный хост своего типа (`jira_*` →
  Jira, `confluence_*` → Confluence), а не на первый попавшийся.
- **Уникальные тулы движка.** `confluence_export_tree_to_adoc` (выгрузка дерева страниц в локальный
  AsciiDoc) и `confluence_publish_adoc` (публикация AsciiDoc обратно в Confluence) — то, чего нет у
  generic Atlassian-серверов.

## Архитектура

```
AI-клиент ──HTTP/JSON-RPC──▶ AdoctMcpServer (adoct-mcp)
                              ├─ ToolCatalog ──▶ JiraClient (adoct-jira)
                              │               ├▶ BitbucketClient (adoct-bitbucket)
                              │               └▶ ConfluenceClient + DispatcherPage/AdocPublisher (adoct-confluence)
                              └─ prompts: product_owner
IntelliJ-плагин (adoct-idea): McpServerService (старт/стоп) + McpSettingsService/Configurable + IdeaEndpointSupplier
```

- `EndpointSupplier` — шов доступов; реализация в плагине читает `ConfluenceSettingsService`.
- Запуск/остановка — `McpServerService` (`@Service` + `Disposable`), старт на старте IDE через
  `AppLifecycleListener`, off-EDT; перезапуск из настроек.
- Аутентификация Atlassian — **PAT** (`Authorization: Bearer`), цель — **Server/Data Center**.

## Каталог тулов (74) + промпт

**Jira — задачи:** `jira_get_issue`, `jira_search` (JQL), `jira_get_transitions`,
`jira_create_issue`, `jira_update_issue`, `jira_transition_issue`, `jira_delete_issue`,
`jira_batch_create_issues`, `jira_add_comment`, `jira_get_changelog`.

**Jira — агайл:** `jira_list_boards`, `jira_list_sprints`, `jira_get_sprint_issues`,
`jira_get_board_backlog`, `jira_create_sprint`, `jira_update_sprint`, `jira_add_issues_to_sprint`,
`jira_link_to_epic`.

**Jira — связи / наблюдатели / worklog:** `jira_get_link_types`, `jira_create_issue_link`,
`jira_remove_issue_link`, `jira_create_remote_issue_link`, `jira_get_issue_watchers`,
`jira_add_watcher`, `jira_remove_watcher`, `jira_get_worklog`, `jira_add_worklog`.

**Jira — проект / метаданные / вложения:** `jira_list_projects`, `jira_get_current_user`,
`jira_get_project_versions`, `jira_create_version`, `jira_get_project_components`,
`jira_search_fields`, `jira_get_attachments`, `jira_download_attachments`.

**Jira — команда / шаблоны / workflow:** `jira_list_team`, `jira_list_assignable_users`,
`jira_assign_issue`, `jira_list_templates`, `jira_get_workflow`, `jira_get_project_statuses`.

**Confluence — чтение:** `confluence_get_page`, `confluence_search` (по заголовку и тексту:
`title=`→`title~`→`text~`; по умолчанию по всем пространствам, в результатах — ключ пространства),
`confluence_list_spaces`, `confluence_get_default_space`,
`confluence_find_page`,
`confluence_get_child_pages`, `confluence_get_user`, `confluence_get_page_history`,
`confluence_get_page_diff`, `confluence_get_comments`, `confluence_get_labels`,
`confluence_get_attachments`.

**Confluence — запись:** `confluence_delete_page`, `confluence_move_page`, `confluence_add_comment`,
`confluence_reply_to_comment`, `confluence_add_labels`, `confluence_delete_label`,
`confluence_upload_attachment`, `confluence_download_attachment`, `confluence_delete_attachment`.

**Confluence — движок ⭐:** `confluence_export_tree_to_adoc`, `confluence_publish_adoc`.

**Bitbucket (Server/DC):** `bitbucket_search` (поиск кода по содержимому/именам файлов,
`POST /rest/search/latest/search`, фильтр `projectKey`/`repoSlug`), `bitbucket_list_projects`,
`bitbucket_list_repositories`, `bitbucket_get_repository`, `bitbucket_get_file`, `bitbucket_browse`,
`bitbucket_list_pull_requests`, `bitbucket_get_pull_request`, `bitbucket_get_pull_request_diff`,
`bitbucket_get_pull_request_activities` (чтение; PAT Bearer, как Jira/Confluence).

**Prompt:** `product_owner` — персона PO с навыками senior Java.

### Страница в AsciiDoc (`confluence_get_page`)

Помимо storage-тела, `confluence_get_page` умеет отдавать страницу сразу в AsciiDoc:

- `format=adoc` — конвертирует страницу нашим движком и кладёт текст в поле `adoc` (по умолчанию `storage`).
- `fast=true` (только для `adoc`) — быстрый режим для подачи контекста: ссылки резолвятся локально
  (страницы — из rendered view, вложения — из метаданных; пользователи остаются нерезолвленными), без
  дополнительных REST-запросов `search`/`user`.

Конвертация полностью in-memory: вложения и картинки не скачиваются, временные файлы не пишутся
(длинные code/PlantUML-блоки инлайнятся, drawio отдаётся ссылкой `image::…[]`). С `fast=true` весь вызов
укладывается в один `getMainPage` — удобно «положить» страницу в контекст ассистента.

`confluence_get_page_diff` тоже принимает `format=adoc` — тела сравниваемых версий возвращаются в AsciiDoc.

### Командное управление

В настройках (Settings → Tools → AsciiDocTools MCP) задаются: **ростер команды** (таблица username/имя/роль →
`jira_list_team`; живой список — `jira_list_assignable_users`) и **типы задач** — на каждый тип задаётся
многострочный **шаблон** (*свободный текст* → `jira_list_templates`; вызов `jira_create_issue` модель собирает
сама по тексту шаблона) и **диаграмма состояний** (*PlantUML state* → `jira_get_workflow`; и шаблон, и
состояния привязаны к типу задачи). Живые статусы — `jira_get_project_statuses`, назначение —
`jira_assign_issue`. Экран также показывает URL для подключения и статус сервера (запущен/остановлен).
Изменения настроек перезапускают сервер и сразу видны тулам.

## CLI и GraalVM native

Модуль `adoct-mcp-cli` поднимает тот же сервер из командной строки: **stdio** (по умолчанию, один
JSON-RPC на строку) или **HTTP** (`--http --port N`). Конфиг — `--config <file.json>` и/или `MCP_*`
(env): `MCP_HOST`/`MCP_TOKEN`/`MCP_KIND`, `MCP_PORT`, `MCP_TRANSPORT`, `MCP_JIRA_PROJECT`,
`MCP_CONFLUENCE_SPACE`. В JSON у каждого `endpoints[]` можно задать `"kind"` (`jira`/`confluence`/
`bitbucket`) и `"default": true` — маршрутизация вызовов без `host` идёт на дефолтный хост нужного типа.

- JVM: `./gradlew :adoct-mcp-cli:installDist` → `build/install/adoct-mcp/bin/adoct-mcp`.
- Native (GraalVM): `./gradlew :adoct-mcp-cli:nativeCompile --no-configuration-cache` (вход
  `McpCliNative` → `ToolRegistry.coreTools()`, **73 тула** без asciidoctorj/JRuby-зависимого
  `confluence_publish_adoc`). Нужен GraalVM 21 в `GRAALVM_HOME` и C-тулчейн (на Windows — Windows SDK + MSVC).

## Сравнение с проектами на GitHub

| | **adoct-mcp (наш)** | **sooperset/mcp-atlassian** | **atlassian/atlassian-mcp-server (офиц.)** |
|---|---|---|---|
| Язык / рантайм | Java, **внутри IntelliJ-плагина** | Python, отдельный процесс/Docker | Удалённый SaaS (Atlassian) |
| Транспорт | HTTP (свой JSON-RPC, без SDK) | STDIO / SSE / HTTP | Remote HTTP (SSE) |
| Цель | **Server/Data Center** | Cloud **и** Server/DC | Только **Cloud** |
| Аутентификация | PAT (из настроек IDE) | PAT / Basic / OAuth | OAuth 2.1 |
| Кол-во тулов | **74** (41 Jira + 23 Confluence + 10 Bitbucket) | **~72** (52 Jira + 20 Confluence) | много (Jira/Confluence/JSM/Bitbucket/Compass) |
| Продукты | Jira + Confluence | Jira + Confluence | + JSM, Bitbucket, Compass |
| Экспорт/публикация AsciiDoc | ✅ **уникально** | ❌ | ❌ |
| Персона-промпт | ✅ `product_owner` | ❌ | ❌ |
| Запуск | авто при старте IDE | вручную/Docker | управляемый облаком |

### Покрытие функций (что есть у них и у нас)

| Область | sooperset | adoct-mcp |
|---|---|---|
| Jira: получить задачу / JQL-поиск | ✅ | ✅ |
| Jira: создать / обновить / перейти по статусу / комментарий | ✅ | ✅ |
| Jira: проекты / текущий пользователь | ✅ | ✅ |
| Jira: доски / спринты / задачи спринта / бэклог | ✅ | ✅ |
| Jira: удаление, batch-создание | ✅ | ✅ |
| Jira: worklog (трудозатраты) | ✅ | ✅ |
| Jira: связи задач / epic-link / remote-link | ✅ | ✅ |
| Jira: watchers | ✅ | ✅ |
| Jira: версии / компоненты проекта | ✅ | ✅ |
| Jira: создать/обновить спринт, добавить в спринт | ✅ | ✅ |
| Jira: поля (field), changelog | ✅ | ✅ |
| Jira: вложения (скачать/метаданные) | ✅ | ✅ |
| Jira: dev-info, SLA, Service Desk, ProForma (нужны приложения) | ✅ | ❌ |
| Confluence: CQL-поиск / страница / дочерние / пользователь | ✅ | ✅ |
| Confluence: найти страницу по space+title | ⚠️ (через CQL) | ✅ |
| Confluence: создать / обновить страницу | ✅ | ⚠️ через `confluence_publish_adoc` (из AsciiDoc) |
| Confluence: удалить / переместить страницу | ✅ | ✅ |
| Confluence: история / diff версий | ✅ | ✅ |
| Confluence: комментарии (читать/добавить/ответить) | ✅ | ✅ |
| Confluence: метки (читать/добавить/удалить) | ✅ | ✅ |
| Confluence: вложения (список/загрузка/скачивание/удаление) | ✅ | ✅ |
| **Экспорт дерева страниц → AsciiDoc** | ❌ | ✅ |
| **Публикация AsciiDoc (файл/папка) → Confluence** | ❌ | ✅ |

Легенда: ✅ есть · ⚠️ частично/иначе · ❌ нет.

## Резюме

`sooperset/mcp-atlassian` — самый широкий по охвату Atlassian REST (72 тула, Cloud+DC). Официальный
сервер — облачный, с OAuth и расширением на JSM/Bitbucket/Compass. Наш `adoct-mcp` сознательно
**узкий и интегрированный**: фокус на сценариях продукт-овнера в Server/DC, запуск прямо из IDE без
отдельного процесса, переиспользование доступов плагина и **уникальный round-trip документации
Confluence ↔ AsciiDoc** через собственный движок. Очевидные направления роста — write-операции
паритета (комментарии/метки/вложения/связи/worklog Confluence и Jira, управление спринтами).
