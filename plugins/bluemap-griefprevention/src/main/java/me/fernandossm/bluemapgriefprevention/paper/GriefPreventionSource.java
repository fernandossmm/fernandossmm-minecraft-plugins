package me.fernandossm.bluemapgriefprevention.paper;

import java.util.ArrayList;
import java.util.List;
import me.fernandossm.bluemapgriefprevention.core.ClaimView;
import me.fernandossm.bluemapgriefprevention.core.Rect;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

/** GriefPrevention's top-level claims; subdivisions are inside their parent and are not drawn. */
public final class GriefPreventionSource implements ClaimSource {

    @Override
    public List<ClaimView> claims() {
        List<ClaimView> views = new ArrayList<>();
        for (Claim claim : GriefPrevention.instance.dataStore.getClaims()) {
            if (claim.parent != null || !claim.inDataStore) {
                continue;
            }
            Location lesser = claim.getLesserBoundaryCorner();
            Location greater = claim.getGreaterBoundaryCorner();
            if (lesser.getWorld() == null) {
                continue;
            }
            Rect area = new Rect(
                    Math.min(lesser.getBlockX(), greater.getBlockX()), Math.min(lesser.getBlockZ(), greater.getBlockZ()),
                    Math.max(lesser.getBlockX(), greater.getBlockX()), Math.max(lesser.getBlockZ(), greater.getBlockZ()));
            boolean admin = claim.isAdminClaim();
            views.add(new ClaimView(claim.getID(), lesser.getWorld().getName(), area, surfaceY(greater),
                    admin ? null : claim.ownerID, admin ? "" : claim.getOwnerName(), admin));
        }
        return views;
    }

    /**
     * The top of the higher corner block the claim was made on. GriefPrevention keeps that block's height,
     * less ExtendIntoGroundDistance, as the greater corner's y; the lesser corner's y is the bottom of the
     * protection, usually near bedrock, which is no place to draw an overlay.
     */
    private static int surfaceY(Location greater) {
        int y = greater.getBlockY() + GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance + 1;
        return Math.clamp(y, greater.getWorld().getMinHeight(), greater.getWorld().getMaxHeight());
    }
}
