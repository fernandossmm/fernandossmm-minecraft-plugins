package me.fernandossm.bluemapbannermarkers.paper;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import java.util.List;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Name tags on banners make waypoints. Anything that can take a banner away (breaking it or what holds it up,
 * explosions, pistons) has it checked on the next tick, once the change is done, whoever cancelled what.
 */
public final class WaypointListener implements Listener {

    /** A piston pushes up to 12 blocks, and what they hold up falls with them. */
    private static final int PISTON_REACH = 14;

    private final Plugin plugin;
    private final Waypoints waypoints;
    private final FaceStore faces;

    public WaypointListener(Plugin plugin, Waypoints waypoints, FaceStore faces) {
        this.plugin = plugin;
        this.waypoints = waypoints;
        this.faces = faces;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !block.getType().name().endsWith("BANNER")) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String name = Waypoints.tagName(item);
        if (name == null) {
            return;
        }
        // Both hands fire; a name tag in the main hand is the one used.
        if (event.getHand() == EquipmentSlot.OFF_HAND && player.getInventory().getItemInMainHand().getType() == Material.NAME_TAG) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (waypoints.name(player, block, name)) {
            Waypoints.consume(player, item);
            faces.refresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        near(event.getBlock(), 1, "broken by " + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        near(event.getBlock(), 1, "destroyed (block physics)");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        exploded(event.blockList(), "by an explosion");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        exploded(event.blockList(), "blown up by " + event.getEntityType().getKey().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        near(event.getBlock(), PISTON_REACH, "by a piston");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        near(event.getBlock(), PISTON_REACH, "by a piston");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        waypoints.chunkLoaded(event.getChunk());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<Waypoint> owned = waypoints.store().ownedBy(player.getUniqueId());
        if (owned.isEmpty()) {
            return;
        }
        boolean renamed = false;
        for (Waypoint w : owned) {
            if (!w.ownerName().equals(player.getName())) {
                waypoints.store().update(w.withOwnerName(player.getName()));
                renamed = true;
            }
        }
        if (renamed) {
            waypoints.requestRedraw();
        }
        // SkinsRestorer applies the skin shortly after joining.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                faces.refresh(player);
            }
        }, 100);
    }

    /** One check for the whole blast, around every block it destroys. */
    private void exploded(List<Block> blocks, String cause) {
        if (blocks.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Block b : blocks) {
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            minZ = Math.min(minZ, b.getZ());
            maxX = Math.max(maxX, b.getX());
            maxY = Math.max(maxY, b.getY());
            maxZ = Math.max(maxZ, b.getZ());
        }
        waypoints.verifyBox(blocks.getFirst().getWorld(), minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1, cause);
    }

    private void near(Block block, int distance, String cause) {
        waypoints.verifyNear(block.getWorld(), block.getX(), block.getY(), block.getZ(), distance, cause);
    }
}
