package me.fernandossm.endcityshulkerrespawn.paper;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

/**
 * GriefPrevention's claims. The only class that refers to GriefPrevention: it is loaded only when that
 * plugin is enabled, so servers without it never need its classes.
 */
public final class GriefPreventionClaims implements Claims {

    @Override
    public boolean claimed(Location location) {
        return GriefPrevention.instance.dataStore.getClaimAt(location, true, null) != null;
    }
}
