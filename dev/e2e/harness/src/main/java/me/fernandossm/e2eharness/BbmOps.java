package me.fernandossm.e2eharness;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

/** /bbmtest: what BlueMap Banner Markers put in the world and on disk, read independently of the plugin. */
final class BbmOps {

    private static final NamespacedKey WAYPOINT = new NamespacedKey("bluemapbannermarkers", "waypoint");

    private BbmOps() {}

    /** Every text display within r blocks: its text, position and waypoint tag. */
    static JsonObject displays(String worldName, double x, double y, double z, double r) {
        World world = Bukkit.getWorld(worldName);
        JsonArray list = new JsonArray();
        for (TextDisplay d : world.getNearbyEntitiesByType(TextDisplay.class, new Location(world, x, y, z), r)) {
            JsonObject o = new JsonObject();
            o.addProperty("text", PlainTextComponentSerializer.plainText().serialize(d.text()));
            o.addProperty("x", d.getLocation().getX());
            o.addProperty("y", d.getLocation().getY());
            o.addProperty("z", d.getLocation().getZ());
            o.addProperty("persistent", d.isPersistent());
            o.addProperty("billboard", d.getBillboard().name());
            o.addProperty("seeThrough", d.isSeeThrough());
            Integer id = d.getPersistentDataContainer().get(WAYPOINT, PersistentDataType.INTEGER);
            o.addProperty("waypoint", id);
            list.add(o);
        }
        JsonObject o = new JsonObject();
        o.add("displays", list);
        return o;
    }

    /**
     * A pixel of an image: a file under the server folder, or "jar:PATH" for a texture in the client jar
     * BlueMap downloaded. Returns its size and the pixel as AARRGGBB.
     */
    static JsonObject pixel(String path, int x, int y) {
        JsonObject o = new JsonObject();
        try {
            BufferedImage image;
            if (path.startsWith("jar:")) {
                File jar = new File(Bukkit.getWorldContainer(), "bluemap/minecraft-client-" + Bukkit.getMinecraftVersion() + ".jar");
                try (ZipFile zip = new ZipFile(jar); InputStream in = zip.getInputStream(zip.getEntry(path.substring(4)))) {
                    image = ImageIO.read(in);
                }
            } else {
                image = ImageIO.read(new File(Bukkit.getWorldContainer(), path));
            }
            o.addProperty("width", image.getWidth());
            o.addProperty("height", image.getHeight());
            o.addProperty("argb", String.format("%08x", image.getRGB(x, y)));
        } catch (Exception e) {
            o.addProperty("error", e.toString());
        }
        return o;
    }

    /** The front-of-head and hat pixels at (dx, dy) of the skin the player has in game, fetched from Mojang. */
    static JsonObject skin(String name, int dx, int dy) {
        JsonObject o = new JsonObject();
        Player player = Bukkit.getPlayerExact(name);
        URL url = player == null ? null : player.getPlayerProfile().getTextures().getSkin();
        o.addProperty("url", url == null ? null : url.toString());
        if (url != null) {
            try (InputStream in = url.openStream()) {
                BufferedImage skin = ImageIO.read(in);
                o.addProperty("face", String.format("%08x", skin.getRGB(8 + dx, 8 + dy)));
                o.addProperty("hat", String.format("%08x", skin.getRGB(40 + dx, 8 + dy)));
                o.addProperty("height", skin.getHeight());
                // Whether the game draws the hat layer: always on 64x64 skins; on old 64x32 ones only if it has
                // a transparent pixel (an opaque one is ignored, the "Notch transparency hack").
                boolean hatShown = skin.getHeight() == 64;
                for (int y = 0; y < 16 && !hatShown; y++) {
                    for (int x = 32; x < 64 && !hatShown; x++) {
                        hatShown = (skin.getRGB(x, y) >>> 24) < 128;
                    }
                }
                o.addProperty("hatShown", hatShown);
            } catch (Exception e) {
                o.addProperty("error", e.toString());
            }
        }
        return o;
    }
}
