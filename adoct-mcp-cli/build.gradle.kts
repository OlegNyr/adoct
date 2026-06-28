// CLI-обёртка над MCP-сервером: запуск из командной строки (stdio по умолчанию, --http опционально)
// и сборка нативного бинаря через GraalVM native-image (быстрый старт — практичный stdio-MCP).
plugins {
    application
    alias(libs.plugins.graalvmNative)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":adoct-mcp"))
    implementation(libs.jacksonDatabind)
    // Логи уходят в stderr (stdout занят протоколом stdio).
    runtimeOnly(libs.slf4jSimple)

    testImplementation(libs.junit)
}

application {
    applicationName = "adoct-mcp"
    mainClass.set("ru.gitverse.adoct.mcp.cli.McpCli")
}

graalvmNative {
    // Используем GraalVM из GRAALVM_HOME/JAVA_HOME, без авто-подбора toolchain.
    toolchainDetection.set(false)
    binaries {
        named("main") {
            // Нативный вход НЕ ссылается на confluence_publish_adoc (asciidoctorj/JRuby несовместим с native).
            mainClass.set("ru.gitverse.adoct.mcp.cli.McpCliNative")
            imageName.set("adoct-mcp")
            buildArgs.add("--no-fallback")
            buildArgs.add("--enable-url-protocols=http,https")
        }
    }
}
