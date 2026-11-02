// Applied by every subproject under plugins/. A plugin's own build file only adds what is specific to it.
//
// Per-plugin inputs:
//   version.txt         the released version, written by release-please
//   description         set in the plugin's build.gradle.kts; also used in paper-plugin.yml
//   gradle.properties   modrinth.id and hangar.slug once the plugin is published there

import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    java
    id("com.modrinth.minotaur")
    id("io.papermc.hangar-publish-plugin")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val minecraftVersion = libs.findVersion("minecraft").get().requiredVersion
val repositoryUrl = "https://github.com/fernandossmm/fernandossmm-minecraft-plugins"

group = "me.fernandossm"
version = file("version.txt").readText().trim()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    compileOnly(libs.findLibrary("paper-api").get())

    testImplementation(libs.findLibrary("paper-api").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("mockbukkit").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.test {
    useJUnitPlatform()
}

// paper-plugin.yml uses ${version}, ${description}, ${apiVersion} and ${website}.
tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "description" to project.description.orEmpty(),
        "apiVersion" to minecraftVersion,
        "website" to "$repositoryUrl/tree/main/plugins/${project.name}",
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    val licenseName = "LICENSE-${project.name}"
    from(rootProject.file("LICENSE")) {
        rename { licenseName }
    }
}

val changelogUrl = "$repositoryUrl/blob/main/plugins/${project.name}/CHANGELOG.md"

// What the release workflow runs: build, then upload to every platform the plugin is configured for.
val publishRelease = tasks.register("publishRelease") {
    group = "publishing"
    description = "Builds the plugin and uploads it to Modrinth and Hangar, where configured."
    dependsOn(tasks.build)
}

// Publishing is configured only for plugins that have a Modrinth project or Hangar page.
// The tokens come from the environment, set by the release workflow from repository secrets.
// findProperty, not providers.gradleProperty: the latter does not read a subproject's gradle.properties.
(findProperty("modrinth.id") as String?)?.let { id ->
    modrinth {
        token = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = id
        versionNumber = project.version.toString()
        versionType = "release"
        uploadFile.set(tasks.jar)
        gameVersions.add(minecraftVersion)
        loaders.addAll("paper", "purpur")
        changelog = "See $changelogUrl"
    }
    publishRelease { dependsOn(tasks.named("modrinth")) }
}

(findProperty("hangar.slug") as String?)?.let { slug ->
    hangarPublish {
        publications.register("plugin") {
            version = project.version.toString()
            id = slug
            channel = "Release"
            apiKey = providers.environmentVariable("HANGAR_API_TOKEN")
            changelog = "See $changelogUrl"
            platforms {
                register(Platforms.PAPER) {
                    jar = tasks.jar.flatMap { it.archiveFile }
                    platformVersions = listOf(minecraftVersion)
                }
            }
        }
    }
    publishRelease { dependsOn(tasks.named("publishPluginPublicationToHangar")) }
}
