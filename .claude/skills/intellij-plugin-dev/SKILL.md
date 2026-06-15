---
name: intellij-plugin-dev
description: >
  Develop, build, test, and publish IntelliJ Platform plugins for IntelliJ IDEA and other JetBrains
  IDEs, with first-class support for 2025.3 (build 253). Use this skill WHENEVER a task touches
  IntelliJ/JetBrains plugin development, even when no version is named: scaffolding, build.gradle.kts
  with the IntelliJ Platform Gradle Plugin 2.x, plugin.xml and plugin model v2, extension points
  (actions, services, tool windows, inspections, intentions, completion, PSI/UAST), the threading
  model, persistent state, Kotlin K2 mode and the Analysis API, platform tests, and
  verify/build/sign/publishPlugin. CRITICAL for 253: IntelliJ IDEA is now a UNIFIED distribution —
  IC (Community) artifacts are gone, so use intellijIdea(version) not intellijIdeaCommunity/Ultimate,
  Gradle plugin 2.10.4+, K2 default. Trigger on "плагин для IDEA", "разработка плагина IntelliJ",
  "intellij platform gradle plugin", "plugin.xml", "extension point", "verifyPlugin падает",
  "мигрировать плагин на 2025.3", or any IntelliJ-plugin code review.
---

# IntelliJ Platform Plugin Development (2025.3 / build 253)

This skill encodes the current, correct way to build IntelliJ Platform plugins. Several facts
changed in 2025.3 and contradict older tutorials and pre-2025 training data. **When 2025.3 (253)
is the target, the rules in "2025.3 critical changes" below override anything else.**

Before generating any non-trivial answer, decide which reference file you need and read it:

| You are doing… | Read |
|---|---|
| Project scaffolding, `build.gradle.kts`, Gradle tasks, publishing, Plugin Verifier, EAP/snapshot testing, migration | `references/build-setup.md` |
| `plugin.xml` structure, plugin model v2 `<dependencies>`, declaring extensions/actions/listeners | `references/plugin-xml.md` |
| Picking & implementing an extension point (actions, services, tool windows, Configurable, inspections, intentions, completion, line markers, PSI/UAST) | `references/extension-points.md` |
| Building the top 5 editor / code-insight plugin types — code folding (FoldingBuilderEx), line markers / gutter icons (LineMarkerProvider), inspections, completion, annotators — with full folding & line-marker recipes | `references/common-plugin-types.md` |
| Threading (EDT, read/write actions), coroutines, background tasks, services lifecycle, persistent state | `references/threading-and-state.md` |
| Kotlin K2 mode, Analysis API, PSI navigation, supportsKotlinPluginMode | `references/kotlin-k2-and-psi.md` |
| Writing tests (light/heavy fixtures, `BasePlatformTestCase`, test data) | `references/testing.md` |

Read more than one when a task spans them (e.g. a new inspection needs `extension-points.md` +
`plugin-xml.md` + `threading-and-state.md`). Don't dump a reference verbatim — extract the parts
relevant to the task.

---

## 2025.3 critical changes (memorize these)

These are the highest-value facts. Getting them wrong produces builds that won't resolve.

1. **Unified distribution.** From 2025.3, IntelliJ IDEA Community and Ultimate ship as ONE
   distribution under the `IU` product code. **`IC` (Community) artifacts no longer exist for
   2025.3+.** Resolving `IC-2025.3`, `IC-253.x`, or calling `intellijIdeaCommunity()` /
   `intellijIdeaUltimate()` for 253 **fails**.

2. **Use `intellijIdea(version)`.** For 253+, the dependency helper is `intellijIdea("2025.3")` —
   regardless of whether you target IDEA specifically or any other IntelliJ-based IDE. The older
   `intellijIdeaCommunity(version)` / `intellijIdeaUltimate(version)` helpers still work only for
   **2025.2 and earlier**. For PyCharm 2025.1+, use `pycharm(version)` (not
   `pycharmCommunity`/`pycharmProfessional`).

3. **Gradle plugin 2.10.4+ required** to target 253. Plugin id is
   `org.jetbrains.intellij.platform`. Check GitHub Releases for the newest 2.x; suggest the
   latest stable rather than pinning to 2.10.4 if you can confirm a newer one.

4. **Paid features** → depend on the `com.intellij.modules.ultimate` module. When the user has no
   active subscription, that module (and plugins depending on it) is disabled. The free tier in
   253 gained: basic DB tools, framework wizards (Spring Boot, Ktor…), LSP API (now free).

5. **K2 mode is the DEFAULT** in 2025.3, and K1 mode is officially deprecated. If your plugin uses
   the Kotlin plugin API, declare `<supportsKotlinPluginMode supportsK2="true"/>` (see
   `kotlin-k2-and-psi.md`). To run K1 locally for testing: VM option
   `-Didea.kotlin.plugin.use.k1=true`.

6. **Module extraction → `bundledModule(...)`.** Several modules were split out of the core plugin
   into separate classloaders. If you use their API, add an explicit `bundledModule(name)` in
   Gradle AND a `<module name="…"/>` in `plugin.xml`'s `<dependencies>`. Affected include:
   `intellij.platform.vcs.dvcs`, `intellij.platform.vcs.log`, `intellij.platform.vcs.log.graph`,
   `intellij.platform.collaborationTools.auth(.base)`, the `intellij.platform.scriptDebugger.*`
   set. IntelliLang moved too: replace `bundledPlugin("org.intellij.intelliLang")` with
   `bundledModule("intellij.platform.langInjection")`.

7. **`@Storage` annotation** may only appear inside `@State(storages = [...])`, never at top level.

8. **Plugin model v2.** When depending on bundled *modules* (not just plugins), `plugin.xml` must
   use the `<dependencies>` block (with `<plugin id="…"/>` and `<module name="…"/>` children),
   not the legacy top-level `<depends>` tags. Details in `plugin-xml.md`.

9. **Split Mode** (remote dev: separate backend + JetBrains Client frontend) is testable from
   2025.3 via the Gradle plugin; use `pluginInstallationTarget` to choose frontend/backend/both.

Backward compatibility note: a plugin built against 2025.2 or earlier generally **still works** in
2025.3 — there's no forced rush. The migration only matters when you set the dev SDK to 253. Always
confirm compatibility with `verifyPlugin`.

---

## Standard workflow for a new plugin

1. **Clarify intent & language.** What should the plugin do, target IDE(s), Java or Kotlin? Default
   to Kotlin + Kotlin DSL build script unless told otherwise — it's the platform's own choice and
   has the best DSL/UI support. Match the user's working language in prose.

2. **Scaffold the project** per `references/build-setup.md`: `build.gradle.kts`,
   `gradle.properties`, `settings.gradle.kts`, Gradle wrapper, and the
   `src/main/resources/META-INF/plugin.xml` per `references/plugin-xml.md`. For 253 use Java/JVM
   **target 21**, `sinceBuild = "253"`, Gradle plugin 2.10.4+, and `intellijIdea("2025.3")`.

3. **Implement the feature** by choosing the right extension point(s) from
   `references/extension-points.md`. Wire each one in `plugin.xml`. Respect the threading model
   (`references/threading-and-state.md`) — this is the #1 source of real-world plugin bugs.

4. **Run it**: `./gradlew runIde` launches a sandboxed IDE with the plugin loaded. Iterate.

5. **Test** with the platform test framework (`references/testing.md`).

6. **Verify & ship**: `./gradlew verifyPlugin` (Plugin Verifier — same checks Marketplace runs),
   then `buildPlugin` → `signPlugin` → `publishPlugin`. See `build-setup.md` for signing keys and
   the `PUBLISH_TOKEN`.

---

## Non-negotiable correctness rules

- **Never block the EDT.** UI/event-dispatch thread work must be fast. Long work goes to a
  background thread / coroutine / `Task.Backgroundable`; PSI/VFS/model reads need a read action,
  writes need a write action on the EDT. Getting this wrong causes freezes and
  `Read access is allowed from…` exceptions. See `threading-and-state.md`.
- **Declare dependencies explicitly** in BOTH `build.gradle.kts` (compile/runtime classpath) and
  `plugin.xml` (runtime loading + Marketplace compatibility). One without the other breaks either
  compilation or loading.
- **Prefer light services** (`@Service`) obtained via `project.service<T>()` / `service<T>()` over
  registering components in XML; constructor-inject nothing heavy and never do work in
  constructors.
- **Don't invent API.** IntelliJ API surface is large and version-sensitive. If unsure whether a
  class/method exists in 253, say so and suggest verifying against the SDK or
  `https://plugins.jetbrains.com/docs/intellij/` rather than guessing a signature.
- **Annotations matter:** respect `@ApiStatus.Internal` / `@ApiStatus.Experimental` /
  `@RequiresEdt` / `@RequiresReadLock` / `@RequiresBackgroundThread` when present.

## Authoritative sources (fetch when a detail is uncertain)

- SDK docs: `https://plugins.jetbrains.com/docs/intellij/welcome.html`
- Gradle plugin docs: `https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html`
- Gradle plugin releases (latest version): `https://github.com/JetBrains/intellij-platform-gradle-plugin/releases`
- 2025.* incompatible changes: `https://plugins.jetbrains.com/docs/intellij/api-changes-list-2025.html`
- Kotlin Analysis API / K2: `https://kotlin.github.io/analysis-api/`
