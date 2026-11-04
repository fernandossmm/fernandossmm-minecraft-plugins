package me.fernandossm.bluemapgriefprevention.paper;

import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimExpirationEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimExtendEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimTransferEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Every GriefPrevention event that can change what the map shows asks for a redraw. */
public final class ClaimListener implements Listener {

    private final Sync sync;

    public ClaimListener(Sync sync) {
        this.sync = sync;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreated(ClaimCreatedEvent event) {
        sync.requestSync();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeleted(ClaimDeletedEvent event) {
        sync.requestSync();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResized(ClaimResizeEvent event) {
        sync.requestSync();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtended(ClaimExtendEvent event) {
        sync.requestSync();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransferred(ClaimTransferEvent event) {
        sync.requestSync();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpired(ClaimExpirationEvent event) {
        sync.requestSync();
    }
}
