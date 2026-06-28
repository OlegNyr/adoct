---
apply: always
---

# Правила разработки

**Стек:** Java 21 · Spring Boot 4 · Jackson 3 · Lombok (`@SuperBuilder`) · MapStruct
**Архитектура:** Слоистая (Layered) · Package-by-Feature
**Методология:** SOLID · TDD

---

## 1. Технологический стек

### 1.1. Java 21

Используем возможности современной Java:

- **Records** — для DTO, value objects, неизменяемых структур данных.
- **Sealed classes/interfaces** — для закрытой иерархии типов (например, доменные события, результаты операций).
- **Pattern matching** (`switch`, `instanceof`) — вместо цепочек `if-else` и ручных кастов.
- **Text blocks** (`"""`) — для многострочных SQL, JSON, шаблонов.
- **Virtual threads** (`Thread.ofVirtual()`, `Executors.newVirtualThreadPerTaskExecutor()`) — для I/O-bound задач.
- **`var`** — только когда тип очевиден из правой части (`var user = userRepository.findById(id)`). Не использовать, если ухудшает читаемость.
- Запрещено использование deprecated API и `sun.*` / `com.sun.*` пакетов.

### 1.2. Spring Boot 4

- Конфигурация через `application.yml` (не `.properties`).
- Профили: `dev`, `test`, `prod`. Никаких секретов в репозитории — только через переменные окружения или Vault.
- Бины — только через конструктор (`@RequiredArgsConstructor` от Lombok). Никаких `@Autowired` на полях или сеттерах.
- `@ConfigurationProperties` вместо `@Value` для групп настроек.
- Использовать `ProblemDetail` (RFC 7807) для HTTP-ошибок через `@RestControllerAdvice`.
- Observability: Micrometer + OpenTelemetry включены по умолчанию.

**Миграция с Boot 3 — критичные изменения:**

- **Стартеры переименованы**: `spring-boot-starter-web` → `spring-boot-starter-webmvc`; `spring-boot-starter-aop` → `spring-boot-starter-aspectj`. Каждому стартеру соответствует тестовый (`spring-boot-starter-webmvc-test`).
- **Пакеты тестовых slice-аннотаций**: `org.springframework.boot.test.autoconfigure.web.servlet.*` → `org.springframework.boot.test.autoconfigure.webmvc.*`. Например, `@WebMvcTest` теперь импортируется из `org.springframework.boot.test.autoconfigure.webmvc.WebMvcTest`.
- **`@MockBean` / `@SpyBean` удалены**: использовать `@MockitoBean` / `@MockitoSpyBean` из `org.springframework.test.context.bean.override.mockito`.
- **`@SpringBootTest` больше не настраивает MockMvc автоматически**: нужно явно добавлять `@AutoConfigureMockMvc`.
- **Jackson**: `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`; `@JsonComponent` → `@JacksonComponent`; `@JsonMixin` → `@JacksonMixin`; свойства `spring.jackson.*` → `spring.jackson.json.*`.
- **Jakarta EE 11 / Servlet 6.1** — базовый уровень.
- **Переехавшие пакеты авто-конфигов** (часто ловят в тестах):
  - `LiquibaseAutoConfiguration` → `org.springframework.boot.liquibase.autoconfigure` (раньше `org.springframework.boot.autoconfigure.liquibase`).
  - `AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure` (раньше `org.springframework.boot.test.autoconfigure.jdbc`).

### 1.3. Jackson 3

- **Пакеты**: `tools.jackson.*` вместо `com.fasterxml.jackson.*` — **кроме аннотаций** (см. ниже).
- **Исключение — `jackson-annotations`**: модуль аннотаций не переезжал; в Jackson 3 по-прежнему используется версия 2.x.
  Импорты аннотаций остаются `com.fasterxml.jackson.annotation.*`:
  `@JsonProperty`, `@JsonAlias`, `@JsonIgnore`, `@JsonInclude`, `@JsonCreator`, `@JsonValue`, `@JsonAnyGetter`, `@JsonAnySetter` и др.
- **Аннотации из `jackson-databind`** переехали: `@JsonSerialize`, `@JsonDeserialize` → `tools.jackson.databind.annotation.*`.
- `ObjectMapper` конфигурируется один раз как бин через `JsonMapper.builder()` (`tools.jackson.databind.json.JsonMapper`).
- Для дат — `java.time.*` сериализуется в ISO-8601 **без дополнительных настроек**: в Jackson 3 поддержка встроена, `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS` выключен по умолчанию. `JavaTimeModule` регистрировать не нужно.
- Для `java.util.Date` формат задан глобально в `ObjectMapperBuilder`: `yyyy-MM-dd'T'HH:mm:ss.SSSZZ`, часовой пояс UTC.
- **Null-поля не сериализуются** — глобально настроен `NON_NULL` через `changeDefaultPropertyInclusion()` в `ObjectMapperBuilder`. На DTO не нужно ставить `@JsonInclude(NON_NULL)` повторно.
- Для неизвестных полей — `FAIL_ON_UNKNOWN_PROPERTIES = false` на входе, строгая схема на выходе.
- DTO — классы с `@Value` + `@Builder` от Lombok. Если DTO участвует в наследовании — `@SuperBuilder` вместо `@Builder`. Java `record` не использовать для DTO.
- Неймнинг — `PropertyNamingStrategies.SNAKE_CASE` при интеграции с внешними API, если не согласовано иное.

### 1.4. Lombok

Разрешённые аннотации:

- `@Getter`, `@RequiredArgsConstructor`, `@Slf4j` — основные рабочие лошадки.
- `@SuperBuilder` (`lombok.experimental.SuperBuilder`) — **обязателен** для всех entity, domain-моделей и DTO, участвующих в наследовании. Не использовать `@Builder`, если класс может быть расширен.
- `@NonFinal` (`lombok.experimental.NonFinal`) — снимает `final` с класса, поставленный `@Value`, разрешая наследование DTO.
- `@Value` + `@Builder` — стандартная пара для DTO и value objects. `@Value` делает класс неизменяемым (все поля `private final`), `@Builder` даёт удобную сборку.
- `@EqualsAndHashCode(callSuper = true)` — всегда явно указывать `callSuper` при наследовании.

Запрещено:

- `@Data` — скрывает поведение, генерирует небезопасные `equals/hashCode` для JPA-сущностей.
- `@AllArgsConstructor` на публичных классах — делает API хрупким.
- `@SneakyThrows` — глотает контракт исключений.
- `@Setter` на уровне класса — нарушает инкапсуляцию.

#### Пример использования `@SuperBuilder` — domain/entity

```java
@Getter
@SuperBuilder
@EqualsAndHashCode
public abstract class BaseEntity {
    private final UUID id;
    private final Instant createdAt;
}

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {
    private final String email;
    private final String displayName;
}

// Использование:
var user = User.builder()
    .id(UUID.randomUUID())
    .createdAt(Instant.now())
    .email("user@example.com")
    .displayName("John")
    .build();
```

#### Пример использования `@SuperBuilder` — наследуемые DTO

Для DTO, которые могут расширяться, используется комбинация `@Value @NonFinal @SuperBuilder`.
`@NonFinal` снимает `final` с класса (который `@Value` ставит по умолчанию), разрешая наследование.
`@EqualsAndHashCode(callSuper = true)` обязателен у дочернего класса.
Дополнительные аннотации Jackson (`@JsonDeserialize`, `@JsonPOJOBuilder`) не нужны —
`JacksonAnnotationSuperBuilderIntrospector` обнаруживает билдер автоматически.

```java
// Родительский DTO — @NonFinal разрешает наследование
@Value
@NonFinal
@SuperBuilder
public class BaseDto {
    String baseField;
}

// Дочерний DTO — явно указываем callSuper = true
@Value
@NonFinal
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
class DerivedDto extends BaseDto {
    String derivedField;
}

// Десериализация из JSON без дополнительных аннотаций:
// {"baseField":"parent-value","derivedField":"child-value"}
// → mapper.readValue(json, DerivedDto.class)
```

### 1.5. MapStruct

- Все маппинги между слоями (entity ↔ domain ↔ DTO) — через MapStruct. **Никакого ручного копирования полей.**
- `@Mapper(componentModel = "spring")` — для интеграции со Spring DI.
- Один маппер на одну пару «источник → цель», либо один маппер на feature-пакет.
- `unmappedTargetPolicy = ReportingPolicy.ERROR` — чтобы забытые поля падали на этапе компиляции.
- Сложную логику маппинга выносить в `default`-методы маппера или в `@Named`-методы, но не в сервисный слой.

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {
    UserDto toDto(User domain);
    User toDomain(UserEntity entity);
    UserEntity toEntity(User domain);
}
```

### 1.6. MyBatis

Слой доступа к БД. XML-мапперы в `src/main/resources/mapper/**/*.xml`, интерфейсы с `@Mapper` — рядом с Row-классом (package-by-feature).

**Конфигурация в `application.yml`:**

```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: ru.otpbank.kc.acd.database
  type-handlers-package: ru.otpbank.kc.acd.database.typehandler
  configuration:
    map-underscore-to-camel-case: true
```

**Row-классы** — иммутабельные `@Value @Builder` (для наследования — `@SuperBuilder + @NonFinal`).

**Result-mapping — только позиционный constructor-based:**

- `@Value` делает Lombok-генерируемый all-args конструктор, но **параметры без имён в bytecode** (Lombok даже с `-parameters` их не сохраняет). Поэтому `<arg name="...">` не работает — используем `<arg>` без `name`, и **порядок `<arg>` строго соответствует порядку полей в Row-классе**.
- Для primitive boolean — `javaType="_boolean"` (MyBatis-алиас). Для обёртки — `java.lang.Boolean`.

```xml
<resultMap id="userRowResult" type="ru.otpbank.kc.acd.database.user.UserRow">
  <constructor>
    <idArg column="user_id" javaType="ru.otpbank.kc.acd.dto.UserId"/>
    <arg column="creation_ts" javaType="java.time.LocalDateTime"/>
    <arg column="admin" javaType="_boolean"/>
    <!-- порядок <arg> == порядок полей в Lombok-конструкторе -->
  </constructor>
</resultMap>
```

**Value objects (`UserId`, `DeviceId` и т.п.) через `TypeHandler`:**

- Класс расширяет `BaseTypeHandler<T>`, аннотирован `@MappedTypes(T.class)`, лежит в пакете из `mybatis.type-handlers-package` — регистрация автоматическая.
- В XML — просто `javaType="ru.otpbank.kc.acd.dto.UserId"`, MyBatis сам находит handler.

```java
@MappedTypes(UserId.class)
public class UserIdTypeHandler extends BaseTypeHandler<UserId> {
    @Override public void setNonNullParameter(PreparedStatement ps, int i, UserId v, JdbcType t) throws SQLException {
        ps.setString(i, v.getValue());
    }
    @Override public UserId getNullableResult(ResultSet rs, String col) throws SQLException {
        var s = rs.getString(col); return s == null ? null : UserId.of(s);
    }
    // ...остальные get-методы аналогично
}
```

**`@MapperScan` — в `@AutoConfiguration`-классе модуля**, не на `@SpringBootApplication`. Сам автоконфиг регистрируется через `AutoConfiguration.imports` (см. §2.3).

```java
@AutoConfiguration
@MapperScan(basePackageClasses = DatabaseAutoConfiguration.class)
public class DatabaseAutoConfiguration {}
```

**`@SuperBuilder` с `<constructor>` НЕ работает** — Lombok не генерирует публичный all-args конструктор при `@SuperBuilder` (только builder-based). Для Row-классов, которые не наследуются — использовать `@Builder`, не `@SuperBuilder`.

---

## 2. Архитектура

### 2.1. Слоистая архитектура

Четыре слоя, зависимости направлены строго сверху вниз:

```
┌─────────────────────────────────────────┐
│  Web layer (controllers, DTOs)          │  ← принимает HTTP, валидирует вход
├─────────────────────────────────────────┤
│  Application layer (services, use-cases)│  ← оркестрация, транзакции
├─────────────────────────────────────────┤
│  Domain layer (модели, бизнес-правила)  │  ← чистая логика, без фреймворков
├─────────────────────────────────────────┤
│  Infrastructure (repositories, clients) │  ← JPA, HTTP-клиенты, брокеры
└─────────────────────────────────────────┘
```

**Правила зависимостей:**

- Web → Application → Domain ← Infrastructure.
- Domain **не зависит ни от чего**, кроме стандартной библиотеки Java и Lombok. Никаких `@Entity`, `@Component`, `@JsonProperty` в domain.
- Infrastructure реализует интерфейсы, объявленные в Domain (порты и адаптеры).
- Controllers не знают про entity. Services не знают про DTO (возвращают domain-модели, контроллер сам вызывает маппер).

### 2.2. Package-by-Feature

**Не по слоям, а по фиче.** Всё, что нужно для работы одной фичи, лежит в одном пакете.

```
com.company.app
├── user                              ← feature-пакет
│   ├── UserController.java           (public)
│   ├── UserService.java              (public — API фичи)
│   ├── UserMapper.java               (package-private)
│   ├── UserRepository.java           (package-private)
│   ├── domain
│   │   └── User.java
│   ├── dto
│   │   ├── UserDto.java              (public — часть API)
│   │   └── CreateUserRequest.java    (public)
│   └── persistence
│       └── UserEntity.java           (package-private)
├── order
│   └── ...
└── shared                            ← только то, что действительно общее
    └── ...
```

**Ключевое правило: минимизировать выход за пределы пакета фичи.**

- Классы по умолчанию **package-private**. `public` — только то, что реально нужно снаружи (контроллер, публичный сервис, DTO).
- Если фиче `order` нужны данные из `user` — она обращается **только к `UserService`** (публичному API фичи), не к `UserRepository` или `UserEntity`.
- Межфичевое взаимодействие — через публичные сервисы, события (`ApplicationEventPublisher`) или отдельный интеграционный слой.
- Если видно, что две фичи плотно переплетены и постоянно зовут друг друга — это сигнал пересмотреть границы или объединить их.

### 2.3. Модульные автоконфигурации

Когда проект разбит на Maven-модули, каждый бизнес-модуль, экспортирующий Spring-бины, делает это через **настоящий auto-config**, а не через `@Import` на `@SpringBootApplication`.

```
acd-client/
└── src/main/
    ├── java/…/client/ClientAutoConfiguration.java
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Содержимое `AutoConfiguration.imports` — одна строка (FQN автоконфига):

```
ru.otpbank.kc.acd.client.ClientAutoConfiguration
```

Сам класс:

```java
@AutoConfiguration
@ComponentScan
public class ClientAutoConfiguration {}
```

**Почему не `@Import(ClientAutoConfiguration.class)` на `AcdApplication`:**

- `@Import` — безусловная Spring-Core механика, класс подгружается **в любом контексте**, включая slice-тесты (`@WebMvcTest`, `@MybatisTest`).
- `@WebMvcTest(...)` отключает автоконфиги через `excludeAutoConfiguration`, но **не может отключить классы, подгруженные через `@Import`** — и если у них `@ComponentScan` с зависимостями, slice-тест падает на отсутствии бинов (типовой пример: `@WebMvcTest` не может найти `UserMapper`, потому что `@Import(ClientAutoConfiguration)` → `@ComponentScan` → `RegisterServiceImpl`, которому нужен мапер из БД-слоя).
- Авто-конфиг, зарегистрированный через `AutoConfiguration.imports`, в slice-тестах автоматически отфильтровывается — `@WebMvcTest` подтягивает только web-слой.

**Исключение** — конфиги, специфичные для boot-модуля (например, `SecurityConfiguration`, `ToolsConfiguration`), могут остаться обычными `@Configuration` в пакете `@SpringBootApplication` — их и не должно фильтровать.

### 2.4. Liquibase и миграции БД

- Changelogs: `src/main/resources/db/changelog/`. Точка входа `db.changelog-master.xml` → `v{N}/index.xml` → отдельные файлы миграций.
- Имена файлов: `YYYYMMDD_NNN_описание.xml`. **Интервалы между номерами** — `010, 020, 022, 025, 030` — чтобы вставить новую миграцию между существующими без переименования всего.
- **Даты/метки времени — всегда `timestamp`**, не `bigint` с epoch-ms. В Java — `LocalDateTime` (MyBatis handles natively).
- **Primitive `boolean` в Row-классе → `NOT NULL DEFAULT false`** в DDL. Lombok всегда сериализует `false`, а `DEFAULT` в Postgres применяется только когда столбец отсутствует в INSERT (MyBatis всегда пишет явно), поэтому полагаться на `DEFAULT` в MyBatis-INSERT нельзя — нужен `NOT NULL`.
- **Wrapper-типы (`Boolean`, `Long`, `String`)** — nullable-столбец без `NOT NULL`; `NULL` проходит через MyBatis как есть.
- **Foreign keys** — отдельным элементом `<addForeignKeyConstraint>` после `<createTable>`, не inline в `<column>`. Упрощает переиспользование и чтение.
- **Value objects в Liquibase** — только `text` (для `UserId`/`DeviceId`); конвертацией занимается MyBatis `TypeHandler`, в БД хранится raw-строка.

---

## 3. SOLID

- **S — Single Responsibility.** Один класс — одна причина для изменения. Если в имени класса есть «And» или «Manager» — это запах.
- **O — Open/Closed.** Для расширения поведения — новая реализация интерфейса или sealed-иерархия, а не правки существующего класса.
- **L — Liskov Substitution.** Наследник не усиливает предусловия и не ослабляет постусловия. Если переопределённый метод бросает новое исключение или возвращает `null` там, где родитель не возвращал — это нарушение.
- **I — Interface Segregation.** Много узких интерфейсов лучше одного «толстого». Клиент не должен зависеть от методов, которые не использует.
- **D — Dependency Inversion.** Сервисы зависят от интерфейсов, не от реализаций. Интерфейсы объявляются в слое, который их использует (в domain), а реализации — в infrastructure.

---

## 4. TDD

### 4.1. Цикл Red → Green → Refactor

1. **Red** — написать падающий тест, описывающий новое поведение.
2. **Green** — написать минимальный код, чтобы тест прошёл. Не больше.
3. **Refactor** — улучшить структуру, не меняя поведения. Все тесты должны остаться зелёными.

Никакого продакшн-кода без предшествующего падающего теста.

### 4.2. Пирамида тестов

- **Unit (большинство)** — JUnit 5 + AssertJ + Mockito. Тестируется один класс, зависимости замокированы. Быстро, изолированно.
- **Integration (меньше)** — `@SpringBootTest` с нужными слайсами (`@DataJpaTest`, `@WebMvcTest`, `@MybatisTest`). Embedded PostgreSQL через `zonky/embedded-database-spring-test` (без Docker).
- **E2E (единицы)** — полный стек, только для критичных пользовательских сценариев.

**Рецепты slice-тестов:**

`@MybatisTest` — мапер + реальная PG + Liquibase:

```java
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(type = DatabaseType.POSTGRES, provider = DatabaseProvider.ZONKY)
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
class UserMapperTest {
    @Autowired UserMapper userMapper;
    // ...
}
```

В модуле, содержащем мапер-тесты, обязателен пустой `@SpringBootApplication` в `src/test/java/` — иначе slice-тесты не найдут `@SpringBootConfiguration`:

```java
// src/test/java/.../DatabaseTestApplication.java
@SpringBootApplication
class DatabaseTestApplication {}
```

`@WebMvcTest` — контроллер + security + мок сервиса:

```java
@WebMvcTest(WhoamiController.class)
@Import({WhoamiController.class, SecurityConfiguration.class, TestJwtDecoderConfiguration.class})
class SecurityConfigurationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean WhoamiService whoamiService;
    // ...
}
```

**Тонкость**: если контроллер находится **вне** базового пакета `@SpringBootApplication` — его нужно **явно добавить в `@Import`** теста, иначе `@WebMvcTest` не найдёт его и вернёт 404.

**Сервисный unit-тест** — без Spring-контекста, `@ExtendWith(MockitoExtension.class)`:

```java
@ExtendWith(MockitoExtension.class)
class RegisterServiceImplTest {
    @Mock UserMapper userMapper;
    RegisterServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RegisterServiceImpl(userMapper, fixedClock);
        ReflectionTestUtils.setField(service, "homeServerName", "otpbank.ru");
    }
    // ...
}
```

Для проверяемости по времени — всегда инжектить `Clock` (бин `Clock.systemUTC()`), никаких `LocalDateTime.now()` напрямую.

### 4.3. Требования к тестам

- Именование: `methodName_shouldDoSomething_whenCondition` или `given_when_then` — выбрать один стиль и держаться его.
- Структура AAA: **Arrange, Act, Assert** — с пустыми строками между блоками.
- Один тест — одно утверждение о поведении. Не пять `assertEquals` подряд про разные вещи.
- AssertJ вместо JUnit-ассертов: `assertThat(user.getEmail()).isEqualTo("...")`.
- Тесты не должны зависеть друг от друга и от порядка выполнения.
- Покрытие — не самоцель. Цель — каждый бизнес-сценарий и каждая ветка логики покрыты осмысленным тестом.

```java
@Test
void shouldRejectRegistration_whenEmailAlreadyExists() {
    // Arrange
    var existing = User.builder().email("taken@example.com").build();
    when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

    // Act
    var result = catchThrowable(() -> userService.register("taken@example.com", "name"));

    // Assert
    assertThat(result)
        .isInstanceOf(EmailAlreadyTakenException.class)
        .hasMessageContaining("taken@example.com");
}
```

---

## 5. Общие требования к коду

- **Именование.** Классы — существительные (`OrderValidator`), методы — глаголы (`validateOrder`). Без сокращений (`usr`, `mgr`) кроме общепринятых (`id`, `url`).
- **Null.** `Optional` для возвращаемых значений, которых может не быть. Поля и параметры — через `@Nullable` / `@NonNull` (JSpecify), если nullable неочевиден.
- **Immutability по умолчанию.** Коллекции возвращаем как `List.copyOf(...)`. Поля — `final`, если нет веской причины.
- **Исключения.** Бизнес-исключения — свои, наследующиеся от общего `DomainException`. Не ловить `Exception` без крайней необходимости, никогда не ловить `Throwable`.
- **Логирование.** SLF4J через `@Slf4j`. Не логировать PII и секреты. Уровни: `ERROR` — требует реакции, `WARN` — аномалия, `INFO` — значимое бизнес-событие, `DEBUG` — детали для отладки.
- **Форматирование.** Google Java Style или принятый командой — настроен через `.editorconfig` + Spotless в CI. Ломает сборку, если не отформатировано.
- **Комментарии.** Объясняют «почему», а не «что». «Что» должно быть видно из кода.
- **Статический анализ.** Checkstyle + ErrorProne + SpotBugs в CI. Предупреждения — ошибки сборки.

---

## 6. Definition of Done

Задача считается выполненной, когда:

1. Поведение покрыто тестами (TDD-цикл пройден).
2. Все тесты зелёные, покрытие не упало.
3. Статический анализ не ругается.
4. Код прошёл code review хотя бы одного коллеги.
5. Документация (Javadoc для публичного API, README фичи) обновлена.
6. Нет `TODO` / `FIXME` без привязанного тикета.
