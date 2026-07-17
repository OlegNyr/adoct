// Самодостаточный исполняемый fat-JAR MCP-сервера: `java -jar adoct-mcp.jar` (stdio по умолчанию,
// `--http` опционально). В отличие от native-образа (:adoct-mcp-cli) включает ВЕСЬ набор тулов,
// в т.ч. confluence_publish_adoc (asciidoctorj/JRuby). Точка входа — McpCli из :adoct-mcp-cli.
plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Приносит McpCli + сервер (:adoct-mcp) + движок и транзитивы.
    implementation(project(":adoct-mcp-cli"))
    // Логи в stderr (stdout занят stdio-протоколом).
    runtimeOnly(libs.slf4jSimple)
}

tasks.shadowJar {
    archiveBaseName.set("adoct-mcp")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "io.github.adoct.mcp.cli.McpCli"
    }
    // Склеиваем META-INF/services (ServiceLoader) из всех зависимостей.
    mergeServiceFiles()
}

// `./gradlew :adoct-mcp-jar:build` сразу собирает исполняемый JAR.
tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
