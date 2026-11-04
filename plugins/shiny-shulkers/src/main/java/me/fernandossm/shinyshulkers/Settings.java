package me.fernandossm.shinyshulkers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.DyeColor;
import org.bukkit.configuration.ConfigurationSection;

/** config.yml, parsed and validated once per load. Invalid values fall back to the default with a warning. */
public record Settings(Set<String> worlds, int oneIn, List<DyeColor> colors) {

    public static final Settings DEFAULTS = new Settings(Set.of(), 50, List.of(DyeColor.values()));

    public static Settings from(ConfigurationSection config, Logger log) {
        int oneIn = config.getInt("one-in", DEFAULTS.oneIn);
        if (oneIn < 1) {
            log.warning("one-in must be 1 or more, not " + oneIn + "; using " + DEFAULTS.oneIn);
            oneIn = DEFAULTS.oneIn;
        }
        List<DyeColor> colors = new ArrayList<>();
        for (String name : config.getStringList("colors")) {
            try {
                DyeColor color = DyeColor.valueOf(name.trim().toUpperCase(Locale.ROOT));
                if (!colors.contains(color)) {
                    colors.add(color);
                }
            } catch (IllegalArgumentException e) {
                log.warning("colors: '" + name + "' is not a dye colour; ignored");
            }
        }
        if (colors.isEmpty()) {
            if (config.contains("colors")) {
                log.warning("colors has no valid dye colour; using all sixteen");
            }
            colors = DEFAULTS.colors;
        }
        return new Settings(Set.copyOf(config.getStringList("worlds")), oneIn, List.copyOf(colors));
    }

    public boolean manages(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
