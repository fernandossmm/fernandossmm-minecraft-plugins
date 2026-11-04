package me.fernandossm.e2eharness;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Optional;
import me.fernandossm.endcityshulkerrespawn.EndCityShulkerRespawn;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.core.Spot;
import me.fernandossm.endcityshulkerrespawn.paper.City;
import me.fernandossm.endcityshulkerrespawn.store.ShulkerTag;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.util.BoundingBox;

/** /ecsrtest city: what End City Shulker Respawn knows about a city, and what is really in it. */
final class CityProbe {

    /** The city's box when last seen: the plugin cannot look a city up once its chunks have unloaded. */
    private static BoundingBox lastBox;

    private CityProbe() {}

    static JsonObject city(World end, int x, int z) {
        EndCityShulkerRespawn plugin = (EndCityShulkerRespawn) Bukkit.getPluginManager().getPlugin("EndCityShulkerRespawn");
        JsonObject o = new JsonObject();
        Optional<City> found = plugin.cities().touching(end, x >> 4, z >> 4).stream().findFirst();
        o.addProperty("found", found.isPresent());
        if (found.isEmpty()) {
            o.addProperty("chunksLoaded", lastBox == null ? -1 : loadedChunks(end, lastBox));
            return o;
        }
        City city = found.get();
        BoundingBox box = city.box();
        lastBox = box.clone();
        CityData data = city.load();
        long now = System.currentTimeMillis();
        o.addProperty("key", city.key());
        o.add("min", xyz(box.getMinX(), box.getMinY(), box.getMinZ()));
        o.add("max", xyz(box.getMaxX(), box.getMaxY(), box.getMaxZ()));
        o.addProperty("generation", data.generation());
        o.addProperty("learnedChunks", data.learnedChunks().size());

        o.addProperty("chunks", ((((int) Math.floor(box.getMaxX()) >> 4) - ((int) Math.floor(box.getMinX()) >> 4)) + 1)
                * ((((int) Math.floor(box.getMaxZ()) >> 4) - ((int) Math.floor(box.getMinZ()) >> 4)) + 1));
        o.addProperty("chunksLoaded", loadedChunks(end, box));

        JsonArray spots = new JsonArray();
        for (Spot spot : data.spots()) {
            JsonObject s = xyz(spot.x(), spot.y(), spot.z());
            s.addProperty("face", spot.face().name());
            s.addProperty("color", spot.color() == Spot.NO_COLOR ? "" : org.bukkit.DyeColor.values()[spot.color()].name());
            s.addProperty("alive", spot.alive());
            s.addProperty("dueInMs", spot.alive() ? 0 : spot.dueAt() - now);
            spots.add(s);
        }
        o.add("spots", spots);

        ShulkerTag.Keys tags = new ShulkerTag.Keys(plugin);
        JsonArray shulkers = new JsonArray();
        for (Entity entity : end.getNearbyEntities(box.clone().expand(1))) {
            if (entity instanceof Shulker shulker && shulker.isValid()) {
                JsonObject s = xyz(shulker.getLocation().getBlockX(), shulker.getLocation().getBlockY(), shulker.getLocation().getBlockZ());
                s.addProperty("face", shulker.getAttachedFace().name());
                s.addProperty("color", shulker.getColor() == null ? "" : shulker.getColor().name());
                s.addProperty("attachedToSolid", shulker.getLocation().getBlock().getRelative(shulker.getAttachedFace()).getType().isSolid());
                s.addProperty("spot", plugin.keeper().owner(city, data, shulker).orElse(-1));
                s.addProperty("tagged", tags.read(shulker).isPresent());
                s.addProperty("persistent", !shulker.getRemoveWhenFarAway());
                shulkers.add(s);
            }
        }
        o.add("shulkers", shulkers);
        return o;
    }

    /** Where a player is, and which city the plugin's own lookup (used by /ecsr) finds there. */
    static JsonObject whereIs(String name) {
        EndCityShulkerRespawn plugin = (EndCityShulkerRespawn) Bukkit.getPluginManager().getPlugin("EndCityShulkerRespawn");
        JsonObject o = new JsonObject();
        org.bukkit.entity.Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            o.addProperty("error", "not online");
            return o;
        }
        org.bukkit.Location l = player.getLocation();
        o.addProperty("world", l.getWorld().getName());
        o.add("at", xyz(l.getX(), l.getY(), l.getZ()));
        o.addProperty("chunkLoaded", l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4));
        o.addProperty("citiesInChunk", plugin.cities().touching(l.getWorld(), l.getBlockX() >> 4, l.getBlockZ() >> 4).size());
        o.addProperty("cityAt", plugin.cities().at(l).map(City::key).orElse(""));
        return o;
    }

    private static int loadedChunks(World end, BoundingBox box) {
        int loaded = 0;
        for (int cx = (int) Math.floor(box.getMinX()) >> 4; cx <= (int) Math.floor(box.getMaxX()) >> 4; cx++) {
            for (int cz = (int) Math.floor(box.getMinZ()) >> 4; cz <= (int) Math.floor(box.getMaxZ()) >> 4; cz++) {
                if (end.isChunkLoaded(cx, cz)) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    private static JsonObject xyz(double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        return o;
    }
}
