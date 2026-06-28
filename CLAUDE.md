# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AsciiDocTools** — an IntelliJ Platform plugin that converts Confluence content (HTML "storage" format) into AsciiDoc and publishes AsciiDoc back to Confluence. It can pull pages live via the Confluence REST API (recursively exporting a page subtree) or convert an offline HTML export, downloading attachments and resolving internal links along the way.

## Build & run

A **Gradle multi-module** build; the IntelliJ Platform Gradle Plugin (2.x) is applied only to the plugin module. JVM toolchain is **21**. Run tasks from the repo root.

Modules (Gradle subprojects):
- `:adoct-confluence` — the **conversion engine** (`parser` = Confluence→AsciiDoc, `generate` = AsciiDoc→Confluence). Pure `java-library`, no IntelliJ dependency.
- `:adoct-jira` — Jira integration (`JiraClient`). Pure `java-library`.
- `:adoct-anonymize` — export anonymizer + bug-report bundling (`anonymize`, `bugreport`). Pure `java-library`, independent of the engine.
- `:adoct-idea` — the **IntelliJ plugin** (`plugins.idea.*`, `plugin.xml`, message bundles). Applies `org.jetbrains.intellij.platform`; depends on the three library modules and bundles them into the distribution.

Common commands:
- `./gradlew :adoct-idea:runIde` — launch a sandbox IDE (IntelliJ IDEA Community 2025.1.3) with the plugin installed. Primary way to exercise it manually (`Alt+I` file importer, `Alt+Shift+I` Confluence export).
- `./gradlew :adoct-idea:buildPlugin` — assemble the plugin zip into `adoct-idea/build/distributions/`.
- `./gradlew build` — compile + test + assemble every module.
- `./gradlew test` — run the JUnit suites of all modules; `./gradlew :adoct-confluence:test` for one module.
- `./gradlew :adoct-confluence:test --tests "ru.gitverse.adoct.parser.golden.MacrosParserTest"` — run a single test class.
- `-Drelease=true` system property strips the `-SNAPSHOT` suffix from the version.

Dependencies resolve from public repositories only: Maven Central + the Gradle Plugin Portal for plugins, and the JetBrains IntelliJ Platform repositories (`intellijPlatform.defaultRepositories()`) for the platform and bundled plugins. See `settings.gradle.kts`. Note: `jsoup`, `jackson`, `commons-lang3`/`commons-io` and Apache `httpclient` — once provided transitively by the IntelliJ Platform — are now declared explicitly in `gradle/libs.versions.toml` and owned by the library modules (they are used internally and never cross the plugin classloader boundary, so bundling our own copies is safe). No credentials or private mirrors are required.

## Architecture

The engine (`:adoct-confluence`) is fully decoupled from IntelliJ APIs so it can be unit-tested standalone; the plugin module (`:adoct-idea`) is glue that wires settings → client → converter behind a `ProgressIndicator`. Entry points in `plugins.idea.*`: tool windows, the `Alt+I`/`Alt+Shift+I` actions, a `Configurable` for Confluence server settings, the app-level `@Service`s `ConvertDocsUrlToAdoc` (export) and `PublishDocsToConfluence` (publish).

### Conversion flow (export)

`DispatcherPage.generate(pageId, progress)` orchestrates a live Confluence pull, recursing into the child-page tree (each page lands in its own subfolder `<parent>/<child title>/`):
1. `ConfluenceClient` fetches the page (`getMainPage`) and downloads attachments into `<dest>/attache/` (skipped when `includeAttachments` is off).
2. Page id is resolved from the URL — both `…?pageId=NNN` and the "human" `/display/SPACE/Title` form (resolved to an id via `findPageId`). The same resolution backs `:confluency-id:` values in `.adoc` (number, or a URL that is resolved and cached back to a number).
3. `ConvertStorageToAdoc.convert(metadata, attachmentDir)` produces the AsciiDoc; the page id is written into the header as `:confluency-id:` so publish can round-trip.
4. Raw inputs under `<dest>/source/` (`body.storage.html`, `view.storage.html`, `content.json`, `links.json` — `links.json` is also a resolved-link **cache**) are written **only in debug mode**. Empty `attache/` and `files/` folders are removed.

### The parser engine (the core)

`ConvertStorageToAdoc.convert` parses the storage HTML with **Jsoup**, builds an intermediate **AST** (`ast/Block`, `ast/Inline`) via `AstBuilder`, and serializes it with `AsciiDocWriter` (which owns blank-line discipline and table layout — no string post-processors). The builders form two registries:

- `BlockBuilder` (`build/`) — a `tag name → [NodeTag]` registry (`build/tag/`). Each `NodeTag` declares the `names()` it handles; the first matching handler builds `Block`s for an element. `InlineBuilder` does the same for inline content; `ImageRenderer`/`LinkRenderer` handle images and links.
- `MacroBuilder` (`build/`) — a `macro name → NodeMacro` registry (`build/macro/`, e.g. `CodeMacro`, `PlantumlMacro`, `DrawioMacro`, `ExpandMacro`, `NoteMacro` (note/info/tip/warning), `JiraMacro`, `TocMacro`, `TabsMacro`). Unknown macros are logged and dropped.
- `BuildContext` threads per-run state (metadata map keyed by `MetadataKey`, heading level, color-export flag) through the build.
- **Splitting**: documents over 700 lines are split into multiple files by `SpliteratorAdoc` on `==` (level-2) headings; otherwise a single `index.adoc` is written.

`MetadataKey` is the enum of well-known context keys (title, page id, URL, attachment/image/files folders, resolved links, color flag).

### To add support for a new element or macro

- New HTML tag → implement `NodeTag` in `parser/build/tag/` and register it in `BlockBuilder`.
- New Confluence macro → implement `NodeMacro` (extend `AbstractNodeMacro`) in `parser/build/macro/` and add it to the handler list in `MacroBuilder`.
Order matters in each registry: the first matching handler wins.

## Code style

The repo ships AI coding rules in `.gigacode/rules/` (`CODESTYLE.md`, `JAVAPRACTIC.md`, `PRD.md`) marked `apply: always`. Highlights worth honoring:
- **2-space indentation**, 120-col lines, no wildcard imports, K&R braces, always braces on control statements.
- Effective Java / SOLID / DDD leanings: prefer immutability, records for data, `Optional`/empty collections over null, static factories/builders, interfaces over abstract classes.
- Lombok is available and used (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter/@Setter`, `@SneakyThrows`).
- Existing code uses records heavily for DTOs (`parser/confluence/content/*`, link types) and sealed-ish pattern matching (`switch` over `LinksValue` subtypes in `DispatcherPage`).

Comments and user-facing strings in this codebase are frequently in **Russian** — match the surrounding language when editing a file.
