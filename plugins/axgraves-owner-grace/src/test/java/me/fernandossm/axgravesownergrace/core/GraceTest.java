package me.fernandossm.axgravesownergrace.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GraceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Duration FIVE = Duration.ofMinutes(5);
    private static final long DIED = 1_000_000_000_000L;

    @Test
    void theOwnerAndStaffOpenAtOnce() {
        assertEquals(Duration.ZERO, Grace.remaining(OWNER, OWNER, false, DIED, DIED, FIVE));
        assertEquals(Duration.ZERO, Grace.remaining(OTHER, OWNER, true, DIED, DIED, FIVE));
    }

    @Test
    void othersWaitOutTheGracePeriod() {
        assertEquals(FIVE, Grace.remaining(OTHER, OWNER, false, DIED, DIED, FIVE));
        assertEquals(Duration.ofSeconds(60), Grace.remaining(OTHER, OWNER, false, DIED, DIED + 240_000, FIVE));
        assertEquals(Duration.ZERO, Grace.remaining(OTHER, OWNER, false, DIED, DIED + 300_000, FIVE));
        assertEquals(Duration.ZERO, Grace.remaining(OTHER, OWNER, false, DIED, DIED + 86_400_000, FIVE));
    }

    @Test
    void aClockSetBackKeepsTheGraveClosedNoLongerThanThePeriod() {
        assertEquals(FIVE, Grace.remaining(OTHER, OWNER, false, DIED, DIED - 3_600_000, FIVE));
    }

    @Test
    void aZeroPeriodOpensGravesToEveryone() {
        assertEquals(Duration.ZERO, Grace.remaining(OTHER, OWNER, false, DIED, DIED, Duration.ZERO));
    }

    @Test
    void theClockRoundsUp() {
        assertEquals("5:00", Grace.clock(FIVE));
        assertEquals("1:05", Grace.clock(Duration.ofMillis(64_001)));
        assertEquals("0:01", Grace.clock(Duration.ofMillis(1)));
    }

    @Test
    void settingsReadTheConfigAndRefuseNonsense() {
        YamlConfiguration c = new YamlConfiguration();
        assertEquals(Settings.DEFAULTS, Settings.from(c, Logger.getAnonymousLogger()));
        for (var pair : List.of(List.of("90s", "PT1M30S"), List.of("2m", "PT2M"), List.of("1h", "PT1H"), List.of("0", "PT0S"),
                List.of("soon", "PT5M"))) {
            c.set("grace-period", pair.get(0));
            assertEquals(Duration.parse(pair.get(1)), Settings.from(c, Logger.getAnonymousLogger()).gracePeriod(), pair.get(0));
        }
        c.set("messages.locked", "<red>Tumba de <owner>: <time>");
        assertEquals("<red>Tumba de <owner>: <time>", Settings.from(c, Logger.getAnonymousLogger()).lockedMessage());
    }
}
