package me.fernandossm.shinyshulkers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

/**
 * Colours End city shulkers as their city is generated. A chunk generated just now arrives with its city
 * shulkers in it, before any other plugin sees them load (End City Shulker Respawn learns them later, on
 * EntitiesLoadEvent, and so remembers their colour). Vanilla fires no spawn event for them.
 */
// Not final: MockBukkit loads plugins in tests by subclassing them.
public class ShinyShulkers extends JavaPlugin implements Listener {

    private volatile Settings settings = Settings.DEFAULTS;
    private RandomGenerator random = ThreadLocalRandom.current();
    private Function<Chunk, List<Shulker>> cityShulkers = ShinyShulkers::cityShulkers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig(), getLogger());
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (!event.isNewChunk() || world.getEnvironment() != World.Environment.THE_END || !settings.manages(world.getName())) {
            return;
        }
        for (Shulker shulker : cityShulkers.apply(event.getChunk())) {
            if (shulker.getColor() == null) {
                Roll.roll(settings.oneIn(), settings.colors(), random).ifPresent(shulker::setColor);
            }
        }
    }

    /** The chunk's shulkers that stand inside an End city. */
    private static List<Shulker> cityShulkers(Chunk chunk) {
        List<BoundingBox> cities = chunk.getStructures(Structure.END_CITY).stream().map(GeneratedStructure::getBoundingBox).toList();
        if (cities.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(chunk.getEntities())
                .filter(e -> e instanceof Shulker)
                .map(e -> (Shulker) e)
                .filter(s -> cities.stream().anyMatch(box -> contains(box, s.getX(), s.getY(), s.getZ())))
                .toList();
    }

    private static boolean contains(BoundingBox b, double x, double y, double z) {
        return x >= b.getMinX() && x <= b.getMaxX() && y >= b.getMinY() && y <= b.getMaxY() && z >= b.getMinZ() && z <= b.getMaxZ();
    }

    public Settings settings() {
        return settings;
    }

    void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
    }

    void useForTests(RandomGenerator random, Function<Chunk, List<Shulker>> cityShulkers) {
        this.random = random;
        this.cityShulkers = cityShulkers;
    }
}
