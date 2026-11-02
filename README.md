# fernandossmm's Minecraft plugins

Small Paper plugins that fill gaps no maintained plugin covers. Each one is independent: install only the ones you want, and none needs another from this repository.

| Plugin | What it does | Status |
|---|---|---|
| [dragon-rebirth](plugins/dragon-rebirth) | Respawns the Ender Dragon for every player who reaches the End for the first time, with its egg and full experience | in development |

Planned: `city-shulkers` (shulkers return to End cities), `bluemap-griefprevention` (GriefPrevention claims on BlueMap), `bluemap-banner-markers` (named banners become BlueMap markers).

## Supported versions

The current target only: **Paper 26.2**, Java 25. Older Minecraft versions are not supported; a plugin moves to a new Minecraft version in a release of its own.

## Building

Needs JDK 25 (Gradle downloads one if none is installed).

```sh
./gradlew build          # every plugin; jars in plugins/<name>/build/libs/
./gradlew runServer      # a local Paper server in dev/run/ with every plugin loaded
```

`runServer` accepts Mojang's EULA for that local server through Paper's command-line flag. Delete `dev/run/` to start from a fresh world.

## Layout

```
build-logic/              conventions every plugin applies (minecraft-plugin): toolchain, Paper API, tests, publishing
gradle/libs.versions.toml one version of every dependency, shared by all plugins
plugins/<name>/           one plugin: build.gradle.kts, version.txt, gradle.properties, src/, README.md, CHANGELOG.md
dev/                      the runServer setup
```

## Releasing

Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `feat!:` …). Release-please reads them per plugin directory and keeps one release PR open for each plugin with unreleased changes. Merging that PR bumps `version.txt`, updates the plugin's `CHANGELOG.md`, tags `<plugin>-v<version>` and publishes:

- the jar is attached to the GitHub release;
- it is uploaded to Modrinth and Hangar if the plugin's `gradle.properties` sets `modrinth.id` / `hangar.slug` and the repository has the `MODRINTH_TOKEN` / `HANGAR_API_TOKEN` secrets.

## Adding a plugin

1. Copy `plugins/dragon-rebirth` to `plugins/<name>` and replace its sources, `description`, `paper-plugin.yml` and README. Set `version.txt` to `0.0.0`.
2. Add `plugins/<name>` to `release-please-config.json` (`"component": "<name>"`) and to `.release-please-manifest.json` (`"0.0.0"`).
3. Add any third-party plugin it depends on to `dev/build.gradle.kts` (`downloadPlugins`) and, if compiled against, to the version catalog.

## License

[GPL-3.0](LICENSE).
