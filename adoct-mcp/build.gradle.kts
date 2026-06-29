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
    // Движок экспорта Confluence и клиенты Jira/Bitbucket — тулы вызывают их напрямую.
    api(project(":adoct-confluence"))
    api(project(":adoct-jira"))
    api(project(":adoct-bitbucket"))

    implementation(libs.jacksonDatabind)
    compileOnly(libs.slf4jApi)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.slf4jApi)
}

tasks.test {
    // Пробрасываем -Dmcp.* в форк тестов (live-смоук McpLiveIT против локального стенда).
    listOf("mcp.host", "mcp.token", "mcp.pageId").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}
