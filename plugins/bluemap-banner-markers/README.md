# BlueMap Banner Markers

Name a banner with a name tag and it becomes a **waypoint**: the name floats over the banner, and BlueMap shows the banner itself, in its real colours and patterns, where it stands.

- **Making one:** name a name tag in an anvil and right-click a placed banner (standing or on a wall) with it. The tag is used up.
- **On the map:** a "Waypoints" layer, shown by default. Hovering a waypoint shows its name; clicking it shows who placed it, with their face.
- **Renaming:** right-click your own waypoint banner with another named tag. Nobody else can rename it; they are told whose it is.
- **Removing:** break the banner, by hand, with an explosion, or by breaking what it hangs on.
- **Limits:** 5 waypoints per player, and names of up to 24 characters (both configurable). A refused name tag is never used up.
- With GriefPrevention, a player needs build trust where the banner is.

No client mods; Paper only.

## Installing

Put the jar in `plugins/` next to BlueMap, and start the server.

**Requirements:** Paper 26.2, Java 25, BlueMap 5. Optional: GriefPrevention 16.18 or later, SkinsRestorer (for faces on offline-mode servers). Folia is not supported.

BlueMap needs `accept-download: true` in `plugins/BlueMap/core.conf`, as it does to render anything: the banner icons and default faces are drawn from the Minecraft textures BlueMap downloads. Nothing of Mojang's is shipped with this plugin.

## Configuration

`plugins/BlueMapBannerMarkers/config.yml`:

| Option | Default | Meaning |
|---|---|---|
| `worlds` | `[]` | Worlds where banners can become waypoints, by name; empty means every world |
| `max-per-player` | `5` | How many waypoints each player can have |
| `max-name-length` | `24` | The longest name a waypoint can have, in characters (at most 50, as in an anvil) |
| `layer.label` | `Waypoints` | The layer's name in BlueMap's marker menu |
| `layer.hidden-by-default` | `false` | Whether the layer starts switched off in the browser |

## Commands and permissions

| Permission | Default | Allows |
|---|---|---|
| `bluemapbannermarkers.use` | everyone | Making waypoints |
| `bluemapbannermarkers.unlimited` | nobody | No limit on waypoints |
| `bluemapbannermarkers.admin` | operators | The commands below |

| Command | Effect |
|---|---|
| `/bbm list [player]` | Every waypoint, or one player's, with its id |
| `/bbm delete <id>` | Removes a waypoint and its name; the banner stays |
| `/bbm scale <1\|2> [id]` | Shows a waypoint's icon at normal or double size. Without an id: the banner you are looking at |
| `/bbm reload` | Reloads `config.yml` |

## How it works

- **The icon** is the banner's front, 20×40 pixels (40×80 when enlarged), drawn as the game draws it: each pattern's texture tinted with its dye, over the base colour.
- **The face** is cut from the skin the player has in game, with the hat layer. On offline-mode servers that is the skin SkinsRestorer applied. A player with no skin gets the game's own default skin for their UUID. Faces are saved when a waypoint is made and refreshed when its owner joins with a different skin.
- **Images and markers** are written into each BlueMap map's own files, so a map served statically while the game server is stopped still shows them.
- **The name over the banner** is a text display that is never saved with the world. It is created when its chunk loads, so removing the plugin leaves nothing behind.
- **Banners removed without an event** (by `/setblock`, or by other plugins) are noticed when their chunk loads, or within a minute while it stays loaded.
- **Waypoints** are kept in `waypoints.json`, and faces in `faces/`.

## License

GPL-3.0.
