# Kotlin K2 mode, Analysis API & PSI

## K2 is the default in 2025.3

From 2025.1 K2 mode is default; **in 2025.3 K1 is officially deprecated.** Practically every
Marketplace plugin already supports K2. If your plugin uses the **Kotlin plugin API** (reads/
resolves Kotlin code), you MUST declare K2 support or it won't load when K2 is on.

Declare in `plugin.xml`:
```xml
<extensions defaultExtensionNs="org.jetbrains.kotlin">
    <supportsKotlinPluginMode supportsK2="true"/>
</extensions>
```
Defaults are `supportsK1="true"`, `supportsK2="false"` — so silence means "K1 only," which is wrong
for 253. Options:
- Support both during migration: `<supportsKotlinPluginMode supportsK2="true"/>` (K1 stays true).
- K2-only: `<supportsKotlinPluginMode supportsK1="false" supportsK2="true"/>`.

Local testing of the other mode:
- Run **K1** on 2025.3: VM option `-Didea.kotlin.plugin.use.k1=true`.
- Run **K2** on ≤2025.2: VM option `-Didea.kotlin.plugin.use.k2=true`.

Add the VM option to the `runIde`/test task in `build.gradle.kts`:
```kotlin
tasks.test { jvmArgs("-Didea.kotlin.plugin.use.k1=true") }   // only if you must test K1
```

A plugin that does NOT touch the Kotlin plugin API (no Kotlin code analysis) needs no declaration
and works in either mode.

## Analysis API (the K2 way to inspect Kotlin code)

K2 replaced the old Kotlin-compiler-internals descriptor API (`BindingContext`,
`resolveToDescriptors`, `analyze {}` from the legacy API) with the **Kotlin Analysis API**
(`org.jetbrains.kotlin.analysis.api.*`). If you see code using the old descriptor API, it's K1-only
and must be migrated.

Core pattern — open an analysis session with `analyze`:
```kotlin
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtExpression

fun describe(expression: KtExpression): String? = analyze(expression) {
    // inside this lambda you have a KaSession receiver
    val ktType = expression.expressionType ?: return@analyze null
    ktType.toString()
}
```
- `analyze(contextElement) { … }` must run **inside a read action** (or a coroutine read action).
- Symbols (`KaSymbol`), types (`KaType`), call info (`resolveToCall()`), and diagnostics are all
  accessed via the `KaSession` receiver — **don't leak `Ka*` objects outside the `analyze` block**;
  they're only valid within it. Extract plain data (strings, PSI pointers) instead.
- Reference docs and migration guide: `https://kotlin.github.io/analysis-api/`.

Match the Analysis API version to the platform: it ships with the bundled Kotlin plugin, so depend
on `bundledPlugin("org.jetbrains.kotlin")` rather than pulling a standalone artifact.

## PSI basics (language-agnostic)

PSI (Program Structure Interface) is the parsed syntax/semantic tree. All reads need a read action.

```kotlin
// File → PSI
val psiFile: PsiFile? = PsiManager.getInstance(project).findFile(virtualFile)

// Find by type
val methods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)

// Navigate
val parentClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)

// Element at caret in an action
val element = e.getData(CommonDataKeys.PSI_ELEMENT)
val editor = e.getData(CommonDataKeys.EDITOR)
val caretOffset = editor?.caretModel?.offset
```
- **Don't hold raw PsiElement across actions** — they can be invalidated. Use `SmartPsiElementPointer`
  (via `SmartPointerManager`) to keep a stable reference between read actions.
- Modify PSI only inside a `WriteCommandAction` (see `threading-and-state.md`). Build new elements
  with the language's factory (`PsiElementFactory`, `KtPsiFactory`, etc.).
- For cross-language tooling (lint-like checks over Java + Kotlin), consider **UAST**
  (`org.jetbrains.uast`): `element.toUElement()`, `UMethod`, `UCallExpression` — a unified view.

## Stubs & indexes

- Use stub indexes / `FileBasedIndex` for fast project-wide lookups instead of walking PSI.
- Index access requires *smart mode*; guard with `DumbService.isDumb` / run in `smartReadAction`.
