package me.fernandossm.endcityshulkerrespawn.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.fernandossm.endcityshulkerrespawn.EndCityShulkerRespawn;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.core.Spot;
import me.fernandossm.endcityshulkerrespawn.core.Spot.Face;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class CityCodecTest {

    private ServerMock server;
    private EndCityShulkerRespawn plugin;
    private PersistentDataContainer pdc;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(EndCityShulkerRespawn.class);
        pdc = server.addPlayer().getPersistentDataContainer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cityDataSurvivesARoundTrip() {
        CityCodec codec = new CityCodec(plugin);
        CityData data = new CityData(List.of(
                new Spot(-100, 80, 2000, Face.DOWN, Spot.NO_COLOR, Spot.ALIVE),
                new Spot(5, 64, -7, Face.EAST, 14, 1_234_567_890_123L)),
                Set.of(CityData.chunkKey(-7, 125), CityData.chunkKey(0, -1)), 3);
        codec.write(pdc, data);
        CityData back = codec.read(pdc);

        assertEquals(data.spots(), back.spots());
        assertEquals(data.learnedChunks(), back.learnedChunks());
        assertEquals(3, back.generation());
    }

    @Test
    void nothingStoredReadsAsEmpty() {
        CityData data = new CityCodec(plugin).read(pdc);
        assertTrue(data.spots().isEmpty());
        assertTrue(data.learnedChunks().isEmpty());
    }

    @Test
    void aNewerFormatIsNotMisread() {
        pdc.set(new NamespacedKey(plugin, "format"), PersistentDataType.INTEGER, 99);
        assertTrue(new CityCodec(plugin).read(pdc).spots().isEmpty());
    }

    @Test
    void shulkerTagsRoundTrip() {
        ShulkerTag.Keys keys = new ShulkerTag.Keys(plugin);
        var shulker = server.addPlayer(); // any entity has a container
        keys.write(shulker, new ShulkerTag("1,2,3", 4, 5));
        assertEquals(Optional.of(new ShulkerTag("1,2,3", 4, 5)), keys.read(shulker));
        assertEquals(Optional.empty(), keys.read(server.addPlayer()));
    }
}
