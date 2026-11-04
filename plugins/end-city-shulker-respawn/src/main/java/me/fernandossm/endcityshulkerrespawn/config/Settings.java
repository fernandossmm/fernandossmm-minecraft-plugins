package me.fernandossm.endcityshulkerrespawn.config;

import java.time.Duration;
import java.util.Set;
import java.util.logging.Logger;
import me.fernandossm.endcityshulkerrespawn.core.Durations;
import org.bukkit.configuration.ConfigurationSection;

/** config.yml, parsed and validated once per load. Invalid values fall back to the default with a warning. */
public record Settings(Set<String> worlds, Duration cooldown, boolean respectGriefPreventionClaims) {

    public static final Settings DEFAULTS = new Settings(Set.of(), Duration.ofHours(16), true);

    public static Settings from(ConfigurationSection config, Logger log) {
        Duration cooldown = DEFAULTS.cooldown;
        String text = config.getString("cooldown", Durations.format(cooldown));
        try {
            cooldown = Durations.parse(text);
        } catch (IllegalArgumentException e) {
            log.warning("cooldown: " + e.getMessage() + "; using " + Durations.format(cooldown));
        }
        return new Settings(
                Set.copyOf(config.getStringList("worlds")),
                cooldown,
                config.getBoolean("respect-griefprevention-claims", DEFAULTS.respectGriefPreventionClaims));
    }

    public boolean manages(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
