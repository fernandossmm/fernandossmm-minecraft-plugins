package me.fernandossm.endcityshulkerrespawn.paper;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import me.fernandossm.endcityshulkerrespawn.config.Settings;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.store.CityCodec;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.BoundingBox;

/**
 * {@link Cities} backed by Paper's generated structures. Each city's data lives in the structure's own
 * persistent data container, saved by the server with the structure.
 */
public final class PaperCities implements Cities {

    private final Supplier<Settings> settings;
    private final CityCodec codec;

    public PaperCities(Supplier<Settings> settings, CityCodec codec) {
        this.settings = settings;
        this.codec = codec;
    }

    /**
     * Only for loaded chunks: Paper's structure lookup loads the chunk otherwise, which inside some events
     * (a chunk unloading) recurses until the stack overflows, and elsewhere stalls the server.
     */
    @Override
    public List<City> touching(World world, int chunkX, int chunkZ) {
        if (world.getEnvironment() != World.Environment.THE_END || !settings.get().manages(world.getName())
                || !world.isChunkLoaded(chunkX, chunkZ)) {
            return List.of();
        }
        return world.getStructures(chunkX, chunkZ, Structure.END_CITY).stream()
                .map(s -> (City) new PaperCity(world, s))
                .toList();
    }

    @Override
    public Optional<City> byKey(World world, String key, Location near) {
        // A shulker may sit in a chunk the city's box covers but none of its pieces reach: look around it too.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Optional<City> found = touching(world, (near.getBlockX() >> 4) + dx, (near.getBlockZ() >> 4) + dz).stream()
                        .filter(c -> c.key().equals(key)).findFirst();
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        // Far from where it started (moved by players): look where the city begins.
        String[] corner = key.split(",");
        if (corner.length != 3) {
            return Optional.empty();
        }
        try {
            int x = Integer.parseInt(corner[0]);
            int z = Integer.parseInt(corner[2]);
            return touching(world, x >> 4, z >> 4).stream().filter(c -> c.key().equals(key)).findFirst();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private final class PaperCity implements City {

        private final World world;
        private final GeneratedStructure structure;

        PaperCity(World world, GeneratedStructure structure) {
            this.world = world;
            this.structure = structure;
        }

        @Override
        public World world() {
            return world;
        }

        @Override
        public String key() {
            BoundingBox b = structure.getBoundingBox();
            return (int) Math.floor(b.getMinX()) + "," + (int) Math.floor(b.getMinY()) + "," + (int) Math.floor(b.getMinZ());
        }

        @Override
        public BoundingBox box() {
            return structure.getBoundingBox();
        }

        @Override
        public CityData load() {
            return codec.read(structure.getPersistentDataContainer());
        }

        @Override
        public void save(CityData data) {
            codec.write(structure.getPersistentDataContainer(), data);
        }
    }
}
