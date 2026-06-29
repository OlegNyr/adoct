// Интеграция с Bitbucket Server/Data Center (BitbucketClient). Чистая Java-библиотека.
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
    implementation(libs.jacksonDatabind)
    compileOnly(libs.slf4jApi)

    testImplementation(libs.junit)
}
