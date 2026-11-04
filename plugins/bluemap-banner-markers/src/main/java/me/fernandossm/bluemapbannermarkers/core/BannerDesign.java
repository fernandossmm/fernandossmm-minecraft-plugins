package me.fernandossm.bluemapbannermarkers.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * What a banner looks like: its base colour and its pattern layers, bottom first. Colours are the dyes'
 * texture colours (DyeColor.getColor()), which is what the game tints banner textures with.
 *
 * @param baseColor the base dye's colour, 0xRRGGBB
 * @param layers    each pattern's texture (asset id such as "minecraft:creeper") and dye colour
 */
public record BannerDesign(int baseColor, List<Layer> layers) {

    public record Layer(String asset, int color) {}

    public BannerDesign {
        layers = List.copyOf(layers);
    }

    /** Short and stable: the same design always has the same key, so its image is written once. */
    public String key() {
        StringBuilder text = new StringBuilder(Integer.toHexString(baseColor));
        for (Layer layer : layers) {
            text.append(';').append(layer.asset()).append('=').append(Integer.toHexString(layer.color()));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
