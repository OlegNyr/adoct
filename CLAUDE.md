# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AsciiDocTools** — an IntelliJ Platform plugin that converts Confluence content (HTML "storage" format) into AsciiDoc. It can pull pages live via the Confluence REST API or convert an offline HTML export, downloading attachments and resolving internal links along the way.

## Build & run

The IntelliJ Platform Gradle Plugin (2.x) drives everything. JVM toolchain is **21**.

- `./gradlew runIde` — launch a sandbox IDE (IntelliJ IDEA Community 2025.1.3) with the plugin installed. This is the primary way to exercise the plugin manually (open a project, then `Alt+I` for the file importer, `Alt+Shift+I` for Confluence export).
- `./gradlew build` — compile + assemble the plugin zip.
- `./gradlew test` — run the JUnit test suite (`src/test/java`).
- `./gradlew test --tests "ru.gitverse.adoct.post.TableCompactPostProcesingTest"` — run a single test class.
- `-Drelease=true` system property strips the `-SNAPSHOT` suffix from the version.

Dependencies resolve from public repositories only: Maven Central + the Gradle Plugin Portal for plugins, and the JetBrains IntelliJ Platform repositories (`intellijPlatform.defaultRepositories()`) for the platform and bundled plugins. See `settings.gradle.kts`. No credentials or private mirrors are required.

## Architecture

Two source roots with distinct responsibilities:

- `ru.gitverse.adoct.plugins.idea.*` — the **IntelliJ plugin layer** (UI, actions, settings, services). This is glue: tool windows, the `Alt+I`/`Alt+Shift+I` actions, a `Configurable` for Confluence server settings, and `ConvertDocsUrlToAdoc` (an app-level `@Service` that wires settings → client → converter behind a `ProgressIndicator`). Registered in `src/main/resources/META-INF/plugin.xml`.
- `ru.gitverse.adoct.*` — the **conversion engine**, fully decoupled from IntelliJ APIs so it can be unit-tested standalone.

### Conversion flow

`DispatcherPage.generate(pageId, progress)` is the orchestrator for a live Confluence pull:
1. `ConfluenceClient` fetches the page (`getMainPage`) and downloads attachments into `<dest>/attache/`.
2. Raw inputs are saved under `<dest>/source/` (`body.storage.html`, `view.storage.html`, `content.json`, `links.json`) — `links.json` is also a **cache**: previously resolved links are reloaded to avoid re-querying.
3. Links found in the storage HTML (`getLinks`) are resolved against the rendered view and the Confluence search/user APIs, then persisted.
4. `ConvertStorageToAdoc.convert(metadata, attachmentDir)` produces the AsciiDoc.

### The parser engine (the core)

`ConvertStorageToAdoc.convert` parses the storage HTML with **Jsoup**, then walks the `<body>` children through `ParseDispatcher`. The design is a **registry of pluggable handlers keyed by HTML tag name**:

- `ParseTags` (interface, `parser/doc/`) — each implementation declares which `tags()` it handles and an `isWork(element)` predicate. `ParseDispatcher` builds a `tag → [handlers]` map and dispatches each element to the first handler whose `isWork` returns true. Unknown tags fall through to printing `element.text()`; `<div>` recurses into children. Handlers needing to recurse implement `SetterDispatcher` to get a back-reference to the dispatcher.
- `ParseTagMacros` delegates Confluence macros (`<ac:structured-macro>` etc.) to a second registry: `ParseMacrosDispatcher` over `ParseMacros` implementations (`parser/macros/`, e.g. `MacrosCode`, `MacrosPlantuml`, `MacrosDrawio`, `MacrosExpand`, `MacrosNote`, `MacrosJira`, `MacrosToc`, `MacrosTabs`). Extend `AbstractParseMacros`, which registers by macro name and receives the dispatcher + printer.
- Output is written through `PrintWriterReturn` into a `StringWriter` (also tee'd to stdout for debugging).
- **Post-processing**: after the full document is built, the string is passed through an ordered `List<PostProcesing>` (`post/`, e.g. `DubleCaretPostProcesing`, `TableCompactPostProcesing`) — string-level cleanups that are easier than fixing mid-stream.
- **Splitting**: documents over 700 lines are split into multiple files by `SpliteratorAdoc` on `==` (level-2) headings; otherwise a single `index.adoc` is written.

`ParseContext` (builder) threads per-run state (metadata map keyed by `MetadataKey`, color-export flag) through the parse tree. `MetadataKey` is the enum of well-known context keys (title, URL, attachment/image/files folders, resolved links, color flag).

### To add support for a new element or macro

- New HTML tag → implement `ParseTags` and add it to the `parseTagsList` in `ConvertStorageToAdoc.convert`.
- New Confluence macro → extend `AbstractParseMacros` and add it to the `parserMacros` list in `ConvertStorageToAdoc.convert`.
Both lists are constructed inline there; order matters because the first matching handler wins.

## Code style

The repo ships AI coding rules in `.gigacode/rules/` (`CODESTYLE.md`, `JAVAPRACTIC.md`, `PRD.md`) marked `apply: always`. Highlights worth honoring:
- **2-space indentation**, 120-col lines, no wildcard imports, K&R braces, always braces on control statements.
- Effective Java / SOLID / DDD leanings: prefer immutability, records for data, `Optional`/empty collections over null, static factories/builders, interfaces over abstract classes.
- Lombok is available and used (`@Slf4j`, `@RequiredArgsConstructor`, `@Getter/@Setter`, `@SneakyThrows`).
- Existing code uses records heavily for DTOs (`client/content/*`, link types) and sealed-ish pattern matching (`switch` over `LinksValue` subtypes in `DispatcherPage`).

Comments and user-facing strings in this codebase are frequently in **Russian** — match the surrounding language when editing a file.
