package me.fernandossm.bluemapgriefprevention.core;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the map needs to know about one top-level claim, read from GriefPrevention.
 *
 * @param surfaceY   the height the overlay is drawn at: the surface where the claim was made
 * @param owner      null for an admin claim
 * @param ownerName  as GriefPrevention reports it; for admin claims, ignored
 */
public record ClaimView(long id, String world, Rect area, int surfaceY, @Nullable UUID owner, String ownerName, boolean admin) {}
