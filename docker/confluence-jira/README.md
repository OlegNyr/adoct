# Локальный стенд Confluence DC + Jira DC

Поднимает Confluence Data Center и Jira Software Data Center на общем PostgreSQL —
чтобы прогонять плагин AsciiDocTools против живого Confluence REST API v1
(pull страниц, аттачи, резолв ссылок, публикация через `PublishDocsToConfluence`).

## Запуск

```bash
cd docker/confluence-jira
docker compose up -d
docker compose logs -f confluence   # ждём "Confluence is ready"
```

Первый старт каждого приложения — несколько минут (распаковка + миграции БД).

| Сервис     | URL                     | Порт |
|------------|-------------------------|------|
| Confluence | http://localhost:8090   | 8090 |
| Jira       | http://localhost:8080   | 8080 |
| PostgreSQL | localhost:5432          | 5432 |

БД/пользователь: `confluence` и `jira`, логин `atlassian` / пароль `atlassian`.

## Настройка после старта

1. Открыть UI, выбрать **Production installation / Data Center**.
2. Параметры БД мастер подставит сам из `ATL_*` переменных — просто подтвердить.
3. Лицензия: бесплатный триал DC на 30 дней — https://my.atlassian.com → **New trial license**
   (можно сгенерировать прямо из мастера по ссылке *Get an evaluation license*).
4. Создать админа и тестовое пространство/проект.

## Подключение плагина

В настройках плагина (Settings → Tools → Confluence, `ConfluenceSettingsConfigurable`):

- **URL**: `http://localhost:8090`
- **Auth**: Personal Access Token (Confluence → Profile → Personal Access Tokens)
  или basic-логин админа.

URL страницы для импорта берётся в формате
`http://localhost:8090/pages/viewpage.action?pageId=<id>`.

## Управление

```bash
docker compose down       # стоп, данные в volume сохраняются
docker compose down -v    # снести вместе с данными
```

## Заметки

- Образы/версии: `atlassian/confluence:8.5` (LTS), `atlassian/jira-software:9.12` (LTS),
  `postgres:15`. Это **Server/DC**, не Cloud — плагин работает с v1 API.
- Память: каждому приложению по умолчанию выдано 1–2 ГБ heap (`JVM_*_MEMORY`).
  Под слабую машину можно поднять только Confluence (закомментировать сервис `jira`).
- Confluence требует `LC_COLLATE/LC_CTYPE = C` на своей БД — задано в init-скрипте.
