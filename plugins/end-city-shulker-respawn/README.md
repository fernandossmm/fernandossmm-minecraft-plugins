# End City Shulker Respawn

End cities are generated once: after the first explorers kill their shulkers, a city stays empty forever, and shulker shells can only come from farms. End City Shulker Respawn brings each city's shulkers back, one by one, some time after they die.

- The first time each chunk of an End city loads, the plugin records the city's shulkers there: where each sits and which block face it is stuck to. These are the city's **spots**. Ships are part of their city.
- When one of those shulkers dies, its spot's **cooldown** starts: real time, 16 hours by default, counting while the server is stopped too.
- The next time that chunk loads after the cooldown (when players arrive from further away), the spot gets a new shulker, stuck where the original was. It comes back in the default purple; with [Shiny Shulkers](../shiny-shulkers) it gets its own chance to be shiny, so a city's colours change as its shulkers come back. Nothing appears while players keep the chunk loaded, so shulkers never pop in next to anyone.
- A city never gets more shulkers than it was generated with. Shulkers bred by players (shulker bullets on shulkers) belong to no spot: they are never refilled and never stop a refill. A city shulker moved elsewhere by players keeps its spot taken until it dies.

No client mods; Paper only.

## Installing

1. Put the jar in `plugins/` and start the server.
2. **Install it before players explore the End, and before pre-generating it** (with Chunky or similar): the plugin can only learn the shulkers a chunk has the first time it loads. On a world whose cities were already looted, each city learns the shulkers still in it; a city already emptied learns nothing. Vanilla does not keep the original positions anywhere a plugin can read them.

**Requirements:** Paper 26.3, Java 25. Folia is not supported. GriefPrevention is optional (see below).

## When a spot stays empty

A due spot is skipped, and tried again on the next load, when:

- its block is no longer air (players built there);
- nothing solid is left next to it (players mined around it); it comes back once something solid is placed there;
- a shulker is already sitting on it;
- the world's difficulty is Peaceful;
- it lies inside a **GriefPrevention claim** (when GriefPrevention is installed and `respect-griefprevention-claims` is on), so a player who claims a city to live in it is not attacked by new shulkers;
- another plugin cancels `CityShulkerRespawnEvent`.

A new shulker sticks to the same block face as the one before it. If that block is gone it sticks to another solid neighbour; if there is none, the spot waits (vanilla would teleport a shulker with nothing to stick to, carrying it away from its spot). New shulkers never despawn, like the city's own.

Vanilla generates a city's shulkers at fixed markers and settles each one on its first tick, which can move it a few blocks. A spot therefore follows its shulker: whenever one of the city's chunks loads, and again a few seconds later, each living city shulker's spot moves to where it sits (within 12 blocks and inside the city, so a shulker players carried away leaves its spot alone).

## Configuration

`plugins/EndCityShulkerRespawn/config.yml`:

| Option | Default | Meaning |
|---|---|---|
| `worlds` | `[]` | End worlds to manage, by name; empty means every End world |
| `cooldown` | `16h` | Real time between a shulker's death and its spot being refilled; `s`, `m`, `h`, `d`, combinable (`30m`, `1d12h`) |
| `respect-griefprevention-claims` | `true` | Spots inside GriefPrevention claims stay empty; ignored without GriefPrevention |

Apply changes with `/ecsr reload`.

## Commands and permissions

All under `/ecsr` (alias `/endcityshulkerrespawn`), permission `endcityshulkerrespawn.admin` (operators by default). They act on the End city you are standing in.

| Command | Does |
|---|---|
| `/ecsr status` | The city's spots: how many are alive, ready to come back, or cooling down, and when the next one is due; how many are inside a claim |
| `/ecsr refill` | Every dead spot becomes due now; they come back the next time their chunks load |
| `/ecsr learn` | Forgets the city and learns it again from the shulkers in it now (for example after rebuilding a city by hand); chunks not loaded are learned when they next load |
| `/ecsr reload` | Reloads `config.yml` |

## For other plugins

`me.fernandossm.endcityshulkerrespawn.api.CityShulkerRespawnEvent` (cancellable): a shulker is about to come back; gives the location, the city and the spot, and lets the listener choose its colour.

## Data

Nothing is kept in the plugin's folder. Each city's spots and cooldowns are stored on the city itself (Paper keeps a data container on every generated structure), and each city shulker carries a tag naming its spot. Both are saved with the world: they move with it, are backed up with it, and disappear if the End is reset. Checking a chunk costs a lookup when it loads; there are no timers or background tasks.

## License

GPL-3.0.
