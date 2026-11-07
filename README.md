# fernandossmm's Minecraft plugins

Small Paper plugins that fill gaps no maintained plugin covers. Each one is independent: install only the ones you want, and none needs another from this repository.

| Plugin | What it does | Status |
|---|---|---|
| [Multiplayer Dragon Rebirth](plugins/multiplayer-dragon-rebirth) | Gives every player their own Ender Dragon fight, with its egg and full experience | not released yet |
| [End City Shulker Respawn](plugins/end-city-shulker-respawn) | Shulkers come back to End cities, on the spots where they were, some time after they die | not released yet |
| [Shiny Shulkers](plugins/shiny-shulkers) | End city shulkers have a 1 in 50 chance to be generated in a dye colour instead of purple | not released yet |
| [BlueMap GriefPrevention](plugins/bluemap-griefprevention) | GriefPrevention claims on BlueMap, one shape per area, in each owner's locator bar colour | not released yet |
| [BlueMap Banner Markers](plugins/bluemap-banner-markers) | A name tag on a banner makes a waypoint: the banner itself on BlueMap, its name floating over it | not released yet |
| [DiscordSRV fail-closed](plugins/discordsrv-fail-closed) | Refuses every login while DiscordSRV is down, so its Discord account check can't fail open | not released yet |

## Supported versions

The current target only: **Paper 26.2**, Java 25. Older Minecraft versions are not supported; a plugin moves to a new Minecraft version in a release of its own.

## Building

Needs JDK 25 (Gradle downloads one if none is installed).

```sh
./gradlew build          # every plugin; jars in plugins/<name>/build/libs/
./gradlew runServer      # a local Paper server in dev/run/ with every plugin loaded
```

`runServer` accepts Mojang's EULA for that local server through Paper's command-line flag. Delete `dev/run/` to start from a fresh world.

## Testing

- `./gradlew build` runs the unit tests and the MockBukkit tests of every plugin.
- `dev/e2e` is an end-to-end suite against a real Paper server: bots join as players (mineflayer, through ViaVersion and ViaBackwards, since bots speak the previous Minecraft version), and a test-only harness plugin reports the server's state over RCON. It needs Node 20 or later and takes about ten minutes:

  ```sh
  cd dev/e2e && npm ci && npm test                     # every plugin's suite
  npm test -- end-city-shulker-respawn                 # one plugin's suite
  ```

## Layout

```
build-logic/              conventions every plugin applies (minecraft-plugin): toolchain, Paper API, tests, publishing
gradle/libs.versions.toml one version of every dependency, shared by all plugins
plugins/<name>/           one plugin: build.gradle.kts, version.txt, gradle.properties, src/, README.md, CHANGELOG.md
dev/                      the runServer setup
dev/e2e/                  end-to-end suite (run.mjs) and its harness plugin
```

## Releasing

Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `feat!:` …). Release-please reads them per plugin directory and keeps one release PR open for each plugin with unreleased changes. Merging that PR bumps `version.txt`, updates the plugin's `CHANGELOG.md`, tags `<plugin>-v<version>` and publishes:

- the jar is attached to the GitHub release;
- it is uploaded to Modrinth and Hangar if the plugin's `gradle.properties` sets `modrinth.id` / `hangar.slug` and the repository has the `MODRINTH_TOKEN` / `HANGAR_API_TOKEN` secrets.

## Adding a plugin

1. Copy `plugins/multiplayer-dragon-rebirth` to `plugins/<name>` and replace its sources, `description`, `paper-plugin.yml` and README. Set `version.txt` to `0.0.0`.
2. Add `plugins/<name>` to `release-please-config.json` (`"component": "<name>"`) and to `.release-please-manifest.json` (`"0.0.0"`).
3. Add any third-party plugin it depends on to `dev/build.gradle.kts` (`downloadPlugins`) and, if compiled against, to the version catalog.

## License

[GPL-3.0](LICENSE).
