package me.fernandossm.multiplayerdragonrebirth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void anEmptyConfigUsesTheDefaults() throws Exception {
        assertEquals(Settings.DEFAULTS, parse(""));
    }

    @Test
    void readsEveryOption() throws Exception {
        Settings s = parse("""
                worlds: [world_the_end, custom_end]
                countdown-seconds: 3
                trigger-on-join: false
                first-run: none
                free-the-end-counts-as-done: true
                egg-per-newcomer: true
                messages:
                  countdown: ""
                  first-time-kill: "<red>gone"
                """);
        assertEquals(Set.of("world_the_end", "custom_end"), s.worlds());
        assertEquals(3, s.countdownSeconds());
        assertFalse(s.triggerOnJoin());
        assertEquals(Settings.FirstRun.NONE, s.firstRun());
        assertTrue(s.freeTheEndCountsAsDone());
        assertTrue(s.eggPerNewcomer());
        assertEquals("", s.countdownMessage());
        assertEquals("<red>gone", s.firstTimeKillMessage());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void invalidValuesFallBackWithAWarning() throws Exception {
        Settings s = parse("""
                countdown-seconds: -5
                first-run: EVERYONE
                """);
        assertEquals(Settings.DEFAULTS.countdownSeconds(), s.countdownSeconds());
        assertEquals(Settings.FirstRun.MARK_EXISTING, s.firstRun());
        assertEquals(2, warnings.size(), warnings::toString);
    }

    @Test
    void anEmptyWorldListManagesEveryWorld() throws Exception {
        assertTrue(parse("").manages("anything"));
        Settings listed = parse("worlds: [world_the_end]");
        assertTrue(listed.manages("world_the_end"));
        assertFalse(listed.manages("other_end"));
    }
}
