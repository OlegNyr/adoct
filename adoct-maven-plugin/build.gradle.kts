// Maven-плагин: публикует папку (или один .adoc) в Confluence через движок :adoct-confluence.
// Дескриптор Maven-плагина (META-INF/maven/plugin.xml) генерируется из аннотаций @Mojo/@Parameter
// плагином maven-plugin-development — весь модуль остаётся частью Gradle-сборки.
//
// Плагин применяется через buildscript (а не plugins {}), чтобы форсировать ASM 9.7 на его classpath:
// сканер аннотаций из maven-plugin-tools 3.x читает скомпилированные .class (включая директорию классов
// проектной зависимости :adoct-confluence, собранной под Java 21 / major 65), а его штатный ASM major 65
// не понимает. ASM 9.7 читает Java 21 без изменения нашего таргета.
buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    configurations.classpath {
        resolutionStrategy {
            // ASM 9.7 читает Java 21 (major 65); QDox 2.1.0 парсит record/sealed в исходниках зависимостей.
            force("org.ow2.asm:asm:9.7")
            force("com.thoughtworks.qdox:qdox:2.1.0")
        }
    }
    dependencies {
        // Версия не из version-catalog: аксессор libs недоступен внутри buildscript {}.
        classpath("de.benediktritter.maven-plugin-development:"
            + "de.benediktritter.maven-plugin-development.gradle.plugin:0.4.3")
    }
}

plugins {
    `java-library`
    // Публикация артефакта плагина (jar с META-INF/maven/plugin.xml + sources/javadoc) в Maven Central.
    // Версия объявлена в корневом build.gradle.kts (apply false) — иначе конфликт shared build service.
    id("com.vanniktech.maven.publish")
}

apply(plugin = "de.benediktritter.maven-plugin-development")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Javadoc-jar обязателен для Central; отключаем строгий doclint для русскоязычных комментариев.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

configure<de.benediktritter.maven.plugin.development.MavenPluginDevelopmentExtension> {
    // Короткий goal-prefix: вызывается как `mvn adoct:publish`.
    goalPrefix.set("adoct")
}

// Задачи maven-plugin-development (генерация дескриптора и help-mojo) тянут maven-plugin-tools
// с Velocity/Java-сериализацией и обращаются к Task.project в рантайме — это несовместимо с Gradle
// configuration cache (включён в gradle.properties). Помечаем обе несовместимыми: прогоны с этими
// задачами корректно отключают кэш вместо жёсткого падения; остальная сборка кэш не теряет.
listOf("generateMavenPluginDescriptor", "generateMavenPluginHelpMojoSources").forEach { taskName ->
    tasks.named(taskName) {
        notCompatibleWithConfigurationCache("maven-plugin-tools is not configuration-cache compatible")
    }
}

dependencies {
    // Движок публикации (AdocPublisher + generate.confluence.ConfluenceClient) и его транзитивы.
    implementation(project(":adoct-confluence"))
    // Maven предоставляет свои версии в рантайме — только для компиляции и генерации дескриптора.
    compileOnly(libs.mavenPluginApi)
    compileOnly(libs.mavenPluginAnnotations)

    // compileOnly не наследуется тестами — тест ссылается на MojoFailureException и запускает execute().
    testImplementation(libs.mavenPluginApi)
    testImplementation(libs.junit)
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(group.toString(), "adoct-maven-plugin", version.toString())
    pom {
        name.set("adoct-maven-plugin")
        description.set("Maven plugin that publishes a folder or .adoc file to Confluence (goal adoct:publish).")
        url.set("https://github.com/OlegNyr/adoct")
        // Идиоматично для Maven-плагина (артефакт остаётся jar с plugin.xml).
        packaging = "maven-plugin"
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("OlegNyr")
                name.set("Oleg Nyrkov")
                url.set("https://github.com/OlegNyr")
            }
        }
        scm {
            url.set("https://github.com/OlegNyr/adoct")
            connection.set("scm:git:https://github.com/OlegNyr/adoct.git")
            developerConnection.set("scm:git:ssh://git@github.com/OlegNyr/adoct.git")
        }
    }
}
