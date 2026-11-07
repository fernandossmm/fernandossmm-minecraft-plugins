plugins {
    id("minecraft-plugin")
}

description = "BlueMap's player heads show the skin each player has in game, such as the one SkinsRestorer gives them on an offline-mode server."

dependencies {
    compileOnly(libs.bluemap.api)
    testImplementation(libs.bluemap.api)
}
