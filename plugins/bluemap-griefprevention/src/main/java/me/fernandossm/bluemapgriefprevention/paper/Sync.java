package me.fernandossm.bluemapgriefprevention.paper;

import java.util.function.Supplier;
import me.fernandossm.bluemapgriefprevention.core.Markers;
import me.fernandossm.bluemapgriefprevention.core.Settings;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Keeps the maps in step with the claims. Changes are drawn on the tick after they happen: GriefPrevention's
 * events fire before the claim changes, so the claim is read once it has. Any number of changes in one tick
 * cost one redraw, and a redraw only replaces markers that differ.
 */
public final class Sync {

    private final Plugin plugin;
    private final Supplier<Settings> settings;
    private final Supplier<ClaimSource> source;
    private final MapSink sink;
    private boolean pending;
    private BukkitTask periodic;

    public Sync(Plugin plugin, Supplier<Settings> settings, Supplier<ClaimSource> source, MapSink sink) {
        this.plugin = plugin;
        this.settings = settings;
        this.source = source;
        this.sink = sink;
    }

    /** A claim changed: redraw on the next tick, once for any number of changes. */
    public void requestSync() {
        if (pending) {
            return;
        }
        pending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending = false;
            syncNow();
        });
    }

    /** Redraws now; does nothing until the map is up (the redraw when it comes up catches everything). */
    public void syncNow() {
        if (sink.ready()) {
            Settings s = settings.get();
            sink.show(Markers.of(source.get().claims(), s), s);
        }
    }

    /** (Re)starts the safety-net redraw at the configured interval. */
    public void startPeriodic() {
        stopPeriodic();
        long ticks = Math.max(20, settings.get().resyncInterval().toMillis() / 50);
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::syncNow, ticks, ticks);
    }

    public void stopPeriodic() {
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
    }
}
