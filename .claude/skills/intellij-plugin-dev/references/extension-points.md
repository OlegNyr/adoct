# Extension points — catalog & patterns

Pick the extension point (EP) that matches the user's goal, implement the class, register it in
`plugin.xml`. Code shown in Kotlin; Java is analogous. Always respect threading
(`threading-and-state.md`).

## How to choose

| Goal | Extension point |
|---|---|
| Menu/toolbar/context command | `AnAction` + `<action>` |
| Background singleton holding state | `@Service` (app or project) |
| Side panel UI | `ToolWindowFactory` + `<toolWindow>` |
| Settings page | `Configurable` + `<applicationConfigurable>`/`<projectConfigurable>` |
| Code warning/error | `LocalInspectionTool` + `<localInspection>` |
| Quick-fix / Alt+Enter action | `IntentionAction` or inspection `LocalQuickFix` |
| Code completion | `CompletionContributor` + `<completion.contributor>` |
| Gutter icon / navigation | `LineMarkerProvider` + `<codeInsight.lineMarkerProvider>` |
| Run code on project open | `ProjectActivity` + `<postStartupActivity>` |
| React to platform events | message-bus listener (`<projectListeners>`) |
| New language support | `Language`, `ParserDefinition`, `FileType`, lexer/parser |
| Annotate/highlight syntax | `Annotator` + `<annotator>` |

---

## Action

```kotlin
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // …do the thing
    }
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
    // 2024.2+: declare which thread update() runs on.
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```
`getActionUpdateThread()` is required by modern platforms: return `BGT` if `update()` only touches
model/PSI (preferred), `EDT` only if it touches Swing components directly. `update()` must be fast
and side-effect-free.

## Light service (`@Service`)

```kotlin
@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project, private val scope: CoroutineScope) {
    fun doWork() { /* … */ }
    companion object {
        fun getInstance(project: Project): MyProjectService = project.service()
    }
}
```
- `Service.Level.APP` for an application-wide singleton (inject nothing or only `CoroutineScope`).
- Obtain via `project.service<MyProjectService>()` / `service<MyAppService>()`.
- A constructor `CoroutineScope` parameter is injected by the platform and is the right scope for
  launching background coroutines tied to the service lifecycle.
- Prefer `@Service` over registering in XML; only declare `<applicationService>`/`<projectService>`
  in plugin.xml if you need to override an interface→impl mapping.

## Tool window

```kotlin
class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = panel {           // Kotlin UI DSL v2
            row { label("Hello from my tool window") }
            row { button("Refresh") { /* … */ } }
        }
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
    override suspend fun isApplicableAsync(project: Project): Boolean = true
}
```
Register: `<toolWindow id="My" anchor="right" factoryClass="…MyToolWindowFactory"/>`.

## Settings page (Configurable)

```kotlin
class MySettingsConfigurable : Configurable {
    private var panel: DialogPanel? = null
    override fun getDisplayName() = "My Plugin"
    override fun createComponent(): JComponent =
        panel { row("API URL:") { textField().bindText(state::url) } }.also { panel = it }
    override fun isModified() = panel?.isModified() ?: false
    override fun apply() { panel?.apply() }
    override fun reset() { panel?.reset() }
    private val state get() = MySettings.getInstance().state
}
```
Register `<applicationConfigurable>` or `<projectConfigurable>` with `parentId="tools"` etc. Pair
with a persistent `@State` component (see `threading-and-state.md`).

## Local inspection + quick fix

```kotlin
class MyInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (/* problem condition */ false) {
                    holder.registerProblem(element, "Message", MyQuickFix())
                }
            }
        }
}
class MyQuickFix : LocalQuickFix {
    override fun getFamilyName() = "Fix it"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // runs inside a write action provided by the platform; mutate PSI here
    }
}
```
Inspection visitors run under a read action automatically; don't start your own. Register with
`<localInspection language="…" implementationClass="…" .../>`.

## Completion contributor

```kotlin
class MyCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    result.addElement(LookupElementBuilder.create("myKeyword"))
                }
            })
    }
}
```

## Line marker (gutter icon)

```kotlin
class MyLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // return a marker only for leaf elements to avoid duplicates
        return null
    }
}
```
Register `<codeInsight.lineMarkerProvider language="…" implementationClass="…"/>`.

> For full folding (FoldingBuilderEx) and line-marker recipes — including the leaf-element rule,
> navigation markers, and `quick`/DumbAware handling — see `references/common-plugin-types.md`.

## Startup activity

```kotlin
class MyStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // suspend fn: do async init off the EDT
    }
}
```
Register `<postStartupActivity implementation="…"/>`. Never block here; it runs during project open.

## Platform event listener (message bus)

```kotlin
class MyFileListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) { /* … */ }
}
```
Register via `<projectListeners>`/`<applicationListeners>` with the `topic` = the listener
interface FQN. Declarative registration is preferred over manual `messageBus.connect()`.

## Notifications

```kotlin
NotificationGroupManager.getInstance()
    .getNotificationGroup("My Notifications")
    .createNotification("Done", NotificationType.INFORMATION)
    .notify(project)
```
Register the group: `<notificationGroup id="My Notifications" displayType="BALLOON"/>`.
