package me.fernandossm.bluemapgriefprevention.core;

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
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void readsEveryOption() throws Exception {
        Settings s = parse("""
                worlds: [world]
                layer:
                  label: Land
                  hidden-by-default: true
                owner: List
                claims:
                  player: false
                  admin: true
                colors:
                  admin: "00ff00"
                  fill-opacity: 0.5
                  line-opacity: 1
                  line-width: 4
                resync-interval: 30s
                """);
        assertEquals(Set.of("world"), s.worlds());
        assertEquals("Land", s.layerLabel());
        assertTrue(s.hiddenByDefault());
        assertEquals(Settings.OwnerShown.LIST, s.owner());
        assertFalse(s.playerClaims());
        assertEquals(0x00FF00, s.adminColor());
        assertEquals(0.5f, s.fillOpacity());
        assertEquals(1f, s.lineOpacity());
        assertEquals(4, s.lineWidth());
        assertEquals(Duration.ofSeconds(30), s.resyncInterval());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void invalidValuesFallBackWithAWarning() throws Exception {
        Settings s = parse("""
                owner: everyone
                colors:
                  admin: purple
                  fill-opacity: 2
                  line-width: 0
                resync-interval: soon
                """);
        assertEquals(Settings.OwnerShown.CLICK, s.owner());
        assertEquals(0xBF00FF, s.adminColor());
        assertEquals(0.2f, s.fillOpacity());
        assertEquals(2, s.lineWidth());
        assertEquals(Duration.ofMinutes(5), s.resyncInterval());
        assertEquals(5, warnings.size(), warnings::toString);
    }
}
