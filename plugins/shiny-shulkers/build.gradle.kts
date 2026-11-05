plugins {
    id("minecraft-plugin")
}

description = "End city shulkers have a small chance to be generated in a dye colour instead of the usual purple."

dependencies {
    // Optional: respawned city shulkers get their own roll. Same repository, so built together.
    compileOnly(project(":end-city-shulker-respawn"))
    testImplementation(project(":end-city-shulker-respawn"))
}
