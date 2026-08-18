import org.gradle.api.publish.maven.MavenPublication

plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.1.0"
}

group = "dev.jlo.gamemodes"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()
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
    systemProperty("ci.workflow", rootProject.file(".github/workflows/ci.yml").absolutePath)
    systemProperty("project.root", rootProject.projectDir.absolutePath)
    systemProperty(
        "ci.pom",
        layout.buildDirectory.file("publications/maven/pom-default.xml").get().asFile.absolutePath,
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "gamemodes"
            artifact(tasks.shadowJar) {
                builtBy(tasks.shadowJar)
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aincraft-org/gamemodes")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

tasks.jar {
    archiveBaseName.set("gamemodes")
    enabled = false
}

tasks.shadowJar {
    archiveFileName.set("gamemodes-${project.version}.jar")
}