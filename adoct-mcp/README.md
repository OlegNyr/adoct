# adoct-mcp — встроенный MCP-сервер AsciiDocTools

MCP-сервер (Model Context Protocol) поверх Jira и Confluence **Server/Data Center** и движка
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
  «однопроектной» инсталляции задаются дефолтный проект Jira и пространство Confluence.
- **Уникальные тулы движка.** `confluence_export_tree_to_adoc` (выгрузка дерева страниц в локальный
  AsciiDoc) и `confluence_publish_adoc` (публикация AsciiDoc обратно в Confluence) — то, чего нет у
  generic Atlassian-серверов.

## Архитектура

```
AI-клиент ──HTTP/JSON-RPC──▶ AdoctMcpServer (adoct-mcp)
                              ├─ ToolCatalog ──▶ JiraClient (adoct-jira)
                              │               └▶ ConfluenceClient + DispatcherPage/AdocPublisher (adoct-confluence)
                              └─ prompts: product_owner
IntelliJ-плагин (adoct-idea): McpServerService (старт/стоп) + McpSettingsService/Configurable + IdeaEndpointSupplier
```

- `EndpointSupplier` — шов доступов; реализация в плагине читает `ConfluenceSettingsService`.
- Запуск/остановка — `McpServerService` (`@Service` + `Disposable`), старт на старте IDE через
  `AppLifecycleListener`, off-EDT; перезапуск из настроек.
- Аутентификация Atlassian — **PAT** (`Authorization: Bearer`), цель — **Server/Data Center**.

## Каталог тулов (20) + промпт

**Jira (13):** `jira_get_issue`, `jira_search` (JQL), `jira_get_transitions`, `jira_create_issue`,
`jira_update_issue`, `jira_transition_issue`, `jira_add_comment`, `jira_list_projects`,
`jira_get_current_user`, `jira_list_boards`, `jira_list_sprints`, `jira_get_sprint_issues`,
`jira_get_board_backlog`.

**Confluence (7):** `confluence_get_page`, `confluence_search` (CQL), `confluence_find_page`,
`confluence_get_child_pages`, `confluence_get_user`, `confluence_export_tree_to_adoc` ⭐,
`confluence_publish_adoc` ⭐.

**Prompt:** `product_owner` — персона PO с навыками senior Java.

## Сравнение с проектами на GitHub

| | **adoct-mcp (наш)** | **sooperset/mcp-atlassian** | **atlassian/atlassian-mcp-server (офиц.)** |
|---|---|---|---|
| Язык / рантайм | Java, **внутри IntelliJ-плагина** | Python, отдельный процесс/Docker | Удалённый SaaS (Atlassian) |
| Транспорт | HTTP (свой JSON-RPC, без SDK) | STDIO / SSE / HTTP | Remote HTTP (SSE) |
| Цель | **Server/Data Center** | Cloud **и** Server/DC | Только **Cloud** |
| Аутентификация | PAT (из настроек IDE) | PAT / Basic / OAuth | OAuth 2.1 |
| Кол-во тулов | **20** (фокус на PO) | **~72** (52 Jira + 20 Confluence) | много (Jira/Confluence/JSM/Bitbucket/Compass) |
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
| Jira: доски / спринты / задачи спринта / бэклог | ✅ | ✅ (read) |
| Jira: удаление, batch-создание | ✅ | ❌ |
| Jira: worklog (трудозатраты) | ✅ | ❌ |
| Jira: связи задач / epic-link / remote-link | ✅ | ❌ |
| Jira: watchers | ✅ | ❌ |
| Jira: версии / компоненты проекта | ✅ | ❌ |
| Jira: создать/обновить спринт, добавить в спринт | ✅ | ❌ (только чтение) |
| Jira: поля/опции, changelog, dev-info, SLA, Service Desk, ProForma | ✅ | ❌ |
| Jira: вложения (скачать/картинки) | ✅ | ❌ |
| Confluence: CQL-поиск / страница / дочерние / пользователь | ✅ | ✅ |
| Confluence: найти страницу по space+title | ⚠️ (через CQL) | ✅ |
| Confluence: создать / обновить страницу | ✅ | ⚠️ через `confluence_publish_adoc` (из AsciiDoc) |
| Confluence: удалить / переместить страницу | ✅ | ❌ |
| Confluence: история / diff версий | ✅ | ❌ |
| Confluence: комментарии (читать/добавить/ответить) | ✅ | ❌ |
| Confluence: метки (читать/добавить) | ✅ | ⚠️ при публикации (`:keywords:`) |
| Confluence: вложения (загрузка/скачивание) | ✅ | ⚠️ при экспорте/публикации |
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
