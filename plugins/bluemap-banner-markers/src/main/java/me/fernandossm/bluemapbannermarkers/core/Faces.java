package me.fernandossm.bluemapbannermarkers.core;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A player's face, cut from their skin as the game draws a head: the face, with the hat layer over it. */
public final class Faces {

    /** Pixels per skin pixel in the image shown in the popup. */
    public static final int SCALE = 4;

    /** The client's default skins, in its order (DefaultPlayerSkin): a player without a skin gets one by UUID. */
    private static final List<String> DEFAULT_SKINS = List.of(
            "slim/alex", "slim/ari", "slim/efe", "slim/kai", "slim/makena", "slim/noor", "slim/steve", "slim/sunny", "slim/zuri",
            "wide/alex", "wide/ari", "wide/efe", "wide/kai", "wide/makena", "wide/noor", "wide/steve", "wide/sunny", "wide/zuri");

    private Faces() {}

    /** The default skin the game shows for a player who has none, as a path in the client jar. */
    public static String defaultSkinPath(UUID player) {
        return "assets/minecraft/textures/entity/player/" + DEFAULT_SKINS.get(Math.floorMod(player.hashCode(), DEFAULT_SKINS.size())) + ".png";
    }

    /** The 8x8 face with its hat layer, scaled up by {@link #SCALE}; null if this is not a skin. */
    public static @Nullable BufferedImage face(BufferedImage skin) {
        int w = skin.getWidth();
        int h = skin.getHeight();
        if (w != 64 || (h != 64 && h != 32)) {
            return null;
        }
        // Old 64x32 skins often fill the hat layer with an opaque colour; the game then ignores the whole
        // layer (the "Notch transparency hack"). 64x64 skins use their alpha as it is.
        boolean hat = h == 64 || hasTransparency(skin);
        BufferedImage face = new BufferedImage(8 * SCALE, 8 * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int pixel = skin.getRGB(8 + x, 8 + y) | 0xFF000000;
                if (hat) {
                    pixel = BannerImage.over(skin.getRGB(40 + x, 8 + y), pixel);
                }
                for (int dy = 0; dy < SCALE; dy++) {
                    for (int dx = 0; dx < SCALE; dx++) {
                        face.setRGB(x * SCALE + dx, y * SCALE + dy, pixel);
                    }
                }
            }
        }
        return face;
    }

    /** Whether the hat area of a 64x32 skin (32..63 x 0..15) has any transparent pixel. */
    private static boolean hasTransparency(BufferedImage skin) {
        for (int y = 0; y < 16; y++) {
            for (int x = 32; x < 64; x++) {
                if ((skin.getRGB(x, y) >>> 24) < 128) {
                    return true;
                }
            }
        }
        return false;
    }
}
