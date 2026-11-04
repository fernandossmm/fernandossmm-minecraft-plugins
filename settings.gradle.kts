rootProject.name = "fernandossmm-minecraft-plugins"

pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Downloads the JDK 25 toolchain on machines that do not have one.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        // Plugins published on Modrinth (GriefPrevention), and nothing else.
        exclusiveContent {
            forRepository { maven("https://api.modrinth.com/maven") }
            filter { includeGroup("maven.modrinth") }
        }
        // BlueMap's API, and nothing else.
        exclusiveContent {
            forRepository { maven("https://repo.bluecolored.de/releases") }
            filter { includeGroup("de.bluecolored") }
        }
    }
}

// Every directory under plugins/ with a build file is a plugin subproject, named after its directory.
file("plugins").listFiles()
    ?.filter { it.resolve("build.gradle.kts").isFile }
    ?.sortedBy { it.name }
    ?.forEach { dir ->
        include(dir.name)
        project(":${dir.name}").projectDir = dir
    }

include("dev")
// A test-only plugin for the end-to-end suite in dev/e2e; never published.
include("e2e-harness")
project(":e2e-harness").projectDir = file("dev/e2e/harness")
