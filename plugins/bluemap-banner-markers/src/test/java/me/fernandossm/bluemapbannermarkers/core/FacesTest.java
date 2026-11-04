package me.fernandossm.bluemapbannermarkers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.image.BufferedImage;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FacesTest {

    private static final int SKIN = 0xFFC08060;
    private static final int HAT = 0xFF2040A0;

    private static BufferedImage skin(int height) {
        BufferedImage skin = new BufferedImage(64, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, SKIN);
            }
        }
        return skin;
    }

    private static int at(BufferedImage face, int x, int y) {
        return face.getRGB(x * Faces.SCALE, y * Faces.SCALE);
    }

    @Test
    void theFaceIsTheFrontOfTheHeadScaledUp() {
        BufferedImage face = Faces.face(skin(64));
        assertEquals(8 * Faces.SCALE, face.getWidth());
        assertEquals(SKIN, at(face, 0, 0));
        assertEquals(SKIN, face.getRGB(8 * Faces.SCALE - 1, 8 * Faces.SCALE - 1));
    }

    @Test
    void theHatLayerIsDrawnOverTheFaceWhereItHasPixels() {
        BufferedImage skin = skin(64);
        for (int x = 40; x < 48; x++) {
            skin.setRGB(x, 8, HAT); // a brim along the top row only
        }
        BufferedImage face = Faces.face(skin);
        assertEquals(HAT, at(face, 3, 0));
        assertEquals(SKIN, at(face, 3, 1));
    }

    @Test
    void theFaceIsAlwaysOpaqueLikeInGame() {
        BufferedImage skin = skin(64);
        skin.setRGB(9, 9, 0x00123456);
        assertEquals(0xFF123456, at(Faces.face(skin), 1, 1));
    }

    @Test
    void anOldSkinWithAnOpaqueHatLayerShowsNoHat() {
        BufferedImage skin = skin(32);
        for (int y = 0; y < 16; y++) {
            for (int x = 32; x < 64; x++) {
                skin.setRGB(x, y, 0xFF000000);
            }
        }
        assertEquals(SKIN, at(Faces.face(skin), 2, 2), "the game ignores a fully opaque hat on 64x32 skins");
        skin.setRGB(63, 15, 0x00000000);
        assertEquals(0xFF000000, at(Faces.face(skin), 2, 2), "with any transparency it is a real hat");
    }

    @Test
    void anythingButASkinHasNoFace() {
        assertNull(Faces.face(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)));
        assertNull(Faces.face(new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB)));
    }

    /** DefaultPlayerSkin.get(UUID): floorMod(uuid.hashCode(), 18) over the slim skins, then the wide ones. */
    @Test
    void defaultSkinsAreChosenAsTheClientChoosesThem() {
        UUID zero = new UUID(0, 0); // hashCode 0
        assertEquals("assets/minecraft/textures/entity/player/slim/alex.png", Faces.defaultSkinPath(zero));
        UUID nine = new UUID(0, 9); // hashCode 9
        assertEquals("assets/minecraft/textures/entity/player/wide/alex.png", Faces.defaultSkinPath(nine));
        UUID minusOne = new UUID(0, 0xFFFFFFFFL); // hashCode -1: floorMod gives 17
        assertEquals("assets/minecraft/textures/entity/player/wide/zuri.png", Faces.defaultSkinPath(minusOne));
    }
}
