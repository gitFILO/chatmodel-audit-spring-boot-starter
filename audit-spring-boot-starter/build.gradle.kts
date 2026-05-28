plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.6"
}

group = "io.github.gitfilo"
version = project.findProperty("version") as String? ?: "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.5")
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.0")
    }
}

dependencies {
    api(project(":audit-core"))

    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.ai:spring-ai-commons")

    implementation("org.flywaydb:flyway-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
