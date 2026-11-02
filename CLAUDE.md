# Conventions

- One Gradle subproject per plugin under `plugins/<name>/`. Plugins never depend on each other at runtime; shared code, if any, is compiled into each jar.
- Build configuration common to all plugins lives in `build-logic/src/main/kotlin/minecraft-plugin.gradle.kts`; a plugin's `build.gradle.kts` only adds what is specific to it.
- Every dependency version is in `gradle/libs.versions.toml`. `minecraft` there is the single source of the targeted version (api-version, Modrinth/Hangar game versions, dev server).
- Commit messages are Conventional Commits; release-please derives versions and changelogs from them. Never edit `version.txt`, `CHANGELOG.md` or `.release-please-manifest.json` by hand except when adding a plugin.
- Java 25, Paper API only (no NMS). Main plugin classes are not `final` (MockBukkit subclasses them).
- Verify with `./gradlew build`; check behaviour on the dev server with `./gradlew runServer`.
- Do not commit or push; stage changes for review.
