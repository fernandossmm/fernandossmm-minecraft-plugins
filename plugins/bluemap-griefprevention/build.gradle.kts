plugins {
    id("minecraft-plugin")
}

description = "Shows GriefPrevention claims on BlueMap, as one shape per player's connected land, in their locator bar colour."

dependencies {
    compileOnly(libs.griefprevention) { isTransitive = false }
    compileOnly(libs.bluemap.api)
    // The sink is tested against BlueMap's real marker classes.
    testImplementation(libs.bluemap.api)
}
