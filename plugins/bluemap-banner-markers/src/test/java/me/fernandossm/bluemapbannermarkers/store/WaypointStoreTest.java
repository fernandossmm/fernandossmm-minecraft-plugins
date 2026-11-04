package me.fernandossm.bluemapbannermarkers.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import me.fernandossm.bluemapbannermarkers.core.BannerDesign;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointStoreTest {

    private static final UUID ALICE = UUID.fromString("10920508-d5d8-3eed-93d2-92f193afe7d7");
    private static final BannerDesign DESIGN = new BannerDesign(0xF9FFFE, List.of(
            new BannerDesign.Layer("minecraft:creeper", 0x1D1D21), new BannerDesign.Layer("minecraft:border", 0xB02E26)));

    @TempDir
    Path dir;

    private WaypointStore store() {
        WaypointStore store = new WaypointStore(dir.resolve("waypoints.json"), Logger.getAnonymousLogger());
        store.load();
        return store;
    }

    @Test
    void waypointsSurviveARestart() {
        WaypointStore store = store();
        Waypoint home = store.add("Home", ALICE, "Alice", "world", 10, 64, -20, DESIGN);
        Waypoint farm = store.add("Farm", ALICE, "Alice", "world_nether", 1, 2, 3, DESIGN);
        store.update(farm.withScale(2));
        store.skin(ALICE, "http://textures.minecraft.net/texture/abc");

        WaypointStore again = store();
        assertEquals(List.of(home, farm.withScale(2)), again.all());
        assertEquals("http://textures.minecraft.net/texture/abc", again.skin(ALICE));
        assertEquals(home, again.at("world", 10, 64, -20).orElseThrow());
        assertEquals(List.of(home), again.inChunk("world", 0, -2));
    }

    @Test
    void idsAreNeverReused() {
        WaypointStore store = store();
        store.add("A", ALICE, "Alice", "world", 0, 0, 0, DESIGN);
        Waypoint b = store.add("B", ALICE, "Alice", "world", 1, 0, 0, DESIGN);
        store.remove(b.id());
        assertEquals(3, store().add("C", ALICE, "Alice", "world", 2, 0, 0, DESIGN).id());
    }

    @Test
    void removingTwiceIsHarmless() {
        WaypointStore store = store();
        Waypoint a = store.add("A", ALICE, "Alice", "world", 0, 0, 0, DESIGN);
        assertEquals(a, store.remove(a.id()));
        assertNull(store.remove(a.id()));
    }

    @Test
    void anUnreadableFileIsKeptAside() throws Exception {
        Files.writeString(dir.resolve("waypoints.json"), "{ not json");
        WaypointStore store = store();
        assertTrue(store.all().isEmpty());
        assertEquals("{ not json", Files.readString(dir.resolve("waypoints.json.unreadable")));
    }
}
