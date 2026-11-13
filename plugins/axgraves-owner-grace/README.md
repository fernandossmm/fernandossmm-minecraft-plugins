# AxGraves Owner Grace

A grave from [AxGraves](https://modrinth.com/plugin/axgraves) stays its owner's for a few minutes after they die: only they can open it, take its experience or empty it. After that, anyone can. AxGraves on its own only has "only the owner, always" or "anyone, at once".

- **The owner** opens their grave at any time.
- **Anyone else** is told whose grave it is and how long is left, for example "This is Steve's grave. You can open it in 3:12.", until the grace period ends.
- **Staff** with `axgravesownergrace.bypass` (or AxGraves' own `axgraves.admin`) open any grave at once.
- The time runs from the death, in real time, also while the server is stopped: AxGraves keeps each grave's time of death across restarts.

No client mods; Paper only.

## Installing

Put the jar in `plugins/` next to AxGraves, set `interact-only-own: false` in `plugins/AxGraves/config.yml` and start the server. With `interact-only-own: true`, AxGraves turns everyone but the owner away before this plugin is asked, so graves stay private for good; the plugin warns about it on startup.

**Requirements:** Paper 26.3, Java 25, AxGraves 1.32 or later. Folia is not supported.

**Minecraft versions:** 0.x for 26.3. Each release's jar is on the [GitHub releases](https://github.com/fernandossmm/fernandossmm-minecraft-plugins/releases).

## Configuration

`plugins/AxGravesOwnerGrace/config.yml`:

| Option | Default | Meaning |
|---|---|---|
| `grace-period` | `5m` | How long a grave stays its owner's: a number followed by `s`, `m` or `h`; `0` opens graves to everyone at once |
| `messages.locked` | "This is `<owner>`'s grave. You can open it in `<time>`." in red | What someone else is told, in [MiniMessage](https://docs.papermc.io/adventure/minimessage/); `<time>` is minutes:seconds left. Empty sends nothing |

Changes apply after a restart. How long graves last at all is AxGraves' `despawn-time-seconds`.

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `axgravesownergrace.bypass` | operators | Opening any grave at once |

## How it works

AxGraves fires its `GraveInteractEvent` when someone clicks a grave, before it opens the grave, gives its stored experience or empties it into the player's inventory (a sneaking click). The plugin cancels that event for anyone but the owner and staff while the grave is younger than the grace period, using the time of death AxGraves stores with each grave. There are no commands, no data files and no background tasks.

## License

GPL-3.0.
