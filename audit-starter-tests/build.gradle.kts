plugins {
    java
    id("io.spring.dependency-management") version "1.1.6"
}

group = "io.modelaudit"
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
    testImplementation(project(":audit-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
