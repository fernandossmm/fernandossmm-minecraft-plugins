package me.fernandossm.endcityshulkerrespawn.api;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A shulker is about to come back to its spot in an End city. Cancelling it leaves the spot empty; it is
 * tried again the next time its chunk loads.
 */
public final class CityShulkerRespawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final String city;
    private final int spot;
    private boolean cancelled;

    public CityShulkerRespawnEvent(Location location, String city, int spot) {
        this.location = location.clone();
        this.city = city;
        this.spot = spot;
    }

    /** Where the shulker will appear. */
    public Location getLocation() {
        return location.clone();
    }

    /** The city's key: the lowest corner of its bounding box, "x,y,z". */
    public String getCity() {
        return city;
    }

    public int getSpot() {
        return spot;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
