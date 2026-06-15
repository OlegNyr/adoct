# plugin.xml & plugin model v2 (dependencies)

Location: `src/main/resources/META-INF/plugin.xml`. It declares identity, compatibility, runtime
dependencies, and every extension/action/listener the plugin contributes.

## Minimal skeleton

```xml
<idea-plugin>
    <id>com.example.myplugin</id>
    <name>My Plugin</name>
    <version>1.0.0</version>
    <vendor email="dev@example.com" url="https://example.com">Example</vendor>

    <description><![CDATA[
        What the plugin does. Shown on Marketplace. HTML allowed.
    ]]></description>

    <!-- Compatibility: since-build comes from build.gradle.kts ideaVersion {} when built. -->

    <!-- Dependencies: see "Two dependency styles" below. -->
    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- contributions registered here -->
    </extensions>

    <actions>
        <!-- actions registered here -->
    </actions>

    <applicationListeners>
        <!-- listeners registered here -->
    </applicationListeners>
</idea-plugin>
```

`<depends>com.intellij.modules.platform</depends>` declares the plugin is platform-only (works in
all IntelliJ-based IDEs). Add more for what you actually use.

## Two dependency styles — pick the right one

**Plugin model v1 (legacy `<depends>`)** — fine when you only depend on *plugins*:
```xml
<depends>com.intellij.modules.platform</depends>
<depends>com.intellij.java</depends>
<depends>org.intellij.scala</depends>
<depends optional="true" config-file="myplugin-withYaml.xml">org.jetbrains.plugins.yaml</depends>
```

**Plugin model v2 (`<dependencies>` block)** — REQUIRED when you depend on bundled *modules*
(common in 253 due to module extraction). Separates plugins from modules explicitly:
```xml
<dependencies>
    <plugin id="com.intellij.modules.platform"/>
    <plugin id="com.intellij.java"/>
    <plugin id="org.intellij.scala"/>
    <module name="intellij.platform.vcs.dvcs"/>
    <module name="intellij.platform.vcs.log"/>
</dependencies>
```
Each of these must also appear in `build.gradle.kts` (`bundledPlugin(...)` / `plugin(...)` /
`bundledModule(...)`). Gradle gives the compile classpath; plugin.xml gives runtime loading + the
correct Marketplace compatibility metadata. Missing either side breaks compilation or loading.

### Common module/plugin ids
| Need | Declare |
|---|---|
| Java PSI / language | plugin `com.intellij.java` |
| Kotlin plugin API | plugin `org.jetbrains.kotlin` |
| YAML | plugin `org.jetbrains.plugins.yaml` |
| Paid/Ultimate-only features | plugin `com.intellij.modules.ultimate` (disabled w/o subscription) |
| VCS log / DVCS (extracted in 253) | module `intellij.platform.vcs.log` / `…vcs.dvcs` |
| Language injection (IntelliLang, 253) | module `intellij.platform.langInjection` |

## Optional dependencies (conditional features)

Use `optional="true"` + a `config-file` that's only loaded when the dependency is present:
```xml
<depends optional="true" config-file="myplugin-git.xml">Git4Idea</depends>
```
`myplugin-git.xml` is a sibling `<idea-plugin>` file in `META-INF/` containing only the
Git-specific extensions. Keeps the core plugin loadable without Git.

## Declaring extensions

```xml
<extensions defaultExtensionNs="com.intellij">
    <toolWindow id="MyTool" anchor="right"
                factoryClass="com.example.MyToolWindowFactory"/>
    <applicationService serviceImplementation="com.example.MyAppService"/>
    <projectService serviceImplementation="com.example.MyProjectService"/>
    <localInspection language="JAVA" shortName="MyInspection"
                     displayName="My inspection" groupName="My group"
                     level="WARNING" enabledByDefault="true"
                     implementationClass="com.example.MyInspection"/>
    <completion.contributor language="JAVA"
                            implementationClass="com.example.MyCompletionContributor"/>
    <postStartupActivity implementation="com.example.MyStartupActivity"/>
    <notificationGroup id="My Notifications" displayType="BALLOON"/>
</extensions>
```
Kotlin-specific extensions use a different namespace:
```xml
<extensions defaultExtensionNs="org.jetbrains.kotlin">
    <supportsKotlinPluginMode supportsK2="true"/>
</extensions>
```

## Declaring actions

```xml
<actions>
    <action id="com.example.MyAction"
            class="com.example.MyAction"
            text="Do The Thing"
            description="Performs the thing">
        <add-to-group group-id="ToolsMenu" anchor="last"/>
        <keyboard-shortcut keymap="$default" first-keystroke="control alt T"/>
    </action>

    <group id="com.example.MyGroup" text="My Group" popup="true">
        <add-to-group group-id="EditorPopupMenu" anchor="last"/>
    </group>
</actions>
```
Common `group-id`s: `MainMenu`, `ToolsMenu`, `EditorPopupMenu`, `ProjectViewPopupMenu`,
`MainToolBar`, `WelcomeScreen.QuickStart`.

## Declaring listeners (preferred over manual subscription)

```xml
<applicationListeners>
    <listener class="com.example.MyAppLifecycleListener"
              topic="com.intellij.ide.AppLifecycleListener"/>
</applicationListeners>
<projectListeners>
    <listener class="com.example.MyFileListener"
              topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
</projectListeners>
```

## Internationalization

Externalize action text and messages into a resource bundle and reference it:
```xml
<resource-bundle>messages.MyPluginBundle</resource-bundle>
```
Then use keys in actions and a `DynamicBundle` subclass in code.
