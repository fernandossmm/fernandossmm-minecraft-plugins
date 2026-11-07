# fernandossmm's Minecraft plugins

Small Paper plugins that fill gaps with no current public solution. Each one is independent: install only the ones you want, and none needs another from this repository.

| Plugin                                                             | What it does                                                                                                  | Versions |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------- | -------- |
| [Multiplayer Dragon Rebirth](plugins/multiplayer-dragon-rebirth)   | Gives every player their own Ender Dragon fight, with its egg and full experience                             | 26.2     |
| [End City Shulker Respawn](plugins/end-city-shulker-respawn)       | Shulkers come back to End cities, on the spots where they were, some time after they die                      | 26.2     |
| [Shiny Shulkers](plugins/shiny-shulkers)                           | End city shulkers have a small chance to be generated in a different color instead of purple                  | 26.2     |
| [BlueMap GriefPrevention](plugins/bluemap-griefprevention)         | GriefPrevention claims on BlueMap, one shape per area, in each owner's colour                                 | 26.2     |
| [BlueMap Banner Markers](plugins/bluemap-banner-markers)           | A name tag on a banner makes a waypoint on Bluemap, showing the banner pattern                                | 26.2     |
| [BlueMap SkinsRestorer Heads](plugins/bluemap-skinsrestorer-heads) | BlueMap's player heads show the skin each player has in game, such as SkinsRestorer's on offline-mode servers | 26.2     |
| [DiscordSRV fail-closed](plugins/discordsrv-fail-closed)           | Refuses every login while DiscordSRV is down, so its Discord account check can't fail open                    | 26.2     |

## Supported versions

Currently 26.2. Older version support is not planned.

## Building

Needs JDK 25 (Gradle downloads one if none is installed).

```sh
./gradlew build          # every plugin; jars in plugins/<name>/build/libs/
./gradlew runServer      # a local Paper server in dev/run/ with every plugin loaded
```

`runServer` accepts Mojang's EULA for that local server through Paper's command-line flag. Delete `dev/run/` to start from a fresh world.

## Testing

- `./gradlew build` runs the unit tests and the MockBukkit tests of every plugin.
- `dev/e2e` is an end-to-end suite against a real Paper server: bots join as players (mineflayer, through ViaVersion and ViaBackwards, since bots don't support newest versions), and a test-only harness plugin reports the server's state over RCON. It needs Node 20.

    ```sh
    cd dev/e2e && npm ci && npm test                     # every plugin's suite
    npm test -- end-city-shulker-respawn                 # one plugin's suite
    ```

    The server listens on 25566 (RCON 25575, BlueMap 8100). To run a second checkout's suite at the same time, give it other ports with `E2E_PORT`, `E2E_RCON_PORT` and `E2E_WEB_PORT`.

## License

[GPL-3.0 copyleft](LICENSE).
