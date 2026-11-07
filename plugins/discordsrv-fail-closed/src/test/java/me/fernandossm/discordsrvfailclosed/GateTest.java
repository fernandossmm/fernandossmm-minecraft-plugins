package me.fernandossm.discordsrvfailclosed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Reading the requirement from DiscordSRV the way its own check does, against stand-ins shaped like DiscordSRV 1.30's
 * classes ({@code DiscordSRV.config()} returning a configuralize {@code DynamicConfig}).
 */
class GateTest {

    private final Logger log = Logger.getAnonymousLogger();

    {
        log.setUseParentHandlers(false);
    }

    /** DynamicConfig's behaviour: getBoolean throws for a missing key. */
    public static final class FakeConfig {
        static final Map<String, Object> VALUES = new HashMap<>();

        public boolean getBoolean(String key) {
            Object value = VALUES.get(key);
            if (value == null) {
                throw new RuntimeException("No option " + key);
            }
            return (Boolean) value;
        }
    }

    public static final class FakeDiscordSrv {
        public static FakeConfig config() {
            return new FakeConfig();
        }
    }

    /** A DiscordSRV whose API changed: no config(). */
    public static final class ChangedDiscordSrv {
        public static Object settings() {
            return new FakeConfig();
        }
    }

    /** A DiscordSRV whose config() returns something without getBoolean. */
    public static final class OddDiscordSrv {
        public static Object config() {
            return "not a config";
        }
    }

    @Test
    void readsTheSettingDiscordSrvHoldsNow() throws Exception {
        FakeConfig.VALUES.put(Gate.LINKING_ENABLED, true);
        assertTrue(Gate.requiresLinkedAccount(FakeDiscordSrv.class));
        FakeConfig.VALUES.put(Gate.LINKING_ENABLED, false);
        assertFalse(Gate.requiresLinkedAccount(FakeDiscordSrv.class));
    }

    @Test
    void aMissingKeyOrAChangedApiThrows() {
        FakeConfig.VALUES.clear();
        assertThrows(ReflectiveOperationException.class, () -> Gate.requiresLinkedAccount(FakeDiscordSrv.class), "missing key");
        assertThrows(ReflectiveOperationException.class, () -> Gate.requiresLinkedAccount(ChangedDiscordSrv.class), "no config()");
        assertThrows(ReflectiveOperationException.class, () -> Gate.requiresLinkedAccount(OddDiscordSrv.class), "no getBoolean");
    }

    @Test
    void aMissingPluginKeepsItClosed() {
        assertEquals(Optional.of(Refusal.NOT_INSTALLED), new Gate(name -> null, log).check());
    }

    @Test
    void aFailingLookupKeepsItClosed() {
        Gate gate = new Gate(name -> {
            throw new IllegalStateException("plugin manager is gone");
        }, log);
        assertEquals(Optional.of(Refusal.ERROR), gate.check());

        Gate linkageError = new Gate(name -> {
            throw new NoClassDefFoundError("a broken jar");
        }, log);
        assertEquals(Optional.of(Refusal.ERROR), linkageError.check());
    }
}
