package me.fernandossm.axgravesownergrace.core;

import java.time.Duration;
import java.util.UUID;

/** Who may open a grave, and when: its owner and staff at once, anyone else once the grace period is over. */
public final class Grace {

    private Grace() {}

    /**
     * How much longer the grave stays closed to this player; zero when they may open it now.
     *
     * @param spawnedMillis when the grave was made (AxGraves' own timestamp, wall-clock milliseconds)
     * @param nowMillis     the current wall-clock time
     */
    public static Duration remaining(UUID opener, UUID owner, boolean bypass, long spawnedMillis, long nowMillis,
                                     Duration period) {
        if (bypass || opener.equals(owner) || period.isZero()) {
            return Duration.ZERO;
        }
        // A clock set back after the death would give a negative age: the grave then counts as just made.
        long age = Math.max(0, nowMillis - spawnedMillis);
        long left = period.toMillis() - age;
        return left <= 0 ? Duration.ZERO : Duration.ofMillis(left);
    }

    /** minutes:seconds, rounded up, so "0:00" is never shown while the grave is still closed. */
    public static String clock(Duration left) {
        long seconds = (left.toMillis() + 999) / 1000;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
