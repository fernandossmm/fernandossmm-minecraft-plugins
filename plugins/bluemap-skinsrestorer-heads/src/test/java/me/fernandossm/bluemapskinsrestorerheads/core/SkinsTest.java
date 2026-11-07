package me.fernandossm.bluemapskinsrestorerheads.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkinsTest {

    @Test
    void onlyMojangsTextureServerIsDownloaded() {
        assertNotNull(Skins.downloadable("http://textures.minecraft.net/texture/abc123"));
        assertNotNull(Skins.downloadable("https://textures.minecraft.net/texture/abc123"));
        assertNull(Skins.downloadable("https://example.com/texture/abc123"));
        assertNull(Skins.downloadable("https://textures.minecraft.net.example.com/x"));
        assertNull(Skins.downloadable("file:///etc/passwd"));
        assertNull(Skins.downloadable("ftp://textures.minecraft.net/x"));
        assertNull(Skins.downloadable("not a url"));
        assertNull(Skins.downloadable(null));
    }

    @Test
    void skinsAre64By64Or64By32() {
        assertTrue(Skins.isSkin(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)));
        assertTrue(Skins.isSkin(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)));
        assertFalse(Skins.isSkin(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)));
        assertFalse(Skins.isSkin(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)));
        assertFalse(Skins.isSkin(null));
    }

    @Test
    void anOpaqueHatOnAnOld64By32SkinIsIgnoredAsTheGameDoes() {
        BufferedImage legacy = filled(64, 32, 0xFF112233);
        BufferedImage shown = Skins.asShownInGame(legacy);
        assertEquals(0, shown.getRGB(41, 9) >>> 24, "hat cleared");
        assertEquals(0xFF112233, shown.getRGB(9, 9), "face kept");
        assertEquals(0xFF112233, legacy.getRGB(41, 9), "the original is not changed");
    }

    @Test
    void aHatWithTransparencyOrA64By64SkinIsKept() {
        BufferedImage legacy = filled(64, 32, 0xFF112233);
        legacy.setRGB(63, 15, 0);
        assertSame(legacy, Skins.asShownInGame(legacy));
        BufferedImage modern = filled(64, 64, 0xFF112233);
        assertSame(modern, Skins.asShownInGame(modern));
    }

    private static BufferedImage filled(int w, int h, int argb) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    @Test
    void defaultSkinFollowsTheClientsChoiceByUuid() {
        // The client picks DefaultPlayerSkin's entry floorMod(uuid.hashCode(), 18): slim first, then wide.
        UUID zero = new UUID(0, 0); // hashCode 0
        assertEquals("assets/minecraft/textures/entity/player/slim/alex.png", Skins.defaultSkinPath(zero));
        UUID nine = new UUID(0, 9); // hashCode 9
        assertEquals("assets/minecraft/textures/entity/player/wide/alex.png", Skins.defaultSkinPath(nine));
        UUID negative = new UUID(0, 0xFFFFFFFFL); // hashCode -1: the last one, not a negative index
        assertEquals("assets/minecraft/textures/entity/player/wide/zuri.png", Skins.defaultSkinPath(negative));
    }
}
