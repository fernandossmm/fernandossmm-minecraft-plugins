package me.fernandossm.bluemapbannermarkers.core;

import java.awt.image.BufferedImage;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Draws a banner's front as the game does: the "base" texture tinted with the base colour, then each
 * pattern's texture tinted with its dye, in order. Banner textures are 64x64; the front of the flag is the
 * 20x40 area at (1, 1). Tinting multiplies each channel by the dye's; a pattern's alpha says where it covers.
 */
public final class BannerImage {

    public static final int WIDTH = 20;
    public static final int HEIGHT = 40;
    private static final int LEFT = 1;
    private static final int TOP = 1;

    private BannerImage() {}

    /** Where a pattern's texture is in the client jar: "minecraft:creeper" is entity/banner/creeper.png. */
    public static String texturePath(String asset) {
        int colon = asset.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : asset.substring(0, colon);
        String path = colon < 0 ? asset : asset.substring(colon + 1);
        return "assets/" + namespace + "/textures/entity/banner/" + path + ".png";
    }

    /**
     * @param textures a texture by its path in the client jar, or null if it is missing (that layer is skipped)
     * @param scale    1 for 20x40, 2 for 40x80; pixels stay sharp
     */
    public static BufferedImage render(BannerDesign design, Function<String, @Nullable BufferedImage> textures, int scale) {
        int[] front = new int[WIDTH * HEIGHT];
        paint(front, textures.apply(texturePath("minecraft:base")), design.baseColor());
        for (BannerDesign.Layer layer : design.layers()) {
            paint(front, textures.apply(texturePath(layer.asset())), layer.color());
        }
        BufferedImage image = new BufferedImage(WIDTH * scale, HEIGHT * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT * scale; y++) {
            for (int x = 0; x < WIDTH * scale; x++) {
                image.setRGB(x, y, front[(y / scale) * WIDTH + x / scale]);
            }
        }
        return image;
    }

    private static void paint(int[] front, @Nullable BufferedImage texture, int color) {
        if (texture == null || texture.getWidth() < LEFT + WIDTH || texture.getHeight() < TOP + HEIGHT) {
            return;
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int t = texture.getRGB(LEFT + x, TOP + y);
                front[y * WIDTH + x] = over(tint(t, color), front[y * WIDTH + x]);
            }
        }
    }

    static int tint(int argb, int rgb) {
        int r = ((argb >> 16) & 0xFF) * ((rgb >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((rgb >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (rgb & 0xFF) / 255;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Source over destination, for straight (not premultiplied) alpha. */
    static int over(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) {
            return src;
        }
        if (sa == 0) {
            return dst;
        }
        int da = dst >>> 24;
        int outA = sa + da * (255 - sa) / 255;
        if (outA == 0) {
            return 0;
        }
        int r = channel(src >> 16, sa, dst >> 16, da, outA);
        int g = channel(src >> 8, sa, dst >> 8, da, outA);
        int b = channel(src, sa, dst, da, outA);
        return (outA << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int s, int sa, int d, int da, int outA) {
        s &= 0xFF;
        d &= 0xFF;
        return Math.round((s * sa + d * da * (255 - sa) / 255f) / outA);
    }
}
