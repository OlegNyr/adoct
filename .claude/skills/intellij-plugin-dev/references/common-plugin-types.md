# Top 5 editor / code-insight plugin types

These are the five most commonly built plugin types that act on code in the editor. Folding and
line markers are covered in depth (full classes, registration, gotchas); the other three are
solid recipes. All are language-aware extension points — register them per language. Code is
Kotlin; Java is analogous. Respect threading (`threading-and-state.md`): these run under read
actions the platform provides — don't start your own, don't block.

Quick map:

| # | Plugin type | Extension point | plugin.xml tag |
|---|---|---|---|
| 1 | **Code folding** (collapse regions) | `FoldingBuilderEx` | `lang.foldingBuilder` |
| 2 | **Line markers / gutter icons** | `LineMarkerProvider` / `RelatedItemLineMarkerProvider` | `codeInsight.lineMarkerProvider` |
| 3 | Inspection + quick fix | `LocalInspectionTool` | `localInspection` |
| 4 | Completion | `CompletionContributor` | `completion.contributor` |
| 5 | Annotator (semantic highlight / validation) | `Annotator` | `annotator` |

---

## 1. Code folding (сворачивание кода) — FoldingBuilderEx

Folding lets the user collapse a text range into a short placeholder. You implement three things:
**which ranges fold** (`buildFoldRegions`), **what the collapsed text shows** (`getPlaceholderText`),
and **whether it's collapsed on open** (`isCollapsedByDefault`).

```kotlin
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class MyFoldingBuilder : FoldingBuilderEx(), DumbAware {

    // 1) Which ranges can be folded. `quick` = true means "be fast, no resolve" (editor opening).
    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        // Example: fold every block comment / custom node type in the file.
        for (element in PsiTreeUtil.findChildrenOfType(root, MyBlockElement::class.java)) {
            val node = element.node ?: continue
            val range = TextRange(element.textRange.startOffset, element.textRange.endOffset)
            if (range.length > 0) {
                descriptors += FoldingDescriptor(node, range)
            }
        }
        return descriptors.toTypedArray()
    }

    // 2) Text shown when the region is collapsed. Keep it short and meaningful.
    override fun getPlaceholderText(node: ASTNode): String {
        return "{...}"   // e.g. derive from node text: first line, key name, etc.
    }

    // 3) Whether this region starts collapsed (like Java imports). Usually false.
    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
```

Registration in `plugin.xml`:
```xml
<extensions defaultExtensionNs="com.intellij">
  <lang.foldingBuilder language="YourLang"
                       implementationClass="com.example.MyFoldingBuilder"/>
</extensions>
```

Key points and gotchas:
- **Implement `DumbAware`** if folding doesn't need indexes — otherwise folding won't run during
  indexing (and the SDK tutorial tests require it). If you DO need resolve/indexes, skip DumbAware
  but then return early when `quick == true`.
- **Honor `quick`:** when true the editor wants immediate auto-folding (e.g. collapse imports on
  open). Do no reference resolving and no expensive checks in quick mode — return only cheap,
  syntactic folds, or an empty array.
- One AST node may yield **several** `FoldingDescriptor`s (several ranges per node is allowed).
- `FoldingDescriptor` has richer constructors: `FoldingDescriptor(node, range, group,
  placeholderText, collapsedByDefault, dependencies)` lets you set the placeholder and default-
  collapse per-descriptor instead of via the overrides — handy when the placeholder varies.
- Use a **`FoldingGroup`** (`FoldingGroup.newGroup("name")`) to make several descriptors fold/
  unfold together as one logical region.
- Ranges must be non-empty and within the document; zero-length or overlapping-invalid ranges are
  dropped or warned about.
- For "// region … // endregion" style **custom** folding, subclass `CustomFoldingBuilder` and
  implement `buildLanguageFoldRegions` instead — it adds custom-region handling for free.
- Default collapse can also be driven by the user's Code Folding settings; don't hard-collapse
  things users expect to see.

Testing: `BasePlatformTestCase` + `myFixture.testFolding("testData/file.ext")` — put `<fold
text='{...}'>…</fold>` markers in the test data and the fixture asserts folds + placeholders.

---

## 2. Line markers / gutter icons (линия / маркеры на полях) — LineMarkerProvider

Line markers are the icons/badges in the left gutter (next to line numbers): run arrows,
"implemented by", "overrides", navigation to related elements, etc. Two flavors:

### 2a. Simple marker (icon + tooltip) — `LineMarkerProvider`

```kotlin
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import javax.swing.Icon

class MyLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // CRITICAL: only react to a LEAF element (e.g. the identifier token), never a
        // composite/parent PSI element. Returning markers for non-leaf elements triggers a
        // platform error ("Performance warning / must return LineMarkerInfo for leaf elements")
        // and causes duplicate or misplaced icons.
        if (!isTargetLeaf(element)) return null

        return LineMarkerInfo(
            element,                       // the leaf element
            element.textRange,
            MY_ICON,                       // javax.swing.Icon
            Function { "My tooltip" },     // tooltip provider
            null,                          // GutterIconNavigationHandler (null = no click nav)
            GutterIconRenderer.Alignment.CENTER,
            { "accessible name" },
        )
    }
}
private val MY_ICON: Icon = com.intellij.openapi.util.IconLoader.getIcon("/icons/mark.svg", MyLineMarkerProvider::class.java)
private fun isTargetLeaf(e: PsiElement): Boolean = /* e.g. */ e is com.intellij.psi.PsiIdentifier
```

### 2b. Navigation marker (click → jump to related elements) — `RelatedItemLineMarkerProvider`

Preferred when the icon should navigate. `NavigationGutterIconBuilder` builds the info and handles
the popup when there are multiple targets.

```kotlin
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement

class MyNavMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (!isTargetLeaf(element)) return                 // same leaf-only rule
        val targets = resolveRelatedElements(element)      // e.g. definitions, usages, configs
        if (targets.isEmpty()) return
        val info = NavigationGutterIconBuilder.create(MY_ICON)
            .setTargets(targets)
            .setTooltipText("Navigate to related")
            .createLineMarkerInfo(element)
        result.add(info)
    }
}
```

Registration (same tag for both flavors):
```xml
<extensions defaultExtensionNs="com.intellij">
  <codeInsight.lineMarkerProvider language="YourLang"
                                  implementationClass="com.example.MyLineMarkerProvider"/>
</extensions>
```

Key points and gotchas:
- **Leaf-element rule is the #1 mistake.** `getLineMarkerInfo` / `collectNavigationMarkers` are
  invoked for *every* PSI element; return/add markers only for the smallest leaf (identifier
  token). Markers on parent elements cause errors and wrong placement.
- **Keep `getLineMarkerInfo` fast** — it runs on every element during highlighting. For anything
  expensive (resolve, index lookups), override `collectSlowLineMarkers(elements, result)` instead;
  it runs in a later, cancellable pass.
- For **navigation**, prefer `RelatedItemLineMarkerProvider` + `NavigationGutterIconBuilder` over a
  hand-written `GutterIconNavigationHandler` — you get the multi-target popup and consistent UX.
- Use 12×12 (gutter) SVG icons; load via `IconLoader.getIcon(path, anchorClass)`.
- Provide a meaningful accessible name (last lambda) — required for accessibility and tests.

Testing: `myFixture.findGuttersAtCaret()` / `myFixture.findAllGutters()` to assert markers exist
and point where expected.

---

## 3. Inspection + quick fix — LocalInspectionTool

Flags a problem and (optionally) offers an Alt+Enter fix.

```kotlin
class MyInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (isProblem(element)) holder.registerProblem(element, "Message", MyQuickFix())
            }
        }
}
class MyQuickFix : LocalQuickFix {
    override fun getFamilyName() = "Fix it"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // platform wraps this in a write action; mutate PSI here
    }
}
```
```xml
<localInspection language="YourLang" shortName="MyInspection"
                 displayName="My inspection" groupName="My group"
                 level="WARNING" enabledByDefault="true"
                 implementationClass="com.example.MyInspection"/>
```
Visitor runs under a read action automatically; keep it cheap. Test with
`myFixture.enableInspections` + `checkHighlighting`, fixes with `findSingleIntention` +
`launchAction` + `checkResultByFile`.

## 4. Completion — CompletionContributor

```kotlin
class MyCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(p: CompletionParameters, c: ProcessingContext, r: CompletionResultSet) {
                    r.addElement(LookupElementBuilder.create("suggestion").withTypeText("hint"))
                }
            })
    }
}
```
```xml
<completion.contributor language="YourLang" implementationClass="com.example.MyCompletionContributor"/>
```
Narrow the `PlatformPatterns` pattern so you only contribute in the right context; broad patterns
slow completion and add noise.

## 5. Annotator — semantic highlighting & validation

Runs during highlighting to add colors/warnings beyond the lexer (e.g. mark unknown keys, color
specific tokens). Lighter than an inspection; good for always-on, syntax-driven feedback.

```kotlin
class MyAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (looksWrong(element)) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Suspicious")
                .range(element.textRange).create()
        }
        // or recolor: holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        //   .range(tokenRange).textAttributes(MyHighlighter.MY_KEY).create()
    }
}
```
```xml
<annotator language="YourLang" implementationClass="com.example.MyAnnotator"/>
```
`annotate` must be fast and side-effect-free (it runs on every change). Use an inspection instead
when you need batch analysis, suppression, or a quick fix tied to a settings toggle.

---

### Choosing between overlapping options
- Fold ranges → **FoldingBuilderEx**. Gutter icon/navigation → **LineMarkerProvider**.
- Always-on, syntactic problem/coloring → **Annotator**. Configurable, suppressible, batch-able,
  with quick fix → **LocalInspectionTool**. (Don't do both for the same check — pick one.)
- Suggestions in the completion popup → **CompletionContributor**.
