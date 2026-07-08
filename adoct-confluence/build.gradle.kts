// Движок конвертации Confluence ↔ AsciiDoc (parser + generate). Чистая Java-библиотека,
// без зависимости от IntelliJ — поэтому тестируется автономно.
plugins {
    id("java-library")
    id("io.freefair.lombok")
    // Публикация в Maven Central (Central Portal): собирает sources/javadoc-jar, подписывает GPG,
    // грузит бандл. Версия объявлена в корневом build.gradle.kts (apply false).
    id("com.vanniktech.maven.publish")
}

// Отдельный namespace для Central (проверяется через GitHub). Остальные модули остаются в pluginGroup;
// координата зависимости в POM плагина совпадает, т.к. оба публикуемых модуля используют эту группу.
group = "io.github.olegnyr"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Javadoc-jar обязателен для Central; отключаем строгий doclint, чтобы русскоязычные комментарии
// и нестандартные теги не роняли сборку.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates("io.github.olegnyr", "adoct-confluence", version.toString())
    pom {
        name.set("adoct-confluence")
        description.set("AsciiDoc ↔ Confluence conversion engine (parser + generate).")
        url.set("https://github.com/OlegNyr/adoct")
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
