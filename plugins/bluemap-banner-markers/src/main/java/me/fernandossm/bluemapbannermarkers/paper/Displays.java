package me.fernandossm.bluemapbannermarkers.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The name over each waypoint banner: a text display, never saved with the world (so uninstalling the plugin
 * leaves none behind), spawned when its chunk loads and gone when it unloads.
 */
public final class Displays {

    /**
     * Heights above the banner's block, just over its crossbar: the game draws banners at 2/3 scale, a standing
     * banner's bar reaching 1.83 blocks up, a wall banner's 0.85, the latter 0.44 from the block's centre
     * towards the wall.
     */
    static final double STANDING_HEIGHT = 1.95;
    static final double WALL_HEIGHT = 0.97;
    static final double WALL_INSET = 0.44;

    private final NamespacedKey key;
    private final Map<Integer, UUID> entities = new HashMap<>();

    public Displays(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "waypoint");
    }

    /** Where the name floats for the banner at this block. */
    public static Location place(Block banner) {
        Location at = banner.getLocation().add(0.5, 0, 0.5);
        if (banner.getBlockData() instanceof Directional wall) {
            BlockFace facing = wall.getFacing();
            return at.add(-facing.getModX() * WALL_INSET, WALL_HEIGHT, -facing.getModZ() * WALL_INSET);
        }
        return at.add(0, STANDING_HEIGHT, 0);
    }

    /** Spawns or renames the display, if the banner's chunk is loaded. */
    public void show(Waypoint w) {
        World world = Bukkit.getWorld(w.world());
        if (world == null || !world.isChunkLoaded(w.x() >> 4, w.z() >> 4)) {
            return;
        }
        Entity existing = entity(w.id());
        if (existing instanceof TextDisplay display) {
            display.text(Component.text(w.name()));
            return;
        }
        TextDisplay display = world.spawn(place(world.getBlockAt(w.x(), w.y(), w.z())), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(Component.text(w.name()));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, w.id());
        });
        entities.put(w.id(), display.getUniqueId());
    }

    public void hide(int id) {
        Entity entity = entity(id);
        if (entity != null) {
            entity.remove();
        }
        entities.remove(id);
    }

    private Entity entity(int id) {
        UUID uuid = entities.get(id);
        Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        return entity != null && entity.isValid() ? entity : null;
    }

    /** Removes every display of ours in this chunk that is not the one we track (left over from a reload). */
    public void sweep(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            Integer id = entity.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            if (id != null && !entity.getUniqueId().equals(entities.get(id))) {
                entity.remove();
            }
        }
    }

    public void hideAll() {
        for (int id : Map.copyOf(entities).keySet()) {
            hide(id);
        }
    }
}
