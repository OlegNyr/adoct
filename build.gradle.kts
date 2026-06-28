import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType



fun property(key: String) = providers.gradleProperty(key)

plugins {
    id("java") // Java support
    id("maven-publish")
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog)
//    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    id("io.freefair.lombok") version "9.2.0"
}


val release = System.getProperty("release").toBoolean()

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()
version = if (release) version else "$version-SNAPSHOT"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    // PlantUML для локального рендеринга
//    implementation("net.sourceforge.plantuml:plantuml:1.2023.9")

    // AsciidoctorJ — парсинг AsciiDoc в AST для генерации Confluence storage format
    // (модуль ru.gitverse.adoct.generate). Тянет JRuby.
    implementation(libs.asciidoctorj)

    implementation("ar.com.hjg:pngj:2.0.1")

    // Генерация осмысленных подменных данных для анонимайзера экспорта (русская локаль)
    implementation("net.datafaker:datafaker:2.4.2")

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

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

//        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
//        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
//            with(changelog) {
//                renderItem(
//                    (getOrNull(pluginVersion) ?: getUnreleased())
//                        .withHeader(false)
//                        .withEmptySections(false),
//                    Changelog.OutputType.HTML,
//                )
//            }
//        }

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
    // recommended() сам подбирает IDE по объявленному диапазону since/until-build (вкл. 2025.3).
    // Запуск: ./gradlew verifyPlugin (скачивает целевые IDE при первом прогоне).
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
    // Плагину индекс поиска по настройкам не нужен.
    buildSearchableOptions { enabled = false }
    prepareJarSearchableOptions { enabled = false }
    jarSearchableOptions { enabled = false }
}