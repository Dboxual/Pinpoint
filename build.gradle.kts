plugins {
    java
}

group = "com.pinpoint"
version = "1.2.4"
description = "WaypointSystem"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(project.properties)
    }
}

tasks.jar {
    archiveBaseName.set("Pinpoint")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")
}
