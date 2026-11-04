package me.fernandossm.bluemapgriefprevention.core;

import java.util.UUID;

/**
 * A player's colour on the locator bar, as Minecraft computes it (26.2, {@code LocatorBar}):
 * {@code ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9f)}. A faithful port of {@code ARGB}'s
 * float arithmetic, so the result is identical; the tests hold it to vectors computed by Minecraft itself.
 */
public final class LocatorColor {

    private static final float BRIGHTNESS = 0.9f;

    private LocatorColor() {}

    /** The colour as 0xRRGGBB. */
    public static int of(UUID player) {
        return setBrightness(player.hashCode() & 0xFFFFFF, BRIGHTNESS);
    }

    /** {@code ARGB.setBrightness} without the alpha channel: through HSB and back with a new brightness. */
    static int setBrightness(int rgb, float brightness) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int max = Math.max(Math.max(red, green), blue);
        int min = Math.min(Math.min(red, green), blue);
        float range = max - min;
        float saturation = max != 0 ? range / max : 0f;
        float hue;
        if (saturation == 0f) {
            hue = 0f;
        } else {
            float r = (max - red) / range;
            float g = (max - green) / range;
            float b = (max - blue) / range;
            if (red == max) {
                hue = b - g;
            } else if (green == max) {
                hue = 2f + r - b;
            } else {
                hue = 4f + g - r;
            }
            hue /= 6f;
            if (hue < 0f) {
                hue += 1f;
            }
        }
        if (saturation == 0f) {
            int v = Math.round(brightness * 255f);
            return rgb(v, v, v);
        }
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float f = h - (float) Math.floor(h);
        float p = brightness * (1f - saturation);
        float q = brightness * (1f - saturation * f);
        float t = brightness * (1f - saturation * (1f - f));
        return switch ((int) h) {
            case 0 -> rgb(Math.round(brightness * 255f), Math.round(t * 255f), Math.round(p * 255f));
            case 1 -> rgb(Math.round(q * 255f), Math.round(brightness * 255f), Math.round(p * 255f));
            case 2 -> rgb(Math.round(p * 255f), Math.round(brightness * 255f), Math.round(t * 255f));
            case 3 -> rgb(Math.round(p * 255f), Math.round(q * 255f), Math.round(brightness * 255f));
            case 4 -> rgb(Math.round(t * 255f), Math.round(p * 255f), Math.round(brightness * 255f));
            case 5 -> rgb(Math.round(brightness * 255f), Math.round(p * 255f), Math.round(q * 255f));
            default -> rgb(red, green, blue);
        };
    }

    private static int rgb(int r, int g, int b) {
        return (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
    }
}
