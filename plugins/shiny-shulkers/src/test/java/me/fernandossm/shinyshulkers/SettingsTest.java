package me.fernandossm.shinyshulkers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.DyeColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private final List<String> warnings = new ArrayList<>();
    private final Logger log = Logger.getAnonymousLogger();

    {
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    private Settings parse(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return Settings.from(config, log);
    }

    @Test
    void theShippedConfigMatchesTheDefaults() {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertEquals(Settings.DEFAULTS, Settings.from(shipped, log));
        assertEquals(50, Settings.DEFAULTS.oneIn());
        assertEquals(16, Settings.DEFAULTS.colors().size());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void readsEveryOption() throws Exception {
        Settings s = parse("""
                worlds: [world_the_end]
                one-in: 10
                colors: [red, Gold_Is_Not_A_Dye, RED, light_blue]
                """);
        assertEquals(10, s.oneIn());
        assertEquals(List.of(DyeColor.RED, DyeColor.LIGHT_BLUE), s.colors(), "case-insensitive, without duplicates");
        assertTrue(s.manages("world_the_end"));
        assertFalse(s.manages("other_end"));
        assertEquals(1, warnings.size(), warnings::toString);
    }

    @Test
    void invalidValuesFallBack() throws Exception {
        Settings s = parse("""
                one-in: 0
                colors: [nothing]
                """);
        assertEquals(50, s.oneIn());
        assertEquals(16, s.colors().size());
        assertEquals(3, warnings.size(), warnings::toString);
    }
}
