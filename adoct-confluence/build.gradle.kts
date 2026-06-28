// Движок конвертации Confluence ↔ AsciiDoc (parser + generate). Чистая Java-библиотека,
// без зависимости от IntelliJ — поэтому тестируется автономно.
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
    // asciidoctorj отдаём наружу: типы org.asciidoctor.ast.* видны в слое плагина.
    api(libs.asciidoctorj)

    implementation(libs.jsoup)
    implementation(libs.jacksonDatabind)
    implementation(libs.commonsLang3)
    implementation(libs.commonsIo)
    implementation(libs.httpclient)
    implementation(libs.pngj)
    compileOnly(libs.slf4jApi)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.slf4jApi)
}
