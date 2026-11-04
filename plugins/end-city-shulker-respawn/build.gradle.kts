plugins {
    id("minecraft-plugin")
}

description = "Shulkers come back to End cities, on the spots where they were, some time after they die."

dependencies {
    compileOnly(libs.griefprevention) { isTransitive = false }
}
