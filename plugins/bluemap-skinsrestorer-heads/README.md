# BlueMap SkinsRestorer Heads

On an offline-mode server, BlueMap draws each player's head on the map from the Mojang account with that name, not from the skin the player actually has. BlueMap SkinsRestorer Heads makes BlueMap use the skin each player has in game: the one SkinsRestorer applied, or the one they set with `/skin`.

- **The map shows what players see.** A player's head on BlueMap is drawn from the skin they have in game, in BlueMap's own head style.
- **Changes follow within seconds.** SkinsRestorer applies a skin a moment after joining, and players change it with `/skin` at any time; the head on the map is redrawn each time, in every map.
- **No skin, default head.** A player without a skin gets the game's default skin for their UUID, as everyone in game sees them, instead of no head.
- **Works while the server is stopped.** Heads are written into BlueMap's map files, so a map served statically shows the last head each player had.

No client mods; Paper only. No commands, no configuration.

## Installing

Put the jar in `plugins/` next to BlueMap, and start the server.

**Requirements:** Paper 26.2, Java 25, BlueMap 5. SkinsRestorer, or any plugin that changes players' in-game skins; the plugin reads the skin from the player, so it works with whichever plugin set it. Folia is not supported.

BlueMap needs `accept-download: true` in `plugins/BlueMap/core.conf`, as it does to render anything: the default skins are read from the Minecraft client jar BlueMap downloads. Nothing of Mojang's is shipped with this plugin.

## How it works

- The plugin replaces BlueMap's skin provider, which looks players up on Mojang's servers by UUID. When a player joins, BlueMap asks it for the skin and draws the head as usual.
- Every 5 seconds, each online player's skin is compared with the one their head was drawn from. When it changed, the new skin is downloaded and the head is written into every map's assets (`assets/playerheads/<uuid>.png`), drawn by BlueMap's own head renderer.
- Skins are downloaded only from Mojang's texture server (`textures.minecraft.net`), where every in-game skin points, and only if they are a skin-sized image. A skin that cannot be downloaded is tried again after a minute.
- The plugin keeps no data of its own. Removing it gives BlueMap its own skin provider back.

## License

GPL-3.0.
