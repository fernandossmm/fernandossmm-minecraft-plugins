// A local Paper server with every plugin in this repository and their third-party dependencies:
//   ./gradlew runServer
// The server lives in dev/run/ (ignored by git); delete it to start from a fresh world.

plugins {
    alias(libs.plugins.run.paper)
}

val pluginProjects = rootProject.subprojects.filter { it != project }
pluginProjects.forEach { evaluationDependsOn(it.path) }

tasks.runServer {
    minecraftVersion(libs.versions.minecraft.get())
    runDirectory(layout.projectDirectory.dir("run").asFile)
    pluginProjects.forEach { pluginJars(it.tasks.named<Jar>("jar").flatMap { jar -> jar.archiveFile }) }
    downloadPlugins {
        // Third-party plugins that ours depend on are added here, pinned, together with the plugin that needs them.
    }
    jvmArgs("-Xms1G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}
