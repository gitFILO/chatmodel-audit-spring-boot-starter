plugins {
    `java-library`
}

group = "io.github.gitfilo"
version = project.findProperty("version") as String? ?: "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

repositories {
    mavenCentral()
}

// Spring Boot 무관 — provider-agnostic core (ADR-006, vault 12-langchain4j-compatibility)
dependencies {
    api("io.micrometer:micrometer-observation:1.14.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
