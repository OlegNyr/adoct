# Build setup, tasks, publishing & migration (2025.3 / 253)

Toolchain: **IntelliJ Platform Gradle Plugin 2.x** (id `org.jetbrains.intellij.platform`),
**2.10.4+** for 253. The old **Gradle IntelliJ Plugin 1.x** (id `org.jetbrains.intellij`) is
obsolete — do not use it for new work.

## Table of contents
1. build.gradle.kts (253, Kotlin)
2. gradle.properties + settings.gradle.kts + version catalog
3. Dependency helpers (what to call for which target)
4. Gradle tasks you actually use
5. Plugin Verifier
6. Signing & publishing
7. Testing against 2025.3 before release (EAP / snapshot)
8. Migration: 1.x → 2.x, and ≤2025.2 → 2025.3

---

## 1. build.gradle.kts (targeting 2025.3)

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"          // latest stable Kotlin
    id("org.jetbrains.intellij.platform") version "2.10.4"   // 2.10.4+ for 253; check Releases for newer
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }   // adds JetBrains repos + installers
}

dependencies {
    intellijPlatform {
        // 2025.3+: unified distribution. Do NOT use intellijIdeaCommunity()/Ultimate() here.
        intellijIdea("2025.3")

        // Bundled plugins you compile against (also declare in plugin.xml):
        bundledPlugin("com.intellij.java")            // if you touch Java PSI
        // bundledModule("intellij.platform.vcs.log") // if you use an extracted module

        // Marketplace/3rd-party plugin dependency, "id:version":
        // plugin("org.intellij.scala:2025.3.14")

        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    // testImplementation("junit:junit:4.13.2")   // JUnit4 is the platform default
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            // untilBuild is optional; leaving it open lets the plugin run on future builds.
            // untilBuild = "253.*"
        }
        changeNotes = """
            Initial release.
        """.trimIndent()
    }

    // Run Plugin Verifier against recommended IDEs:
    pluginVerification {
        ides { recommended() }
    }

    // Publishing (token from env; never hardcode):
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // channels = listOf("eap")  // optional non-default release channel
    }

    // Marketplace signing (required to publish; see §6):
    signing {
        certificateChainFile = file("chain.crt")
        privateKeyFile = file("private.pem")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

kotlin {
    jvmToolchain(21)   // 253 requires Java 21
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
```

Notes:
- **Java 21** is mandatory for 253 (242+ needed 17; older needed 11). The JDK chosen runs Gradle
  and compiles plugin sources.
- If you don't use Kotlin, drop the kotlin plugin & `kotlin{}` block and keep `JavaCompile`.
- `defaultRepositories()` wires the JetBrains releases repo and installer artifacts.

## 2. gradle.properties / settings.gradle.kts / version catalog

`gradle.properties` — keep heap reasonable and enable config cache:
```properties
org.gradle.jvmargs=-Xmx4g
org.gradle.configuration-cache=true
org.gradle.caching=true
kotlin.stdlib.default.dependency=false
```
`settings.gradle.kts` — the plugin needs its repositories available at settings time:
```kotlin
rootProject.name = "my-plugin"
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```
Prefer a `gradle/libs.versions.toml` version catalog for the platform/Kotlin/plugin versions in
real projects so upgrades are one-line.

## 3. Dependency helpers — which to call

| Target | Helper |
|---|---|
| IntelliJ IDEA **2025.3+** (unified) | `intellijIdea("2025.3")` |
| IntelliJ IDEA **≤ 2025.2** | `intellijIdeaCommunity("2025.2")` or `intellijIdeaUltimate("2025.2")` |
| PyCharm **2025.1+** | `pycharm("2025.3")` |
| PyCharm **≤ 2024.3** | `pycharmCommunity(...)` / `pycharmProfessional(...)` |
| A bundled plugin | `bundledPlugin("com.intellij.java")` |
| An extracted bundled module | `bundledModule("intellij.platform.vcs.log")` |
| A Marketplace plugin | `plugin("id:version")` |
| Local IDE install | `local("/path/to/IDE")` |

The platform type here is **only** used during development. Actual user compatibility comes from
`plugin.xml` (`<depends>` / `<dependencies>` and `since-build`). A single build can serve many IDE
versions.

## 4. Gradle tasks

| Task | Purpose |
|---|---|
| `runIde` | Launch a sandboxed IDE with the plugin loaded (primary dev loop) |
| `buildPlugin` | Produce the distributable ZIP in `build/distributions/` |
| `verifyPlugin` | Run Plugin Verifier (binary compat against target IDEs) |
| `signPlugin` | Sign the ZIP for Marketplace |
| `publishPlugin` | Upload to JetBrains Marketplace |
| `runIdeForUiTests` | Launch with the robot server for UI tests |
| `prepareSandbox` | Stage the sandbox without launching |

Register an ad-hoc EAP run (test on a different build without changing the dev SDK):
```kotlin
val runEap by intellijPlatformTesting.runIde.registering {
    type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea
    version = "253-EAP-SNAPSHOT"
    useInstaller = false
}
```

## 5. Plugin Verifier

`verifyPlugin` runs the same checks Marketplace runs on upload — run it before every publish.
Configure which IDEs to check:
```kotlin
intellijPlatform {
    pluginVerification {
        ides {
            recommended()            // JetBrains' recommended set
            // ide(IntelliJPlatformType.IntellijIdea, "2025.3")
            // select(...) for ranges
        }
    }
}
```
Common verifier failures in 253: missing `bundledModule(...)` for an extracted module → the
verifier names the module; add it in both Gradle and `plugin.xml`.

## 6. Signing & publishing

Marketplace requires **signed** uploads. Generate a certificate chain + private key (see SDK docs
"Plugin Signing"); store the password and the Marketplace token as environment variables
(`PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`). Then:
```
./gradlew clean buildPlugin verifyPlugin signPlugin publishPlugin
```
Versioning across IDE lines: use SemVer with a build suffix, e.g. `1.2.3-251` and `1.2.3-253`;
Marketplace serves the right one per the user's IDE using each artifact's `since-build`/
`until-build`.

## 7. Testing against 2025.3 before/around release

- **Released build:** `intellijIdea("2025.3")` (or a 4-part build like `intellijIdea("253.27864.23")`).
- **EAP snapshot:**
  ```kotlin
  dependencies {
      intellijPlatform {
          intellijIdea("253-EAP-SNAPSHOT") { useInstaller = false }
      }
  }
  ```
  `useInstaller = false` opts into the snapshot Maven repo instead of installer artifacts.

## 8. Migration

### Gradle IntelliJ Plugin 1.x → IntelliJ Platform Gradle Plugin 2.x
- Plugin id `org.jetbrains.intellij` → `org.jetbrains.intellij.platform`.
- `intellij { version = "..."; type = "IC" }` → `dependencies { intellijPlatform { intellijIdea("...") } }`.
- Add `repositories { intellijPlatform { defaultRepositories() } }` and the settings-level repos.
- `patchPluginXml { sinceBuild.set(...) }` → `intellijPlatform { pluginConfiguration { ideaVersion { sinceBuild = "..." } } }`.
- Tasks renamed/reshaped; `runIde`, `buildPlugin`, `verifyPlugin`, `signPlugin`, `publishPlugin` remain.

### ≤ 2025.2 → 2025.3 (253)
1. Bump Gradle plugin to **2.10.4+**.
2. Replace `intellijIdeaCommunity()/Ultimate()` with `intellijIdea("2025.3")`.
3. Set JVM target **21**, `sinceBuild = "253"`.
4. For any extracted module you use, add `bundledModule(...)` (Gradle) + `<module name="…"/>`
   (plugin.xml `<dependencies>`); swap IntelliLang to `intellij.platform.langInjection`.
5. Move any top-level `@Storage` into `@State(storages = [...])`.
6. If you use the Kotlin plugin API, declare K2 support (see `kotlin-k2-and-psi.md`).
7. `./gradlew verifyPlugin` and fix what it reports.

If `Could not find idea:ideaIC:253.x` appears: that's the unified-distribution change — switch to
`intellijIdea(version)` (or build against an older IC for compatibility only).
