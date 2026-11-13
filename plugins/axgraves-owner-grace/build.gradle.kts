plugins {
    id("minecraft-plugin")
}

description = "Graves from AxGraves stay their owner's for a few minutes, then anyone can open them."

dependencies {
    // Compiled against AxGraves' GraveInteractEvent and Grave; never bundled.
    compileOnly(libs.axgraves) { isTransitive = false }
}
