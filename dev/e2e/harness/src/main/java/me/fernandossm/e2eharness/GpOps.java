package me.fernandossm.e2eharness;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.UUID;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** /gptest: GriefPrevention claims made and changed through its own API, and a player's locator bar colour. */
final class GpOps {

    private GpOps() {}

    /** "admin" for an admin claim; otherwise a player who has joined (offline-mode UUID by name). */
    private static UUID owner(String name) {
        return name.equals("admin") ? null : Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    static JsonObject claim(String worldName, String owner, int x1, int z1, int x2, int z2) {
        World world = Bukkit.getWorld(worldName);
        CreateClaimResult result = GriefPrevention.instance.dataStore.createClaim(
                world, x1, x2, 64, 64, z1, z2, owner(owner), null, null, null);
        JsonObject o = new JsonObject();
        o.addProperty("succeeded", result.succeeded);
        o.addProperty("id", result.claim != null ? result.claim.getID() : -1);
        return o;
    }

    private static Claim byId(long id) {
        return GriefPrevention.instance.dataStore.getClaim(id);
    }

    /** Through the API, which fires no event: only the periodic redraw can notice. */
    static JsonObject resize(long id, int x1, int z1, int x2, int z2) {
        CreateClaimResult result = GriefPrevention.instance.dataStore.resizeClaim(byId(id), x1, x2, 64, 64, z1, z2, null);
        JsonObject o = new JsonObject();
        o.addProperty("succeeded", result.succeeded);
        return o;
    }

    static JsonObject transfer(long id, String owner) {
        GriefPrevention.instance.dataStore.changeClaimOwner(byId(id), owner(owner));
        JsonObject o = new JsonObject();
        o.addProperty("owner", byId(id).getOwnerName());
        return o;
    }

    static JsonObject delete(long id) {
        GriefPrevention.instance.dataStore.deleteClaim(byId(id));
        JsonObject o = new JsonObject();
        o.addProperty("deleted", id);
        return o;
    }

    /** What Minecraft itself computes for the locator bar, by calling its own code on this server. */
    static JsonObject locatorColor(String player) {
        JsonObject o = new JsonObject();
        try {
            Class<?> argb = Class.forName("net.minecraft.util.ARGB", true, Bukkit.getServer().getClass().getClassLoader());
            Method color = argb.getMethod("color", int.class, int.class);
            Method setBrightness = argb.getMethod("setBrightness", int.class, float.class);
            int base = (int) color.invoke(null, 255, owner(player).hashCode());
            int result = (int) setBrightness.invoke(null, base, 0.9f);
            o.addProperty("rgb", String.format("%06x", result & 0xFFFFFF));
        } catch (ReflectiveOperationException e) {
            o.addProperty("error", e.toString());
        }
        return o;
    }
}
