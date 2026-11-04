package me.fernandossm.shinyshulkers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class PluginTest {

    private ServerMock server;
    private WorldMock end;
    private ShinyShulkers plugin;
    /** The shulkers the fake structure lookup reports as standing in a city. */
    private final List<Shulker> inCity = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = new WorldMock();
        end.setName("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        server.addWorld(end);
        plugin = MockBukkit.load(ShinyShulkers.class);
        plugin.useForTests(new SplittableRandom(3), chunk -> List.copyOf(inCity));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Shulker cityShulker() {
        Shulker s = end.spawn(new Location(end, 4.5, 70, 4.5), Shulker.class);
        inCity.add(s);
        return s;
    }

    private void oneIn(int n) {
        plugin.getConfig().set("one-in", n);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void generate(WorldMock world, boolean newChunk) {
        server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(0, 0), newChunk));
    }

    @Test
    void aNewlyGeneratedCitysShulkersAreRolled() {
        oneIn(1);
        Shulker a = cityShulker();
        Shulker b = cityShulker();
        generate(end, true);

        assertTrue(a.getColor() != null && b.getColor() != null);
    }

    @Test
    void mostAreNotShinyAtTheDefaultChance() {
        for (int i = 0; i < 2000; i++) {
            cityShulker();
        }
        generate(end, true);
        long shiny = inCity.stream().filter(s -> s.getColor() != null).count();
        assertTrue(shiny > 15 && shiny < 70, "about 40 of 2000 expected, got " + shiny);
    }

    @Test
    void chunksLoadedAgainAreNotRolled() {
        oneIn(1);
        Shulker s = cityShulker();
        generate(end, false);
        assertNull(s.getColor(), "only the city's first, generated spawn is rolled");
    }

    @Test
    void alreadyColouredShulkersKeepTheirColour() {
        oneIn(1);
        Shulker s = cityShulker();
        s.setColor(DyeColor.BLACK);
        plugin.getConfig().set("colors", List.of("white"));
        plugin.saveConfig();
        plugin.reloadSettings();
        generate(end, true);
        assertEquals(DyeColor.BLACK, s.getColor());
    }

    @Test
    void onlyConfiguredColoursAreUsed() {
        oneIn(1);
        plugin.getConfig().set("colors", List.of("red", "lime"));
        plugin.saveConfig();
        plugin.reloadSettings();
        for (int i = 0; i < 50; i++) {
            cityShulker();
        }
        generate(end, true);
        assertTrue(inCity.stream().allMatch(s -> s.getColor() == DyeColor.RED || s.getColor() == DyeColor.LIME));
    }

    @Test
    void otherWorldsAreIgnored() {
        oneIn(1);
        Shulker s = cityShulker();
        WorldMock overworld = server.addSimpleWorld("world");
        generate(overworld, true);
        assertNull(s.getColor(), "not an End world");

        plugin.getConfig().set("worlds", List.of("another_end"));
        plugin.saveConfig();
        plugin.reloadSettings();
        generate(end, true);
        assertNull(s.getColor(), "not a managed End world");
    }
}
