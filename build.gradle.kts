// Корневой проект — без кода. Общие настройки для всех модулей; вся IntelliJ-специфика в :adoct-idea.
plugins {
    id("io.freefair.lombok") version "9.2.0" apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.intelliJPlatform) apply false
    alias(libs.plugins.changelog) apply false
}

val release = System.getProperty("release").toBoolean()

allprojects {
    group = providers.gradleProperty("pluginGroup").get()
    version = providers.gradleProperty("pluginVersion").get().let { v -> if (release) v else "$v-SNAPSHOT" }
}
