# Threading model, coroutines, background work & persistent state

The #1 source of real plugin bugs. Get this right and most "Read access is allowed from…",
freeze, and `Slow operations are prohibited on EDT` problems disappear.

## The model in one screen

- **EDT (Event Dispatch Thread / UI thread):** all Swing UI access; must stay responsive. Never do
  I/O, network, indexing, or long PSI traversals here.
- **Read action:** required to read the PSI/VFS/project model from a background thread. Multiple
  read actions can run concurrently; they're blocked while a write action runs.
- **Write action:** required to modify PSI/model. Must run on the EDT. Only one at a time; blocks
  all reads.
- **Background thread (BGT):** where long work belongs. To read the model from here, wrap in a
  read action.

```kotlin
// Read the model from a background thread:
val text = ReadAction.compute<String, RuntimeException> { psiFile.text }

// Or non-blocking read action that auto-restarts if the model changes:
ReadAction.nonBlocking<Result> { computeFromPsi() }
    .inSmartMode(project)
    .finishOnUiThread(ModalityState.defaultModalityState()) { result -> updateUi(result) }
    .submit(AppExecutorUtil.getAppExecutorService())

// Modify the model — write action on EDT:
WriteCommandAction.runWriteCommandAction(project) {
    psiElement.replace(newElement)   // undoable, grouped as one command
}

// Jump back onto the EDT for UI:
ApplicationManager.getApplication().invokeLater({ /* UI */ }, ModalityState.defaultModalityState())
```

Guidelines:
- Wrap model reads off-EDT in `ReadAction`; prefer `ReadAction.nonBlocking` for anything that can
  be cancelled/restarted (it cooperates with indexing and PSI changes).
- Wrap model writes in `WriteCommandAction` (gives undo + PSrite action + EDT) rather than raw
  `runWriteAction`, so the change is undoable.
- Respect `@RequiresEdt`, `@RequiresReadLock`, `@RequiresBackgroundThread`, `@RequiresWriteLock`.
- Check `ProgressManager.checkCanceled()` in long loops; honor `ProgressIndicator`.
- Use `DumbService`/`inSmartMode` when you need indexes (they're unavailable during indexing).

## Coroutines (modern, preferred for new code)

The platform is coroutine-first. A `@Service` gets an injected `CoroutineScope`; use it.

```kotlin
@Service(Service.Level.PROJECT)
class MyService(private val project: Project, private val cs: CoroutineScope) {
    fun start() {
        cs.launch {
            val data = withContext(Dispatchers.IO) { fetch() }      // off-EDT I/O
            val psiInfo = readAction { analyze(project) }           // suspend read action
            withContext(Dispatchers.EDT) { showInUi(psiInfo) }      // EDT for UI
            edtWriteAction { mutate() }                             // suspend write action on EDT
        }
    }
}
```
Key dispatchers/helpers: `Dispatchers.EDT`, `Dispatchers.IO`, `readAction { }`,
`writeAction { }` / `edtWriteAction { }`, `smartReadAction(project) { }`. Don't use
`Dispatchers.Default` for model access — use the read/write helpers.

Bundle coroutines correctly: the platform ships its own kotlinx-coroutines; don't shade your own.
The Gradle plugin excludes the shadowed `org.jetbrains.intellij.deps.kotlinx` group.

## Background tasks with progress (classic API)

```kotlin
ProgressManager.getInstance().run(
    object : Task.Backgroundable(project, "Doing work", /* canCancel = */ true) {
        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            for (i in 0..100) {
                indicator.checkCanceled()
                indicator.fraction = i / 100.0
                indicator.text = "Step $i"
            }
        }
    })
```
Use `Task.Modal` if the user must wait, `Task.Backgroundable` otherwise. For coroutine code prefer
`withBackgroundProgress(project, "…") { reportProgress(…) { } }`.

## Application/Project access & modality

- `ApplicationManager.getApplication()` → `runReadAction`, `invokeLater`, `isDispatchThread`,
  `isUnitTestMode`.
- Use the right `ModalityState` with `invokeLater` so callbacks fire under the correct dialog
  context.

## Persistent state (`@State` / `@Storage`)

Settings that survive restarts. **In 253 `@Storage` must be inside `@State(storages=[...])`** — never
top-level.

```kotlin
@Service(Service.Level.APP)
@State(name = "MySettings", storages = [Storage("MyPlugin.xml")])
class MySettings : PersistentStateComponent<MySettings.State> {
    data class State(var apiUrl: String = "", var enabled: Boolean = true)
    private var state = State()
    override fun getState() = state
    override fun loadState(s: State) { state = s }
    companion object { fun getInstance(): MySettings = service() }
}
```
- App-level storage lives in the IDE config dir; project-level uses
  `StoragePathMacros.WORKSPACE_FILE` (per-user) or a shared project file.
- Keep the State class a simple data holder with public mutable fields/`var`s and default values —
  it's (de)serialized by the platform.
- Read/write `getInstance().state.xxx`; bind it in a `Configurable` via the Kotlin UI DSL.

## PasswordSafe for secrets

Never store tokens/passwords in `@State`. Use `PasswordSafe`:
```kotlin
val attributes = CredentialAttributes(generateServiceName("MyPlugin", "api-token"))
PasswordSafe.instance.set(attributes, Credentials("user", token))
val token = PasswordSafe.instance.getPassword(attributes)
```
