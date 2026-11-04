package me.fernandossm.endcityshulkerrespawn.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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
        assertEquals(Duration.ofHours(16), Settings.DEFAULTS.cooldown());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void readsEveryOption() throws Exception {
        Settings s = parse("""
                worlds: [world_the_end]
                cooldown: 1d12h
                respect-griefprevention-claims: false
                """);
        assertEquals(Set.of("world_the_end"), s.worlds());
        assertEquals(Duration.ofHours(36), s.cooldown());
        assertFalse(s.respectGriefPreventionClaims());
        assertTrue(s.manages("world_the_end"));
        assertFalse(s.manages("other_end"));
    }

    @Test
    void anInvalidCooldownFallsBackWithAWarning() throws Exception {
        assertEquals(Duration.ofHours(16), parse("cooldown: soon").cooldown());
        assertEquals(1, warnings.size(), warnings::toString);
    }
}
