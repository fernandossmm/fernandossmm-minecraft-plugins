package me.fernandossm.endcityshulkerrespawn.paper;

import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * A city chunk's entities loading: learn, settle and refill. City shulkers leaving the world: start the
 * cooldown. Nothing happens while chunks unload: looking cities up then would make the server load the
 * chunk again.
 */
public final class ShulkerListener implements Listener {

    /** Long enough for freshly generated shulkers to have ticked once and settled. */
    private static final long SETTLE_DELAY_TICKS = 100;

    private final Plugin plugin;
    private final Supplier<Cities> cities;
    private final CityKeeper keeper;

    public ShulkerListener(Plugin plugin, Supplier<Cities> cities, CityKeeper keeper) {
        this.plugin = plugin;
        this.cities = cities;
        this.keeper = keeper;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        int x = chunk.getX();
        int z = chunk.getZ();
        List<City> touching = cities.get().touching(world, x, z);
        if (touching.isEmpty()) {
            return;
        }
        touching.forEach(city -> {
            keeper.learn(city, x, z, event.getEntities());
            keeper.settle(city, event.getEntities()); // shulkers saved after an earlier visit have settled
        });
        // Spawned on the next tick rather than in the middle of the chunk's entities being added.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (world.isChunkLoaded(x, z)) {
                cities.get().touching(world, x, z).forEach(city -> keeper.refill(city, x, z));
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (world.isChunkLoaded(x, z)) {
                List<Entity> now = List.of(world.getChunkAt(x, z).getEntities());
                cities.get().touching(world, x, z).forEach(city -> keeper.settle(city, now));
            }
        }, SETTLE_DELAY_TICKS);
    }

    /** Death, or any removal but the chunk unloading; the shulker's spot starts its cooldown. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Shulker shulker && event.getCause() != EntityRemoveEvent.Cause.UNLOAD) {
            keeper.gone(shulker);
        }
    }
}
