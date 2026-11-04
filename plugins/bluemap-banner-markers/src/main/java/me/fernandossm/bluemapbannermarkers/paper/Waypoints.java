package me.fernandossm.bluemapbannermarkers.paper;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import java.util.function.Supplier;
import me.fernandossm.bluemapbannermarkers.core.BannerDesign;
import me.fernandossm.bluemapbannermarkers.core.Names;
import me.fernandossm.bluemapbannermarkers.core.Settings;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import me.fernandossm.bluemapbannermarkers.store.WaypointStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/** What players and staff do with waypoints, and keeping the world, the store and the map in agreement. */
public final class Waypoints {

    public static final String USE = "bluemapbannermarkers.use";
    public static final String UNLIMITED = "bluemapbannermarkers.unlimited";

    private final Plugin plugin;
    private final Supplier<Settings> settings;
    private final WaypointStore store;
    private final Displays displays;
    private final BlueMapSink sink;
    private final Supplier<BuildCheck> builds;
    private boolean redrawPending;
    private @Nullable BukkitTask periodic;

    public Waypoints(Plugin plugin, Supplier<Settings> settings, WaypointStore store, Displays displays, BlueMapSink sink,
                     Supplier<BuildCheck> builds) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.displays = displays;
        this.sink = sink;
        this.builds = builds;
    }

    public WaypointStore store() {
        return store;
    }

    /** The name a name tag carries, or null for a tag that was never named. */
    public static @Nullable String tagName(@Nullable ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.NAME_TAG || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomName()) {
            return null;
        }
        return Names.clean(PlainTextComponentSerializer.plainText().serialize(meta.customName()));
    }

    /**
     * The player right-clicked this banner with a name tag carrying this name. Returns whether the tag is used
     * up; the reason is told to the player either way.
     */
    public boolean name(Player player, Block block, String name) {
        if (!(block.getState(false) instanceof Banner banner) || !player.hasPermission(USE)) {
            return false;
        }
        String world = block.getWorld().getName();
        if (!settings.get().allows(world)) {
            player.sendMessage(Component.text("Banners in this world cannot be waypoints.", NamedTextColor.RED));
            return false;
        }
        Waypoint existing = store.at(world, block.getX(), block.getY(), block.getZ()).orElse(null);
        if (existing != null && !existing.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("This banner is " + existing.ownerName() + "'s waypoint, \"" + existing.name()
                    + "\". Only " + existing.ownerName() + " can rename it.", NamedTextColor.RED));
            return false;
        }
        if (!builds.get().mayBuild(player, block)) {
            player.sendMessage(Component.text("You need permission to build here to make this banner a waypoint.", NamedTextColor.RED));
            return false;
        }
        if (existing != null) {
            Waypoint renamed = existing.withName(name).withOwnerName(player.getName());
            store.update(renamed);
            displays.show(renamed);
            requestRedraw();
            player.sendMessage(Component.text("Waypoint renamed to \"" + name + "\".", NamedTextColor.GREEN));
            return true;
        }
        int max = settings.get().maxPerPlayer();
        int owned = store.ownedBy(player.getUniqueId()).size();
        if (owned >= max && !player.hasPermission(UNLIMITED)) {
            player.sendMessage(Component.text("You have " + owned + " waypoints, the most allowed. Break one of your waypoint"
                    + " banners to make a new one.", NamedTextColor.RED));
            return false;
        }
        Waypoint created = store.add(name, player.getUniqueId(), player.getName(), world, block.getX(), block.getY(), block.getZ(),
                design(banner));
        displays.show(created);
        requestRedraw();
        player.sendMessage(Component.text("Waypoint \"" + name + "\" added to the map.", NamedTextColor.GREEN));
        return true;
    }

    /** Uses up one name tag from the hand, unless in creative. */
    public static void consume(Player player, ItemStack item) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    static BannerDesign design(Banner banner) {
        return new BannerDesign(banner.getBaseColor().getColor().asRGB(), banner.getPatterns().stream()
                .map(p -> new BannerDesign.Layer(RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN).getKeyOrThrow(p.getPattern()).asString(), p.getColor().getColor().asRGB()))
                .toList());
    }

    /** Removes the waypoint if its banner is gone; true if it was. Only for waypoints in loaded chunks. */
    public boolean verify(Waypoint w, String cause) {
        World world = Bukkit.getWorld(w.world());
        if (world == null || !world.isChunkLoaded(w.x() >> 4, w.z() >> 4)) {
            return false;
        }
        Block block = world.getBlockAt(w.x(), w.y(), w.z());
        if (block.getState(false) instanceof Banner) {
            return false;
        }
        remove(w.id(), "its banner is gone, " + cause + " (" + block.getType().getKey().getKey() + " there now)");
        return true;
    }

    /** On the next tick (once the block changes are done), checks the waypoints within this distance of a block. */
    public void verifyNear(World world, int x, int y, int z, int distance, String cause) {
        verifyBox(world, x - distance, y - distance, z - distance, x + distance, y + distance, z + distance, cause);
    }

    /** On the next tick, checks the waypoints inside this box (bounds included). */
    public void verifyBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String cause) {
        String name = world.getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Waypoint w : store.all()) {
                if (w.world().equals(name) && w.x() >= minX && w.x() <= maxX && w.y() >= minY && w.y() <= maxY
                        && w.z() >= minZ && w.z() <= maxZ) {
                    verify(w, cause);
                }
            }
        });
    }

    /** A chunk loaded: its banners may have changed while it was away; its names need displays again. */
    public void chunkLoaded(Chunk chunk) {
        String world = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded()) {
                return;
            }
            displays.sweep(chunk);
            for (Waypoint w : store.inChunk(world, cx, cz)) {
                if (!verify(w, "noticed when its chunk loaded")) {
                    displays.show(w);
                }
            }
        });
    }

    /** @param why logged, e.g. "deleted by Steve" */
    public @Nullable Waypoint remove(int id, String why) {
        Waypoint removed = store.remove(id);
        displays.hide(id);
        if (removed != null) {
            plugin.getLogger().info("Removed waypoint " + id + " \"" + removed.name() + "\" of " + removed.ownerName() + " at "
                    + removed.world() + " " + removed.x() + " " + removed.y() + " " + removed.z() + ": " + why);
            requestRedraw();
        }
        return removed;
    }

    public void scale(Waypoint w, int scale) {
        store.update(w.withScale(scale));
        requestRedraw();
    }

    /** Every waypoint whose chunk is loaded: displays shown, gone banners dropped. */
    public void checkLoaded() {
        for (Waypoint w : store.all()) {
            if (!verify(w, "noticed by the periodic check")) {
                displays.show(w);
            }
        }
    }

    /** Draws the map on the next tick, once for any number of changes in this one. */
    public void requestRedraw() {
        if (redrawPending) {
            return;
        }
        redrawPending = true;
        Bukkit.getScheduler().runTask(plugin, this::redrawNow);
    }

    public void redrawNow() {
        redrawPending = false;
        if (sink.ready()) {
            sink.show(store.all(), settings.get());
        }
    }

    /** Every minute, catches banners removed with no event (commands, other plugins) in loaded chunks. */
    public void startPeriodic() {
        stopPeriodic();
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::checkLoaded, 1200, 1200);
    }

    public void stopPeriodic() {
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
    }

    public List<Waypoint> all() {
        return store.all();
    }
}
