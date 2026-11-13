// Local Paper servers with every plugin in this repository:
//   ./gradlew runServer      for trying things by hand; lives in dev/run/
//   ./gradlew runE2eServer   for the end-to-end suite in dev/e2e (started by it, not by hand); lives in dev/e2e/run/
// Both directories are ignored by git; delete them to start from a fresh world.

import xyz.jpenilla.runpaper.task.RunServer

plugins {
    alias(libs.plugins.run.paper)
}

val pluginProjects = rootProject.subprojects.filter { it.projectDir.parentFile.name == "plugins" }
pluginProjects.forEach { evaluationDependsOn(it.path) }
evaluationDependsOn(":e2e-harness")

// Refuses every login unless DiscordSRV is running with a Discord bot, which these servers do not have: it is
// left out of them and loaded only by its own suite's servers (runE2eFailClosedServer below).
val failClosed = project(":discordsrv-fail-closed")
val failClosedJar = failClosed.tasks.named<Jar>("jar").flatMap { it.archiveFile }

fun RunServer.withOurPlugins() {
    minecraftVersion(libs.versions.minecraft.get())
    pluginProjects.filter { it != failClosed }.forEach { pluginJars(it.tasks.named<Jar>("jar").flatMap { jar -> jar.archiveFile }) }
    jvmArgs("-Xms1G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}

// The test bots speak the protocol of the previous Minecraft version; these translate it.
fun RunServer.withBotTranslation() {
    downloadPlugins {
        url("https://cdn.modrinth.com/data/P1OZGk5p/versions/Fkfcr89K/ViaVersion-5.12.2.jar")
        url("https://cdn.modrinth.com/data/NpvuJQoq/versions/WFO37e8T/ViaBackwards-5.12.2.jar")
    }
}

tasks.runServer {
    withOurPlugins()
    runDirectory(layout.projectDirectory.dir("run").asFile)
    downloadPlugins {
        // Third-party plugins that ours integrate with, pinned to the version in gradle/libs.versions.toml.
        url("https://cdn.modrinth.com/data/O4o4mKaq/versions/dGfCZHqk/GriefPrevention.jar") // 16.18.7
        url("https://cdn.modrinth.com/data/swbUV1cr/versions/uHQIrUdf/bluemap-5.29-paper.jar") // for bluemap-griefprevention
        url("https://cdn.modrinth.com/data/TsLS8Py5/versions/ziIzW16f/SkinsRestorer.jar") // 15.12.6, for bluemap-banner-markers and bluemap-skinsrestorer-heads
        url("https://cdn.modrinth.com/data/Cz6msz34/versions/MdUKntqn/AxGraves-1.32.2.jar") // 1.32.2, for axgraves-owner-grace
    }
}

tasks.register<RunServer>("runE2eServer") {
    group = "verification"
    description = "Starts the server the end-to-end suite drives (dev/e2e/run.mjs starts it)."
    withOurPlugins()
    runDirectory(layout.projectDirectory.dir("e2e/run").asFile)
    pluginJars(project(":e2e-harness").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    withBotTranslation()
    downloadPlugins {
        url("https://cdn.modrinth.com/data/O4o4mKaq/versions/dGfCZHqk/GriefPrevention.jar") // 16.18.7
        url("https://cdn.modrinth.com/data/swbUV1cr/versions/uHQIrUdf/bluemap-5.29-paper.jar") // 5.29
        url("https://cdn.modrinth.com/data/TsLS8Py5/versions/ziIzW16f/SkinsRestorer.jar") // 15.12.6, offline-mode skins
        url("https://cdn.modrinth.com/data/Cz6msz34/versions/MdUKntqn/AxGraves-1.32.2.jar") // 1.32.2
    }
}

// DiscordSRV fail-closed's suite (dev/e2e/discordsrv-fail-closed.mjs): that plugin alone, without and with
// DiscordSRV. The other plugins and the harness are left out; vanilla commands are enough for its checks.
fun RunServer.failClosedServer() {
    group = "verification"
    minecraftVersion(libs.versions.minecraft.get())
    runDirectory(layout.projectDirectory.dir("e2e/run").asFile)
    pluginJars(failClosedJar)
    withBotTranslation()
    jvmArgs("-Xms1G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}

tasks.register<RunServer>("runE2eFailClosedServer") {
    description = "Starts DiscordSRV fail-closed's end-to-end server without DiscordSRV (dev/e2e/run.mjs starts it)."
    failClosedServer()
}

tasks.register<RunServer>("runE2eFailClosedDiscordServer") {
    description = "Starts DiscordSRV fail-closed's end-to-end server with DiscordSRV (dev/e2e/run.mjs starts it)."
    failClosedServer()
    downloadPlugins {
        url("https://cdn.modrinth.com/data/UmLGoGij/versions/ATlquwiT/DiscordSRV-Build-1.30.5.jar") // 1.30.5
    }
}
