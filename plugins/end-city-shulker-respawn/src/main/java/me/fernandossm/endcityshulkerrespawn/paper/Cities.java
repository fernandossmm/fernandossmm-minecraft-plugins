package me.fernandossm.endcityshulkerrespawn.paper;

import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;

/** Finds the End cities of the worlds the plugin manages. */
public interface Cities {

    /** Cities that reach into this chunk; empty for unmanaged worlds. */
    List<City> touching(World world, int chunkX, int chunkZ);

    /** The city with this key, looked for around {@code near} first. */
    Optional<City> byKey(World world, String key, Location near);

    /**
     * The city whose bounding box this location is in, if any. Searched in the chunks around it: a city's
     * box also covers chunks that none of its pieces reach.
     */
    default Optional<City> at(Location location) {
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        for (int r = 0; r <= SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    Optional<City> found = touching(location.getWorld(), cx + dx, cz + dz).stream()
                            .filter(c -> c.contains(location.getX(), location.getY(), location.getZ()))
                            .findFirst();
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Chunks searched around a location; an End city with its ship spans less than this either way. */
    int SEARCH_RADIUS = 8;
}
