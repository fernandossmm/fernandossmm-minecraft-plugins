# Multiplayer Dragon Rebirth

On a vanilla server only the first players to beat the Ender Dragon get its egg and its 12,000 experience; everyone after them finds an empty End. Multiplayer Dragon Rebirth gives every player their own first fight.

- When a **newcomer** (a player who has never taken part in a dragon kill) is in the End and no dragon is there, a dragon is summoned for them after a short countdown, with the vanilla summoning animation: four end crystals appear on the exit portal (players cannot destroy them) and the pillars are rebuilt.
- A dragon whose kill involves at least one newcomer drops **its egg and 12,000 experience**, exactly like the very first dragon. This holds however the dragon appeared: summoned by the plugin, respawned by players with end crystals, or the world's original dragon.
- Taking part means **damaging the dragon**: hits, arrows, tridents, TNT and anything else the game attributes to the player.
- Everyone who damaged it stops being a newcomer after the Dragon dies.

No client mods; Paper only.

## Installing

1. Put the jar in `plugins/` and start the server.
2. On its first start the plugin records every player who has ever joined the server as having fought the dragon, so only players new to the server are newcomers. Set `first-run: NONE` before the first start to make everyone a newcomer instead.
3. Give staff `multiplayerdragonrebirth.exempt` so their visits to the End do not summon dragons.

**Requirements:** Paper 26.3, Java 25. Folia is not supported.

**Minecraft versions:** 0.2.x for 26.3, 0.1.x for 26.2. Each release's jar is on the [GitHub releases](https://github.com/fernandossmm/fernandossmm-minecraft-plugins/releases).

## What players will notice

- The countdown and the first-time kill are announced to the whole server (both messages can be changed or turned off).
- Each summoning rebuilds the obsidian pillars and their crystals, destroying whatever is built on or around them, as a vanilla respawn does. Consider protecting the main island from building.
- Each kill opens a new end gateway, up to vanilla's 20.
- Eggs pile up on the exit portal: one per first-time kill, or one per newcomer in it with `egg-per-newcomer`. They are placed as blocks, never as loose items that could roll into the open exit portal; only if there is no room are they dropped, as items that never despawn.
- Players who joined a newcomer's fight also collect the 12,000 experience; it drops for whoever is there.

## Configuration

`plugins/MultiplayerDragonRebirth/config.yml`:

| Option | Default | Meaning |
|---|---|---|
| `worlds` | `[]` | End worlds to manage, by name; empty means every End world with a dragon fight |
| `countdown-seconds` | `10` | Delay between a newcomer arriving and the summoning |
| `trigger-on-join` | `true` | Also start the countdown when a newcomer logs in inside the End |
| `first-run` | `MARK_EXISTING` | On the plugin's first start: `MARK_EXISTING` records everyone who has joined as having fought; `NONE` records nobody |
| `egg-per-newcomer` | `false` | A first-time kill gives one egg per newcomer who damaged the dragon instead of one; the extra eggs are stacked on the first, on the exit portal |
| `free-the-end-counts-as-done` | `false` | Players with the "Free the End" advancement never count as newcomers. A last resort: vanilla grants it only to the player who lands the kill. |
| `messages.countdown` | `A dragon rises for <newcomers> in <seconds> seconds.` | MiniMessage; placeholders `<newcomers>`, `<seconds>`, `<world>`; empty to disable |
| `messages.first-time-kill` | `The dragon has fallen! Its egg and power await.` | MiniMessage; placeholders `<newcomers>`, `<world>`; empty to disable |

Apply changes with `/mdr reload`.

## Commands and permissions

All under `/mdr` (alias `/multiplayerdragonrebirth`), permission `multiplayerdragonrebirth.admin` (operators by default). Player names work for offline players too.

| Command | Does |
|---|---|
| `/mdr status [world]` | The fight in each managed End world: dragon, summoning phase, countdown, who has damaged the current dragon |
| `/mdr check <player>` | Whether a player is a newcomer, and if not since when and why |
| `/mdr mark <player>` | The player counts as having fought |
| `/mdr reset <player>` | The player is a newcomer again |
| `/mdr reset-all confirm` | Everyone is a newcomer again |
| `/mdr mark-existing` | Everyone who has joined the server counts as having fought (what `first-run: MARK_EXISTING` does on the first start) |
| `/mdr summon [world]` | Summons a dragon now, without countdown or newcomer |
| `/mdr reload` | Reloads `config.yml` |

`multiplayerdragonrebirth.exempt` (nobody by default): never summons a dragon by entering the End, and never makes a kill first-time. Players in Creative or Spectator mode, and players hidden by a vanish plugin, never summon or take part either.

## For other plugins

Events in `me.fernandossm.multiplayerdragonrebirth.api`:

- `DragonSummonEvent` (cancellable): a countdown ended and a dragon is about to be summoned for the newcomers in it.
- `FirstTimeDragonKillEvent`: a dragon died in a first-time kill; gives the participants and the newcomers among them. A natural hook for announcing to Discord or giving rewards.

## Data

`plugins/MultiplayerDragonRebirth/players.json` lists the players who have fought, with when and why. It is replaced atomically on every change. If it cannot be read, the plugin disables itself rather than starting empty, which would make every player a newcomer again; fix or restore the file and restart.

Who damaged the current dragon is kept in memory only: if the server restarts mid-fight, the damage dealt after the restart is what counts. A restart during the ten-second death animation of a first-time kill cancels that kill's egg and extra experience; the participants still count as having fought.

## License

GPL-3.0.
