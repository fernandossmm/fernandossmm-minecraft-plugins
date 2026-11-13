package me.fernandossm.axgravesownergrace.core;

import java.time.Duration;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/** config.yml, parsed once per load. Invalid values fall back to the default with a warning. */
public record Settings(Duration gracePeriod, String lockedMessage) {

    public static final Settings DEFAULTS = new Settings(Duration.ofMinutes(5),
            "<red>This is <owner>'s grave. You can open it in <time>.");

    private static final Pattern DURATION = Pattern.compile("(\\d+)([smh])");

    public static Settings from(ConfigurationSection c, Logger log) {
        Duration period = DEFAULTS.gracePeriod;
        String text = c.getString("grace-period", "5m").trim().toLowerCase(Locale.ROOT);
        Matcher m = DURATION.matcher(text);
        if (text.equals("0")) {
            period = Duration.ZERO;
        } else if (m.matches()) {
            long n = Long.parseLong(m.group(1));
            period = switch (m.group(2)) {
                case "s" -> Duration.ofSeconds(n);
                case "m" -> Duration.ofMinutes(n);
                default -> Duration.ofHours(n);
            };
        } else {
            log.warning("grace-period must be like 30s, 5m or 1h (or 0), not '" + text + "'; using 5m");
        }
        return new Settings(period, c.getString("messages.locked", DEFAULTS.lockedMessage));
    }
}
