plugins {
    id("minecraft-plugin")
}

description = "Name a banner with a name tag and it becomes a waypoint: a BlueMap marker drawn as the banner itself, with its name over it in the world."

dependencies {
    // Optional: only to ask whether a player may build where the banner is.
    compileOnly(libs.griefprevention) { isTransitive = false }
    compileOnly(libs.bluemap.api)
    testImplementation(libs.bluemap.api)
}
