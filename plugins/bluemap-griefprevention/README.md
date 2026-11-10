# BlueMap GriefPrevention

Draws GriefPrevention claims on BlueMap, as flat coloured areas seen through the terrain, in a "Claims" layer shown by default.

- **One shape per area, not a pile of rectangles.** A player's claims that touch along an edge or overlap become a single outline; unclaimed land they enclose shows as a hole. Claims that meet only at a corner stay separate shapes, and claims of different owners are never merged.
- **Each player's colour is their locator bar colour**: the same colour Minecraft gives that player on the locator bar, computed the same way from their UUID. Nobody picks it and it never changes. Admin claims are bright purple (`#BF00FF`).
- **The owner is named only on click.** Clicking a claim opens a popup with "Claim of NAME" and the size, such as "3 claims · 300 blocks". BlueMap shows nothing when hovering over an area; in its marker list, claims are listed as "Claim" (or "Admin claim").
- **Drawn at ground level**: at the height of the blocks the claim was marked on, not at the bottom of its protection near bedrock, so it sits on the terrain in the 3D view.
- Top-level claims only; subdivisions lie inside their claim and are not drawn.

The map follows claims as they are created, resized, extended, transferred, deleted or expire, within a tick. Everything is also redrawn every 5 minutes, to catch changes made without an event (some commands and other plugins change claims silently). No client mods; Paper only.

## Installing

Put the jar in `plugins/` next to GriefPrevention and BlueMap, and start the server.

**Requirements:** Paper 26.3, Java 25, GriefPrevention 16.18 or later, BlueMap 5. Folia is not supported.

**Minecraft versions:** 0.2.x for 26.3, 0.1.x for 26.2. Each release's jar is on the [GitHub releases](https://github.com/fernandossmm/fernandossmm-minecraft-plugins/releases).

## Configuration

`plugins/BlueMapGriefPrevention/config.yml`:

| Option | Default | Meaning |
|---|---|---|
| `worlds` | `[]` | Worlds whose claims are drawn, by name; empty means every world |
| `layer.label` | `Claims` | The layer's name in BlueMap's marker menu |
| `layer.hidden-by-default` | `false` | Whether the layer starts switched off in the browser |
| `owner` | `click` | Where the owner's name appears: `click` (popup only), `list` (also in BlueMap's marker list), `none` |
| `claims.player` / `claims.admin` | `true` | Which kinds of claims are drawn |
| `messages.*` | English | The texts on the map: "Claim", "Claim of NAME", "Admin claim", the size line and the thousands separator. The default `config.yml` lists them all; translate them there |
| `colors.admin` | `#BF00FF` | The colour of admin claims |
| `colors.fill-opacity` / `colors.line-opacity` | `0.2` / `0.8` | Opacity of the area and of its outline |
| `colors.line-width` | `2` | Outline width, in pixels |
| `resync-interval` | `5m` | How often everything is redrawn; a number followed by `s`, `m` or `h` |

## Commands

Permission `bluemapgriefprevention.admin` (operators by default).

| Command | Effect |
|---|---|
| `/bmgp resync` | Redraws every claim now |
| `/bmgp reload` | Reloads `config.yml` and redraws |

## How it works

BlueMap forgets markers added by plugins when it starts or reloads, so the plugin draws them again each time; after that it redraws only the shapes that changed. With BlueMap's default file storage, BlueMap also writes them to `bluemap/web/maps/<world>/live/markers.json`. **A map served statically from those files while the game server is stopped still shows the claims**, as they were when the server stopped.

The outlines are traced on the claims' own corners (no block-by-block scan), so a claim of any size costs the same. The plugin keeps no data of its own.

## License

GPL-3.0.
