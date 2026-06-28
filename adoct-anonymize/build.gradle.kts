// Анонимизация экспортов и сбор баг-репортов (anonymize + bugreport). Чистая Java-библиотека,
// независимая от движка — потенциально может стать отдельным проектом.
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
    implementation(libs.jsoup)
    implementation(libs.jacksonDatabind)
    implementation(libs.commonsLang3)
    implementation(libs.commonsIo)
    implementation(libs.datafaker)
    compileOnly(libs.slf4jApi)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.slf4jApi)
}
