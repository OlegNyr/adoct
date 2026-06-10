# Testing IntelliJ plugins

The platform test framework runs your code against a real (headless) IDE core. Enable it in
`build.gradle.kts`:
```kotlin
dependencies {
    intellijPlatform {
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")   // JUnit4 is the platform default
}
```
JUnit4 is the baseline. JUnit5 is possible but needs the dedicated test framework type and runner;
prefer JUnit4 unless the user insists.

## Light tests (fast, in-memory) — most common

Extend `BasePlatformTestCase`. It gives a light in-memory project + `myFixture`.

```kotlin
class MyInspectionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData"

    fun testInspectionFlagsProblem() {
        myFixture.configureByText("a.java", "class A { void f() { <warning>badCall()</warning>; } }")
        myFixture.enableInspections(MyInspection::class.java)
        myFixture.checkHighlighting()
    }

    fun testCompletion() {
        myFixture.configureByText("a.txt", "my<caret>")
        myFixture.complete(CompletionType.BASIC)
        assertContainsElements(myFixture.lookupElementStrings!!, "myKeyword")
    }

    fun testQuickFix() {
        myFixture.configureByFile("before.java")
        val intention = myFixture.findSingleIntention("Fix it")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile("after.java")
    }
}
```
`myFixture` highlights: `configureByText/File`, `complete`, `checkHighlighting`,
`findSingleIntention`, `launchAction`, `checkResultByFile`, `type`, `lookupElementStrings`.

### Highlighting markup in test data
Inline tags in fixture text assert highlighting: `<warning>…</warning>`, `<error>…</error>`,
`<info>…</info>`, and `<caret>` marks the cursor. `checkHighlighting()` fails if actual ≠ declared.

## Heavy tests (real project on disk)

When you need a real module/SDK/project model, extend `HeavyPlatformTestCase` (a.k.a. the heavy
fixture) or build a fixture with `IdeaTestFixtureFactory`. Heavier and slower — use only when a
light test can't express the scenario (e.g. multi-module, real run configurations).

## Test data layout

Keep input/expected files under `src/test/testData` (or your `getTestDataPath()`), versioned
alongside the test. `before.java`/`after.java` pairs are the convention for fix tests.

## Threading in tests

Tests run with `isUnitTestMode = true`. Many platform calls behave synchronously, but still wrap
model reads/writes properly (`runReadAction`/`WriteCommandAction`) so the same code works in
production. Use `PlatformTestUtil.dispatchAllEventsInIdeEventQueue()` to flush the EDT queue when
asserting async UI effects.

## Running

```
./gradlew test                 # unit/light + heavy tests
./gradlew check                # test + verifyPlugin + others
```
For UI-level tests, the Gradle plugin offers `runIdeForUiTests` with the robot server, paired with
the `intellij-ui-test-robot` library — only reach for this when headless fixtures can't cover the
interaction.

## Practical advice

- Favor many fast light tests over a few heavy ones.
- Test inspections/intentions/completion via `myFixture` assertions, not by poking internals.
- For Kotlin-analysis code, run the suite in **both** K1 and K2 during migration (toggle with the
  VM options from `kotlin-k2-and-psi.md`) until K2-only is declared.
- Put plugin-loading sanity in CI: `verifyPlugin` catches missing `bundledModule`/`<dependencies>`
  declarations that unit tests won't.
