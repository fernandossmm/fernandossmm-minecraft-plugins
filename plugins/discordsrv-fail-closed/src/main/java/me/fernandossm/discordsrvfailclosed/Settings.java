package me.fernandossm.discordsrvfailclosed;

import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;

/** config.yml, parsed once per load. An empty or invalid message falls back to the default with a warning. */
public record Settings(Component message) {

    static final String DEFAULT_MESSAGE = "<red>Joining is paused: the Discord account check is offline.</red>\n"
            + "<gray>Please try again in a few minutes.</gray>";

    public static final Settings DEFAULTS = new Settings(MiniMessage.miniMessage().deserialize(DEFAULT_MESSAGE));

    public static Settings from(ConfigurationSection config, Logger log) {
        String raw = config.getString("message");
        if (raw == null || raw.isBlank()) {
            log.warning("message is empty; using the default");
            return DEFAULTS;
        }
        try {
            return new Settings(MiniMessage.miniMessage().deserialize(raw));
        } catch (RuntimeException e) {
            log.warning("message is not valid MiniMessage (" + e.getMessage() + "); using the default");
            return DEFAULTS;
        }
    }
}
