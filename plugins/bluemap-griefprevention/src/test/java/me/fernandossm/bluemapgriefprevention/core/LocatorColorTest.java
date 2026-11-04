package me.fernandossm.bluemapgriefprevention.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocatorColorTest {

    /**
     * locator-colors.txt holds colours computed by Minecraft 26.2's own code (net.minecraft.util.ARGB, as the
     * locator bar calls it) for 505 UUIDs. The port must give the same colour for every one.
     */
    @Test
    void matchesMinecraftsLocatorBarColours() throws Exception {
        int checked = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/locator-colors.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(" ");
                int argb = Integer.parseUnsignedInt(parts[1], 16);
                assertEquals(argb & 0xFFFFFF, LocatorColor.of(UUID.fromString(parts[0])), parts[0]);
                checked++;
            }
        }
        assertEquals(505, checked);
    }

    @Test
    void greyIsJustDimmed() {
        assertEquals(0xE6E6E6, LocatorColor.setBrightness(0xFFFFFF, 0.9f));
        assertEquals(0xE6E6E6, LocatorColor.setBrightness(0x000000, 0.9f));
    }
}
