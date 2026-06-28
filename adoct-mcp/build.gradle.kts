// MCP-сервер (Model Context Protocol) поверх движка Confluence и клиента Jira.
// Чистая Java-библиотека: минимальная реализация JSON-RPC/MCP на JDK HttpServer,
// без внешнего MCP SDK и веб-контейнера (см. решение по транспорту в плане).
plugins {
    id("java-library")
    id("io.freefair.lombok")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Движок экспорта Confluence и клиент Jira — тулы вызывают их напрямую.
    api(project(":adoct-confluence"))
    api(project(":adoct-jira"))

    implementation(libs.jacksonDatabind)
    compileOnly(libs.slf4jApi)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.slf4jApi)
}
