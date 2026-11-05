package me.fernandossm.shinyshulkers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import me.fernandossm.endcityshulkerrespawn.EndCityShulkerRespawn;
import me.fernandossm.endcityshulkerrespawn.api.CityShulkerRespawnEvent;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** With End City Shulker Respawn installed: each shulker it brings back is rolled anew. */
class RespawnRollsTest {

    private ServerMock server;
    private WorldMock end;
    private ShinyShulkers plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = new WorldMock();
        end.setName("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        server.addWorld(end);
        MockBukkit.load(EndCityShulkerRespawn.class);
        plugin = MockBukkit.load(ShinyShulkers.class);
        plugin.useForTests(new SplittableRandom(3), chunk -> List.of());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private DyeColor respawn() {
        CityShulkerRespawnEvent event = new CityShulkerRespawnEvent(new Location(end, 4.5, 70, 4.5), "0,60,0", 0);
        server.getPluginManager().callEvent(event);
        return event.getColor();
    }

    private void config(String key, Object value) {
        plugin.getConfig().set(key, value);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    @Test
    void eachRespawnIsRolled() {
        config("one-in", 1);
        config("colors", List.of("red", "lime"));
        for (int i = 0; i < 20; i++) {
            DyeColor color = respawn();
            assertTrue(color == DyeColor.RED || color == DyeColor.LIME, String.valueOf(color));
        }
    }

    @Test
    void mostRespawnsArePurpleAtTheDefaultChance() {
        int shiny = 0;
        for (int i = 0; i < 2000; i++) {
            if (respawn() != null) {
                shiny++;
            }
        }
        assertTrue(shiny > 15 && shiny < 70, "about 40 of 2000 expected, got " + shiny);
    }

    @Test
    void otherWorldsAreLeftAlone() {
        config("one-in", 1);
        config("worlds", List.of("another_end"));
        assertNull(respawn());
    }

    @Test
    void aColourChosenByAnotherPluginIsKept() {
        config("one-in", 1);
        config("colors", List.of("red"));
        CityShulkerRespawnEvent event = new CityShulkerRespawnEvent(new Location(end, 4.5, 70, 4.5), "0,60,0", 0);
        event.setColor(DyeColor.BLACK);
        server.getPluginManager().callEvent(event);
        assertEquals(DyeColor.BLACK, event.getColor());
    }
}
