# Shiny Shulkers

When an End city is generated, each of its shulkers has a 1 in 50 chance to be **shiny**: to come in one of the sixteen dye colours instead of the usual purple, each colour equally likely. A shiny shulker drops its shell like any other; it just stands out, and so do shulker boxes made from its farm.

- A city's generated shulkers are rolled once, as the city is generated. Shulkers from spawn eggs, commands and other plugins are not, and cities generated before the plugin was installed stay as they are.
- A shulker duplicated by a shulker bullet takes its parent's colour, as in vanilla: a farm built around a shiny shulker breeds shiny shulkers.
- With [End City Shulker Respawn](../end-city-shulker-respawn), every shulker it brings back gets its own roll: a shiny one may come back purple and a purple one shiny, so a city's colours change over time.

No client mods; Paper only.

## Installing

Put the jar in `plugins/` and start the server, before the End is explored or pre-generated: only cities generated afterwards get shiny shulkers.

**Requirements:** Paper 26.2, Java 25. Folia is not supported.

## Configuration

`plugins/ShinyShulkers/config.yml`:

| Option | Default | Meaning |
|---|---|---|
| `worlds` | `[]` | End worlds to manage, by name; empty means every End world |
| `one-in` | `50` | The chance, as "one in N", that a generated city shulker is shiny |
| `colors` | all sixteen | The colours a shiny shulker can be, each equally likely |

Changes apply after a restart.

## How it works

A newly generated chunk arrives with its city's shulkers already in it, before any plugin sees them load; vanilla fires no spawn event for them. The plugin colours them then, only in chunks generated just now that lie in an End city. Shulkers End City Shulker Respawn brings back are rolled as they come back, through its respawn event. There are no commands, no data files and no background tasks.

## License

GPL-3.0.
