# adoct-mcp-jar

Самодостаточный исполняемый **JAR** MCP-сервера AsciiDocTools — запуск одной командой
`java -jar adoct-mcp.jar`, без установки IDE-плагина и без native-сборки.

В отличие от native-бинаря (`:adoct-mcp-cli`) fat-JAR содержит **весь** набор тулов, включая
`confluence_publish_adoc` (публикация `.adoc` в Confluence, требует asciidoctorj/JRuby).

## Сборка

```bash
./gradlew :adoct-mcp-jar:shadowJar
# → adoct-mcp-jar/build/distributions/… нет; JAR тут:
#   adoct-mcp-jar/build/libs/adoct-mcp-<версия>.jar
```

Требуется **JDK 21+** для запуска.

## Запуск

```bash
# stdio (по умолчанию) — для MCP-клиента
java -jar adoct-mcp.jar

# HTTP — сервер на http://127.0.0.1:7337/mcp
java -jar adoct-mcp.jar --http
# порт/хост: --port 7337  --bind 127.0.0.1
```

## Настройка (JSON-конфиг)

Полная конфигурация — JSON-файл, путь передаётся через `--config <path>` или переменную окружения
`MCP_CONFIG`. Формат:

```json
{
  "transport": "stdio",
  "defaultJiraProject": "ABC",
  "defaultConfluenceSpace": "DOC",
  "endpoints": [
    { "host": "https://jira.example.com",       "kind": "jira",       "token": "${JIRA_TOKEN}",       "default": true },
    { "host": "https://confluence.example.com", "kind": "confluence", "token": "${CONFLUENCE_TOKEN}" },
    { "host": "https://bitbucket.example.com",  "kind": "bitbucket",  "token": "${BITBUCKET_TOKEN}" }
  ],
  "team": [
    { "username": "ivanov", "displayName": "Иван Иванов",    "role": "Backend" },
    { "username": "petrov", "displayName": "Пётр Петров",    "role": "QA" },
    { "username": "sidorova", "displayName": "Анна Сидорова", "role": "Analyst" }
  ]
}
```

- `kind` — `jira` | `confluence` | `bitbucket`. Можно опустить: тип определяется по хосту
  (`jira`/`bitbucket`/`stash` в адресе), иначе — `confluence`.
- `default: true` — основной хост своего типа (на него уходят вызовы без явного `host`).
- **`token` и `host` поддерживают `${ENV_VAR}`** — значение берётся из переменной окружения, а не
  хранится в файле. Несуществующая переменная → пустая строка.

### Команда (ростер)

Массив `team` (см. в примере конфига выше) — список сотрудников команды; MCP-тулы используют его,
чтобы сопоставлять исполнителей и роли (кому назначать задачи, кто есть в команде). Поля элемента:

- `username` — логин в Jira/Confluence (обязателен; пустые записи пропускаются).
- `displayName` — отображаемое имя.
- `role` — роль в команде (например `Backend`, `QA`, `Analyst`).

### Токены из переменных окружения

Так секреты не лежат в JSON. MCP-клиент запускает процесс и передаёт токены через блок `env`, а
конфиг ссылается на них через `${...}`:

```json
{
  "mcpServers": {
    "adoct": {
      "command": "java",
      "args": ["-jar", "/absolute/path/adoct-mcp.jar", "--config", "/absolute/path/adoct-mcp.json"],
      "env": {
        "JIRA_TOKEN": "первый-PAT",
        "CONFLUENCE_TOKEN": "второй-PAT",
        "BITBUCKET_TOKEN": "третий-PAT"
      }
    }
  }
}
```

(`mcpServers` — формат конфига MCP-клиентов, например Claude Desktop: `claude_desktop_config.json`.)

### Быстрый режим — один хост целиком из окружения

Без JSON-файла: одиночный эндпоинт задаётся переменными окружения (переопределяет `endpoints`):

```json
{
  "mcpServers": {
    "adoct": {
      "command": "java",
      "args": ["-jar", "/absolute/path/adoct-mcp.jar"],
      "env": {
        "MCP_HOST": "https://jira.example.com",
        "MCP_TOKEN": "ваш-PAT",
        "MCP_KIND": "jira"
      }
    }
  }
}
```

## Переменные окружения

| Переменная | Назначение |
|---|---|
| `MCP_CONFIG` | путь к JSON-конфигу (равноценно `--config`) |
| `MCP_HOST` / `MCP_TOKEN` / `MCP_KIND` | быстрый одиночный эндпоинт (переопределяет `endpoints`) |
| `MCP_TRANSPORT` | `stdio` (по умолчанию) или `http` |
| `MCP_PORT` / `MCP_BIND` | порт/адрес для HTTP-режима (по умолчанию `7337` / `127.0.0.1`) |
| `MCP_JIRA_PROJECT` / `MCP_CONFLUENCE_SPACE` | проект/пространство по умолчанию |

Плюс любые `${VAR}`, на которые ссылается JSON-конфиг (например `JIRA_TOKEN` выше).

Приоритет источников (по возрастанию): JSON-файл → переменные `MCP_*` → аргументы командной строки.
