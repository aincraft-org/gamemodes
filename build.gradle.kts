plugins {
    java
    id("com.gradleup.shadow") version "9.1.0"
}

group = "dev.jlo.gamemodes"
version = "0.1.0-SNAPSHOT"
description = "Deterministic New World-inspired gamemodes for Paper"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("gamemodes")
    enabled = false
}

tasks.shadowJar {
    archiveFileName.set("gamemodes-${project.version}.jar")
}