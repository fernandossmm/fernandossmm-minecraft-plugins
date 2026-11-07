package me.fernandossm.bluemapskinsrestorerheads.core;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What counts as a skin, where one may be downloaded from, and the default a player without one gets. */
public final class Skins {

    /** Skins come only from Mojang's texture server, which is where every profile skin points. */
    public static final String SKIN_HOST = "textures.minecraft.net";

    /** A skin is a small PNG; anything bigger is not one. */
    public static final int MAX_SKIN_BYTES = 64 * 1024;

    /** The client's default skins, in its order (DefaultPlayerSkin): a player without a skin gets one by UUID. */
    private static final List<String> DEFAULT_SKINS = List.of(
            "slim/alex", "slim/ari", "slim/efe", "slim/kai", "slim/makena", "slim/noor", "slim/steve", "slim/sunny", "slim/zuri",
            "wide/alex", "wide/ari", "wide/efe", "wide/kai", "wide/makena", "wide/noor", "wide/steve", "wide/sunny", "wide/zuri");

    private Skins() {}

    /** The default skin the game shows for a player who has none, as a path in the client jar. */
    public static String defaultSkinPath(UUID player) {
        return "assets/minecraft/textures/entity/player/" + DEFAULT_SKINS.get(Math.floorMod(player.hashCode(), DEFAULT_SKINS.size())) + ".png";
    }

    /** The skin's address if it may be downloaded: https or http on Mojang's texture server; otherwise null. */
    public static @Nullable URI downloadable(@Nullable String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return SKIN_HOST.equals(uri.getHost()) && ("https".equals(scheme) || "http".equals(scheme)) ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whether an image has a skin's size: 64x64, or the old 64x32. */
    public static boolean isSkin(@Nullable BufferedImage image) {
        return image != null && image.getWidth() == 64 && (image.getHeight() == 64 || image.getHeight() == 32);
    }

    /**
     * The skin as the game draws it. Old 64x32 skins often fill their hat layer with an opaque colour, and the
     * game then ignores the whole layer (the "Notch transparency hack"); BlueMap would draw it, hiding the face
     * under a solid square. Such a skin is returned with its hat layer cleared; any other skin as it is.
     */
    public static BufferedImage asShownInGame(BufferedImage skin) {
        if (skin.getHeight() != 32 || hasTransparency(skin)) {
            return skin;
        }
        BufferedImage copy = new BufferedImage(skin.getWidth(), skin.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < skin.getHeight(); y++) {
            for (int x = 0; x < skin.getWidth(); x++) {
                copy.setRGB(x, y, x >= 32 && y < 16 ? 0 : skin.getRGB(x, y));
            }
        }
        return copy;
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
