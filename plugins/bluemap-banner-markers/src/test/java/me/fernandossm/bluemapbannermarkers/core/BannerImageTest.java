package me.fernandossm.bluemapbannermarkers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

class BannerImageTest {

    private static final int WHITE_TEXTURE = 0xFFFFFFFF;

    /** A 64x64 texture whose front (20x40 at 1,1) is covered where the predicate says, white and opaque. */
    private static BufferedImage texture(java.util.function.BiPredicate<Integer, Integer> covered) {
        BufferedImage t = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 20; x++) {
                if (covered.test(x, y)) {
                    t.setRGB(1 + x, 1 + y, WHITE_TEXTURE);
                }
            }
        }
        // Outside the front (the back, the edges) is never drawn: make it loud.
        t.setRGB(0, 0, 0xFFFF0000);
        t.setRGB(30, 10, 0xFFFF0000);
        return t;
    }

    private final Map<String, BufferedImage> textures = new HashMap<>(Map.of(
            BannerImage.texturePath("minecraft:base"), texture((x, y) -> true),
            BannerImage.texturePath("minecraft:stripe_top"), texture((x, y) -> y < 8),
            BannerImage.texturePath("minecraft:half_vertical"), texture((x, y) -> x < 10)));

    @Test
    void theBaseIsTheBaseTextureTintedWithTheBaseColour() {
        BufferedImage image = BannerImage.render(new BannerDesign(0x3C44AA, List.of()), textures::get, 1);
        assertEquals(20, image.getWidth());
        assertEquals(40, image.getHeight());
        assertEquals(0xFF3C44AA, image.getRGB(0, 0));
        assertEquals(0xFF3C44AA, image.getRGB(19, 39));
    }

    @Test
    void layersAreTintedAndDrawnInOrderWhereTheirTextureCovers() {
        BannerDesign design = new BannerDesign(0xF9FFFE, List.of(
                new BannerDesign.Layer("minecraft:half_vertical", 0x1D1D21), // left half black
                new BannerDesign.Layer("minecraft:stripe_top", 0xB02E26)));   // then a red top stripe over it
        BufferedImage image = BannerImage.render(design, textures::get, 1);
        assertEquals(0xFFB02E26, image.getRGB(2, 2), "top left: the stripe, drawn last");
        assertEquals(0xFFB02E26, image.getRGB(15, 2), "top right: the stripe");
        assertEquals(0xFF1D1D21, image.getRGB(2, 20), "left: the half");
        assertEquals(0xFFF9FFFE, image.getRGB(15, 20), "right: the base");
    }

    @Test
    void tintingMultipliesEachChannel() {
        assertEquals(0xFF7F0000 & 0xFFFFFFFF, BannerImage.tint(0xFFFFFFFF, 0x7F0000));
        assertEquals(0x80404040, BannerImage.tint(0x80808080, 0x808080));
    }

    @Test
    void halfTransparentPatternsBlend() {
        assertEquals(0xFF7F7F7F, BannerImage.over(0x80000000 | 0x000000, 0xFFFFFFFF) | 0, "black at half alpha over white");
        assertEquals(0xFFFFFFFF, BannerImage.over(0x00000000, 0xFFFFFFFF));
    }

    @Test
    void scaleTwoDoublesEveryPixel() {
        BannerDesign design = new BannerDesign(0xF9FFFE, List.of(new BannerDesign.Layer("minecraft:stripe_top", 0xB02E26)));
        BufferedImage small = BannerImage.render(design, textures::get, 1);
        BufferedImage large = BannerImage.render(design, textures::get, 2);
        assertEquals(40, large.getWidth());
        assertEquals(80, large.getHeight());
        for (int y = 0; y < 80; y++) {
            for (int x = 0; x < 40; x++) {
                assertEquals(small.getRGB(x / 2, y / 2), large.getRGB(x, y));
            }
        }
    }

    @Test
    void aPatternWithoutTextureIsSkipped() {
        BannerDesign design = new BannerDesign(0xF9FFFE, List.of(new BannerDesign.Layer("somepack:unknown", 0x000000)));
        assertEquals(0xFFF9FFFE, BannerImage.render(design, textures::get, 1).getRGB(5, 5));
    }

    @Test
    void texturePathsFollowTheAssetId() {
        assertEquals("assets/minecraft/textures/entity/banner/creeper.png", BannerImage.texturePath("minecraft:creeper"));
        assertEquals("assets/pack/textures/entity/banner/flag.png", BannerImage.texturePath("pack:flag"));
    }

    @Test
    void designKeysAreStableAndDiffer() {
        BannerDesign a = new BannerDesign(0xF9FFFE, List.of(new BannerDesign.Layer("minecraft:creeper", 0x1D1D21)));
        BannerDesign b = new BannerDesign(0xF9FFFE, List.of(new BannerDesign.Layer("minecraft:creeper", 0xB02E26)));
        assertEquals(a.key(), new BannerDesign(0xF9FFFE, List.of(new BannerDesign.Layer("minecraft:creeper", 0x1D1D21))).key());
        assertNotEquals(a.key(), b.key());
        assertEquals(12, a.key().length());
    }

    /** Banners are tinted with the dye's texture colour, which is what DyeColor.getColor() is. */
    @Test
    void dyeColoursAreTheGamesTextureColours() {
        assertEquals(0xF9FFFE, DyeColor.WHITE.getColor().asRGB());
        assertEquals(0x1D1D21, DyeColor.BLACK.getColor().asRGB());
        assertEquals(0xB02E26, DyeColor.RED.getColor().asRGB());
        assertEquals(0x3C44AA, DyeColor.BLUE.getColor().asRGB());
    }
}
