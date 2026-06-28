import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// Слой плагина IntelliJ: UI, действия, сервисы. Собирает движок (:adoct-confluence),
// Jira (:adoct-jira) и анонимайзер (:adoct-anonymize) в распространяемый плагин.
plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    id("io.freefair.lombok")
}

fun property(key: String) = providers.gradleProperty(key)

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":adoct-confluence"))
    implementation(project(":adoct-jira"))
    implementation(project(":adoct-anonymize"))
    implementation(project(":adoct-mcp"))

    compileOnly(libs.slf4jApi)
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion")) {
            useInstaller = false
        }
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',').filter(String::isNotBlank) })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',').filter(String::isNotBlank) })

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    // Отключаем инструментирование байт-кода: у плагина нет .form-файлов, а тяжёлый
    // ExternalDependencyInstrumentingArtifactTransform перелопачивает всю платформу (~1.3 ГБ)
    // и вешает слабый CI-раннер. См. GITVERSE_PUBLISHING.md.
    instrumentCode = false

    pluginConfiguration {
        version = project.version as String

        // README.md лежит в корне репозитория, а не в каталоге модуля.
        description = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        ideaVersion {
            sinceBuild = property("pluginSinceBuild")
            // Пустой pluginUntilBuild => без верхней границы (provider { null }), иначе значение из gradle.properties.
            // Пустую строку Plugin Verifier не принимает, поэтому именно null. См. GITVERSE_PUBLISHING.md §5.
            val untilBuildProp = property("pluginUntilBuild").orNull?.trim().orEmpty()
            if (untilBuildProp.isEmpty()) {
                untilBuild = provider { null }
            } else {
                untilBuild = provider { untilBuildProp }
            }
        }
    }

    // Plugin Verifier: те же проверки совместимости, что гоняет Marketplace.
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    test {
        // Пробрасываем -Dconfluence.* из JVM Gradle в форк тестов (live-стенд ConfluenceLivePublishIT).
        // Без этого system-property с командной строки в дочерний JVM не попадают и тест скипается.
        listOf("confluence.base", "confluence.url", "confluence.token", "confluence.dest").forEach { key ->
            providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
        }
    }

    // Полностью отключаем цепочку searchable-options. buildSearchableOptions поднимает
    // headless-IDE (падает на CI без libfreetype.so.6 и жрёт память), а зависимые
    // prepare/jarSearchableOptions без неё валятся на отсутствующем каталоге при clean.
    buildSearchableOptions { enabled = false }
    prepareJarSearchableOptions { enabled = false }
    jarSearchableOptions { enabled = false }
}
