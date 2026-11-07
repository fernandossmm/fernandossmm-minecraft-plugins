# DiscordSRV fail-closed

Keeps a server that only lets in players with a linked Discord account (DiscordSRV's "Require linked account to play") closed when DiscordSRV is not running, instead of open to everyone.

DiscordSRV's own check already refuses logins while it is still connecting to Discord, while Discord is unreachable and when the check fails. But when DiscordSRV **disables itself**, the server forgets its join check along with it, and every login goes through. DiscordSRV disables itself when:

- the bot token is missing, too short, or rejected by Discord (a typo, a reset or revoked token);
- its startup crashes, for example after a broken update.

No DiscordSRV setting changes that. This plugin refuses every login unless DiscordSRV is enabled **and** its `Require linked account to play` setting (in `linking.yml`) is on. While both hold, it does nothing and DiscordSRV decides who joins.

- **No bypass list.** On an offline-mode server anyone can log in with any name, so a list of names to let through would let anyone through. While the server is closed, fix DiscordSRV from the server console.
- **Its own errors refuse too.** If the check fails for any reason, the login is refused.
- **Nothing else is changed.** Players already online stay online, and a login another plugin has already refused (a ban, a full server) keeps that plugin's message.

The server log says whether the server is open once it has started, and again whenever DiscordSRV is disabled or enabled:

```
[DiscordSRVFailClosed] Every login is refused: DiscordSRV is not enabled
[DiscordSRVFailClosed] Refused the login of Alex: DiscordSRV is not enabled
[DiscordSRVFailClosed] DiscordSRV requires a linked account; logins are left to it
```

No client mods; Paper only.

## Installing

Put the jar in `plugins/` next to DiscordSRV and start the server.

**Requirements:** Paper 26.2, Java 25, DiscordSRV 1.x. Folia is not supported.

The plugin protects the server only while it is loaded: if its jar is missing, nothing refuses logins. A server built from scripts or containers should check that the jar is there before it starts.

## Configuration

`plugins/DiscordSRVFailClosed/config.yml`:

| Option | Default | Meaning |
|---|---|---|
| `message` | "Joining is paused: the Discord account check is offline. Please try again in a few minutes." in red and grey | What a refused player sees, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format |

Changes apply after a restart.

## How it works

On every login, just before the server lets the player in, the plugin checks that DiscordSRV is enabled and asks DiscordSRV for its `Require linked account to play` setting: the value DiscordSRV holds now, which is what its own check uses. It doesn't read `linking.yml` from disk, which DiscordSRV reads only when it starts or on `/discord reload`, so an edited but not yet reloaded file can't open the server. If the setting can't be read (for example a DiscordSRV update changed how it is stored), the login is refused. The plugin runs at the highest event priority, after other plugins, so a plugin that allows logins can't open the server again. There are no commands, no data files and no background tasks.

## License

GPL-3.0.
