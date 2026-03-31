plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    id("io.micronaut.application") version "4.6.2"
    id("com.gradleup.shadow") version "8.3.9"
    id("io.micronaut.aot") version "4.6.2"
}

version = "0.1"
group = "com.docuro"

repositories {
    mavenCentral()
}

dependencies {
    // KSP annotation processors
    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    ksp("io.micronaut.security:micronaut-security-annotations")
    ksp("io.micronaut.data:micronaut-data-processor")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")

    // Micronaut core
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut:micronaut-http-client")

    // Security + JWT (JWE encrypted tokens)
    implementation("io.micronaut.security:micronaut-security-jwt")

    // Data + PostgreSQL
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.postgresql:postgresql")

    // Flyway migrations
    implementation("io.micronaut.flyway:micronaut-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Object storage (MinIO / AWS S3)
    implementation("io.micronaut.objectstorage:micronaut-object-storage-aws")

    // Document processing
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Crypto: BCrypt (password hashing) + Bouncy Castle (Argon2id KEK derivation)
    // AES-256-GCM is handled by JVM built-in javax.crypto
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")

    // Test
    testImplementation("io.micronaut:micronaut-http-client")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.docuro.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

kotlin {
    jvmToolchain(21)
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.docuro.*")
    }
    aot {
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}
