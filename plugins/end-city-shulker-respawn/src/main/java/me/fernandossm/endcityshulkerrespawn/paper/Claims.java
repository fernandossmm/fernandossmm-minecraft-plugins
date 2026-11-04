package me.fernandossm.endcityshulkerrespawn.paper;

import org.bukkit.Location;

/** Whether a location is inside someone's land claim. */
@FunctionalInterface
public interface Claims {

    Claims NONE = location -> false;

    boolean claimed(Location location);
}
