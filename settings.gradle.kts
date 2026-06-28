import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "AsciiDocTools"

include(
    ":adoct-confluence",
    ":adoct-jira",
    ":adoct-anonymize",
    ":adoct-idea",
)

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.11.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
