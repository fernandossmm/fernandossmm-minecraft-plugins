package me.fernandossm.endcityshulkerrespawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.paper.Cities;
import me.fernandossm.endcityshulkerrespawn.paper.City;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Cities kept in memory, since MockBukkit generates no structures. Data is copied on every load and save. */
final class FakeCities implements Cities {

    final List<FakeCity> cities = new ArrayList<>();

    FakeCity add(World world, BoundingBox box) {
        FakeCity city = new FakeCity(world, box);
        cities.add(city);
        return city;
    }

    @Override
    public List<City> touching(World world, int chunkX, int chunkZ) {
        return cities.stream()
                .filter(c -> c.world == world
                        && chunkX >= (int) Math.floor(c.box.getMinX()) >> 4 && chunkX <= (int) Math.floor(c.box.getMaxX()) >> 4
                        && chunkZ >= (int) Math.floor(c.box.getMinZ()) >> 4 && chunkZ <= (int) Math.floor(c.box.getMaxZ()) >> 4)
                .map(c -> (City) c)
                .toList();
    }

    @Override
    public Optional<City> byKey(World world, String key, Location near) {
        return cities.stream().filter(c -> c.world == world && c.key().equals(key)).map(c -> (City) c).findFirst();
    }

    static final class FakeCity implements City {

        private final World world;
        private final BoundingBox box;
        private CityData stored = CityData.empty();

        FakeCity(World world, BoundingBox box) {
            this.world = world;
            this.box = box;
        }

        @Override
        public World world() {
            return world;
        }

        @Override
        public String key() {
            return (int) box.getMinX() + "," + (int) box.getMinY() + "," + (int) box.getMinZ();
        }

        @Override
        public BoundingBox box() {
            return box;
        }

        @Override
        public CityData load() {
            return copy(stored);
        }

        @Override
        public void save(CityData data) {
            stored = copy(data);
        }

        private static CityData copy(CityData data) {
            return new CityData(data.spots(), data.learnedChunks(), data.generation());
        }
    }
}
