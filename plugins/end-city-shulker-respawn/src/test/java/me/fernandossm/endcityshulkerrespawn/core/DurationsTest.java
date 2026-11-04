package me.fernandossm.endcityshulkerrespawn.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationsTest {

    @ParameterizedTest
    @CsvSource({"16h, 57600", "30m, 1800", "90s, 90", "7d, 604800", "1d12h, 129600", "1D 12H, 129600", "2h30m15s, 9015"})
    void parses(String text, long seconds) {
        assertEquals(Duration.ofSeconds(seconds), Durations.parse(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "16", "h", "0h", "16x", "-5m", "1h banana", "12h30"})
    void rejects(String text) {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"16h", "1d12h", "45s", "2h30m15s", "7d"})
    void formatsBackToTheSameText(String text) {
        assertEquals(text, Durations.format(Durations.parse(text)));
    }

    @ParameterizedTest
    @CsvSource({"0, 0s", "-5, 0s"})
    void formatsNothingAsZero(long seconds, String text) {
        assertEquals(text, Durations.format(Duration.ofSeconds(seconds)));
    }
}
