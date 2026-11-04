// Console commands the end-to-end suite uses to inspect and drive a real dragon fight.

plugins {
    java
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(project(":multiplayer-dragon-rebirth"))
    compileOnly(project(":end-city-shulker-respawn"))
    compileOnly(libs.griefprevention) { isTransitive = false }
}

tasks.processResources {
    val props = mapOf("apiVersion" to libs.versions.minecraft.get())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
