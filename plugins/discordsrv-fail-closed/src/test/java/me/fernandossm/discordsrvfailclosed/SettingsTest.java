package me.fernandossm.discordsrvfailclosed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    private static String plain(Settings s) {
        return PlainTextComponentSerializer.plainText().serialize(s.message());
    }

    @Test
    void theShippedConfigMatchesTheDefaults() {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertEquals(Settings.DEFAULTS, Settings.from(shipped, log));
        assertEquals("Joining is paused: the Discord account check is offline.\nPlease try again in a few minutes.",
                plain(Settings.DEFAULTS));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void readsTheMessage() throws Exception {
        Settings s = parse("message: \"<gold>Closed<newline>for now\"");
        assertEquals("Closed\nfor now", plain(s));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void anEmptyMessageFallsBack() throws Exception {
        assertEquals(Settings.DEFAULTS, parse("message: \"  \""));
        assertEquals(Settings.DEFAULTS, parse("other: 1"));
        assertEquals(2, warnings.size(), warnings::toString);
    }
}
