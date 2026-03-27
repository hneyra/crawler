plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "crawler"
version = "0.0.1-SNAPSHOT"
description = "crawler"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    mavenCentral()
}

dependencies {
    // Spring Batch
    implementation("org.springframework.boot:spring-boot-starter-batch")

    // Spring Data JPA + PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // WebClient (Spring WebFlux)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Jsoup (HTML parsing)
    implementation("org.jsoup:jsoup:1.18.3")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // JsonPath
    implementation("com.jayway.jsonpath:json-path:2.9.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.batch:spring-batch-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // WireMock
    testImplementation("org.wiremock:wiremock:3.13.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
