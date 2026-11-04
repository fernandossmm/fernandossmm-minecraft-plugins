package me.fernandossm.endcityshulkerrespawn.core;

import java.time.Duration;

/**
 * Where one of a city's original shulkers sits.
 *
 * @param face   the side of the shulker's block that it is attached to (DOWN: it sits on the block below)
 * @param color  dye colour index, or {@link #NO_COLOR} for the default purple; in Java Edition only commands
 *               and plugins colour shulkers, but a shulker coloured that way comes back in its colour
 * @param dueAt  {@link #ALIVE} while its shulker lives; otherwise when it may be refilled, in epoch millis
 */
public record Spot(int x, int y, int z, Face face, int color, long dueAt) {

    public enum Face { DOWN, UP, NORTH, SOUTH, WEST, EAST }

    public static final int NO_COLOR = -1;
    public static final long ALIVE = 0;

    public boolean alive() {
        return dueAt == ALIVE;
    }

    public boolean due(long nowMillis) {
        return !alive() && nowMillis >= dueAt;
    }

    public Spot died(long nowMillis, Duration cooldown) {
        return dueBy(nowMillis + cooldown.toMillis());
    }

    /** Dead, refillable from this time on. Never {@link #ALIVE}, whatever the clock says. */
    public Spot dueBy(long millis) {
        return new Spot(x, y, z, face, color, Math.max(1, millis));
    }

    public Spot refilled() {
        return new Spot(x, y, z, face, color, ALIVE);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
