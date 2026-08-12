plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.1.0"
}

group = "dev.jlo.gamemodes"
version = "0.1.0-SNAPSHOT"
description = "Deterministic New World-inspired gamemodes for Paper"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("gamemodes")
}


tasks.shadowJar {
    archiveFileName.set("gamemodes-${project.version}.jar")
}

tasks.jar {
    enabled = false
}