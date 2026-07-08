# adoct-maven-plugin

Maven-плагин, публикующий папку (или один `.adoc`) в Confluence. Тонкая обёртка над движком
`ru.gitverse.adoct.generate.AdocPublisher` из модуля `:adoct-confluence` — та же логика, что и публикация
из IDE-плагина (`Alt+Shift+I`) и MCP-тула `confluence_publish_adoc`.

## Цель

| | |
|---|---|
| Координаты | `io.github.olegnyr:adoct-maven-plugin` |
| Goal | `publish` (goal-prefix `adoct` → `mvn adoct:publish`) |

## Параметры

| Параметр | Свойство | Обяз. | По умолчанию | Описание |
|---|---|---|---|---|
| `pageUrl` | `confluence.pageUrl` | да | — | URL целевой (файл) или родительской (папка) страницы. `...?pageId=NNN` или `.../display/SPACE/Title`. |
| `token` | `confluence.token` | да | — | Personal Access Token (Bearer). |
| `source` | `confluence.source` | да | `${project.basedir}/src/docs/asciidoc` | Папка с `.adoc` или один `.adoc`-файл. |
| `serverUrl` | `confluence.serverUrl` | нет | схема+хост из `pageUrl` | Корень REST API. Указывать явно при контекстном пути (`https://host/confluence`). |
| `skip` | `confluence.skip` | нет | `false` | Пропустить публикацию. |
| `failOnError` | `confluence.failOnError` | нет | `true` | Провалить сборку, если при публикации папки `failed > 0`. |

## Использование

В `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.olegnyr</groupId>
      <artifactId>adoct-maven-plugin</artifactId>
      <version>1.0.0</version>
      <configuration>
        <pageUrl>https://confluence.example.com/pages/viewpage.action?pageId=12345</pageUrl>
        <source>${project.basedir}/docs</source>
        <!-- токен не хранить в pom: -Dconfluence.token=... или из окружения/settings.xml -->
      </configuration>
    </plugin>
  </plugins>
</build>
```

Запуск:

```bash
mvn adoct:publish -Dconfluence.token=$CONFLUENCE_PAT
# или полностью: mvn io.github.olegnyr:adoct-maven-plugin:1.0.0:publish -Dconfluence.pageUrl=... -Dconfluence.token=...
```

Для короткой формы `mvn adoct:publish` добавьте группу в `~/.m2/settings.xml`:

```xml
<pluginGroups>
  <pluginGroup>io.github.olegnyr</pluginGroup>
</pluginGroups>
```

## Конвенции публикации папки

Наследуются от `AdocPublisher` (см. его Javadoc):

- `index.adoc` каждой папки — страница-узел; остальные файлы папки — её дочерние страницы; `index.adoc`
  подпапки — ребёнок `index.adoc` родителя. Корневой `index.adoc` = страница из `pageUrl`.
- `:confluency-id:` — номер (или URL) существующей страницы; дописывается автоматически после создания.
  `:confluency-id: ignore` — файл пропускается.
- `:keywords:` (через запятую) → метки страницы. `:imagesdir:` — база для картинок-вложений.
- Тело и вложения заливаются только при изменении (sha256 в content-property).

## Установка

Публикуются **два** артефакта: сам плагин и движок `io.github.olegnyr:adoct-confluence` (транзитивная
зависимость; остальные транзитивы — asciidoctorj и пр. — из Maven Central).

### Локально (для тестов)

```bash
./gradlew :adoct-confluence:publishToMavenLocal :adoct-maven-plugin:publishToMavenLocal
```

### Maven Central (Central Portal)

Публикация настроена плагином `com.vanniktech.maven.publish` (собирает sources/javadoc, подписывает GPG,
грузит бандл в Central Portal). Разовая подготовка (делается вручную, вне сборки):

1. **Аккаунт и namespace.** Зарегистрироваться на https://central.sonatype.com, добавить namespace
   `io.github.olegnyr` и подтвердить его через GitHub (портал даёт код → создать публичный репозиторий
   с этим именем). Сгенерировать в портале **user token** (username/password).
2. **GPG-ключ.** `gpg --gen-key`, затем выложить публичный ключ на keyserver
   (`gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`). Экспортировать секретный ключ для Gradle:
   `gpg --export-secret-keys --armor <KEYID>`.
3. **Креды в `~/.gradle/gradle.properties`** (не в репозиторий):
   ```properties
   mavenCentralUsername=<portal-token-username>
   mavenCentralPassword=<portal-token-password>
   signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END...
   signingInMemoryKeyPassword=<passphrase>
   ```
   В CI те же значения — через переменные `ORG_GRADLE_PROJECT_*`.

Публикация релиза (Central не принимает `-SNAPSHOT`). Флаг `--no-configuration-cache` обязателен —
remote-publish задачи Gradle/Vanniktech не сериализуются в configuration cache (включён в `gradle.properties`):

```bash
./gradlew :adoct-confluence:publishToMavenCentral :adoct-maven-plugin:publishToMavenCentral \
  -Drelease=true --no-configuration-cache
```

> Плагин `com.vanniktech.maven.publish` объявлен в корневом `build.gradle.kts` с `apply false` — иначе его
> общий build service (`SonatypeRepositoryBuildService`) конфликтует при применении в двух модулях-соседях.

Бандл появится в Central Portal как «staged»; по умолчанию его нужно подтвердить кнопкой **Publish**
в UI портала (для авто-релиза — `publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)`).
После релиза артефакт доступен на Central через ~15–30 минут.

### Корпоративный Nexus/Artifactory (альтернатива)

Плагин `maven-publish`/Vanniktech умеет и в приватный репозиторий — задать `repositories { maven { url } }`
и креды из окружения, публиковать `./gradlew publish`.

## Требования и особенности сборки

- **Рантайм: JDK 21+** — движок `:adoct-confluence` собран под Java 21, поэтому Maven, запускающий плагин,
  должен работать на JDK 21 или новее.
- Плагин `de.benediktritter.maven-plugin-development` (0.4.3) несёт maven-plugin-tools 3.6 со старыми
  ASM/QDox, не понимающими Java 21 (`major 65`, `record`). В `build.gradle.kts` на его classpath
  форсятся `asm:9.7` и `qdox:2.1.0` — иначе генерация `plugin.xml` падает на классах/исходниках движка.
- Задачи генерации дескриптора/help-mojo несовместимы с Gradle configuration cache; они помечены
  `notCompatibleWithConfigurationCache`, поэтому прогоны, включающие этот модуль (например, полный
  `./gradlew build`), кэш конфигурации не сохраняют. Путь релиза IDE-плагина (`:adoct-idea:buildPlugin`)
  этот модуль не затрагивает — там кэш работает как прежде.
