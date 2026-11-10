package me.fernandossm.bluemapgriefprevention.core;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/** config.yml, parsed and validated once per load. Invalid values fall back to the default with a warning. */
public record Settings(
        Set<String> worlds,
        String layerLabel,
        boolean hiddenByDefault,
        OwnerShown owner,
        boolean playerClaims,
        boolean adminClaims,
        int adminColor,
        float fillOpacity,
        float lineOpacity,
        int lineWidth,
        Duration resyncInterval,
        Messages messages) {

    /** Where a claim's owner is named. */
    public enum OwnerShown { CLICK, LIST, NONE }

    public static final Settings DEFAULTS = new Settings(Set.of(), "Claims", false, OwnerShown.CLICK, true, true,
            0xBF00FF, 0.2f, 0.8f, 2, Duration.ofMinutes(5), Messages.DEFAULTS);

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})");
    private static final Pattern DURATION = Pattern.compile("(\\d+)([smh])");

    public static Settings from(ConfigurationSection c, Logger log) {
        OwnerShown owner = DEFAULTS.owner;
        String ownerText = c.getString("owner", owner.name());
        try {
            owner = OwnerShown.valueOf(ownerText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("owner must be click, list or none, not '" + ownerText + "'; using click");
        }

        int adminColor = DEFAULTS.adminColor;
        String colorText = c.getString("colors.admin", "#BF00FF");
        Matcher hex = HEX.matcher(colorText.trim());
        if (hex.matches()) {
            adminColor = Integer.parseInt(hex.group(1), 16);
        } else {
            log.warning("colors.admin must be a colour like #BF00FF, not '" + colorText + "'; using #BF00FF");
        }

        int lineWidth = c.getInt("colors.line-width", DEFAULTS.lineWidth);
        if (lineWidth < 1) {
            log.warning("colors.line-width must be 1 or more; using " + DEFAULTS.lineWidth);
            lineWidth = DEFAULTS.lineWidth;
        }

        Duration resync = DEFAULTS.resyncInterval;
        String resyncText = c.getString("resync-interval", "5m");
        Matcher d = DURATION.matcher(resyncText.trim().toLowerCase(Locale.ROOT));
        if (d.matches() && Long.parseLong(d.group(1)) > 0) {
            long n = Long.parseLong(d.group(1));
            resync = switch (d.group(2)) {
                case "s" -> Duration.ofSeconds(n);
                case "m" -> Duration.ofMinutes(n);
                default -> Duration.ofHours(n);
            };
        } else {
            log.warning("resync-interval must be like 30s, 5m or 1h, not '" + resyncText + "'; using 5m");
        }

        return new Settings(
                Set.copyOf(c.getStringList("worlds")),
                c.getString("layer.label", DEFAULTS.layerLabel),
                c.getBoolean("layer.hidden-by-default", DEFAULTS.hiddenByDefault),
                owner,
                c.getBoolean("claims.player", DEFAULTS.playerClaims),
                c.getBoolean("claims.admin", DEFAULTS.adminClaims),
                adminColor,
                opacity(c, "colors.fill-opacity", DEFAULTS.fillOpacity, log),
                opacity(c, "colors.line-opacity", DEFAULTS.lineOpacity, log),
                lineWidth,
                resync,
                Messages.from(c));
    }

    private static float opacity(ConfigurationSection c, String key, float fallback, Logger log) {
        double value = c.getDouble(key, fallback);
        if (value < 0 || value > 1) {
            log.warning(key + " must be between 0 and 1, not " + value + "; using " + fallback);
            return fallback;
        }
        return (float) value;
    }

    public boolean manages(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
