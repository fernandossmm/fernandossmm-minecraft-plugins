package me.fernandossm.e2eharness;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.World;

/** /ecsrtest claim and unclaim: real GriefPrevention claims, made through its own API as an admin would. */
final class Claims {

    private Claims() {}

    /** An admin claim on the 3×3 columns around a block; GriefPrevention claims reach from bedrock to the sky. */
    static JsonObject claim(World world, int x, int z) {
        JsonObject o = new JsonObject();
        o.addProperty("claimsEnabled", GriefPrevention.instance.claimsEnabledForWorld(world));
        CreateClaimResult result = GriefPrevention.instance.dataStore.createClaim(
                world, x - 1, x + 1, world.getMinHeight(), world.getMaxHeight() - 1, z - 1, z + 1, null, null, null, null);
        o.addProperty("succeeded", result.succeeded);
        o.addProperty("id", result.claim != null ? result.claim.getID() : -1);
        return o;
    }

    static JsonObject unclaimAll() {
        int deleted = 0;
        for (Claim claim : new ArrayList<>(GriefPrevention.instance.dataStore.getClaims())) {
            GriefPrevention.instance.dataStore.deleteClaim(claim);
            deleted++;
        }
        JsonObject o = new JsonObject();
        o.addProperty("deleted", deleted);
        return o;
    }
}
