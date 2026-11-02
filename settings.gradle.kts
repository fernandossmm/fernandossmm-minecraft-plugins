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
