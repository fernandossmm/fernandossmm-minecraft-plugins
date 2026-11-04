package me.fernandossm.bluemapbannermarkers.core;

import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** config.yml, parsed and validated once per load. Invalid values fall back to the default with a warning. */
public record Settings(Set<String> worlds, int maxPerPlayer, String layerLabel, boolean hiddenByDefault) {

    public static final Settings DEFAULTS = new Settings(Set.of(), 5, "Waypoints", false);

    public static Settings from(ConfigurationSection c, Logger log) {
        int max = c.getInt("max-per-player", DEFAULTS.maxPerPlayer);
        if (max < 0) {
            log.warning("max-per-player must be 0 or more, not " + max + "; using " + DEFAULTS.maxPerPlayer);
            max = DEFAULTS.maxPerPlayer;
        }
        return new Settings(
                Set.copyOf(c.getStringList("worlds")),
                max,
                c.getString("layer.label", DEFAULTS.layerLabel),
                c.getBoolean("layer.hidden-by-default", DEFAULTS.hiddenByDefault));
    }

    public boolean allows(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
