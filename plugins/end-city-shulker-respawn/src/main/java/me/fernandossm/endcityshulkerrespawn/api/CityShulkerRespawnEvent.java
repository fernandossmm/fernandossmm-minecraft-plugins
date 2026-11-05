package me.fernandossm.endcityshulkerrespawn.api;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/**
 * A shulker is about to come back to its spot in an End city. Cancelling it leaves the spot empty; it is
 * tried again the next time its chunk loads. Listeners can choose its colour; it comes back in the default
 * purple otherwise, whatever colour the shulker before it had.
 */
public final class CityShulkerRespawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final String city;
    private final int spot;
    private @Nullable DyeColor color;
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

    /** The new shulker's colour; null for the default purple. */
    public @Nullable DyeColor getColor() {
        return color;
    }

    public void setColor(@Nullable DyeColor color) {
        this.color = color;
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
