package me.fernandossm.bluemapgriefprevention.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.RenderManager;
import de.bluecolored.bluemap.api.WebApp;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.plugin.Plugin;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.fernandossm.bluemapgriefprevention.core.ClaimView;
import me.fernandossm.bluemapgriefprevention.core.Markers;
import me.fernandossm.bluemapgriefprevention.core.MarkerSpec;
import me.fernandossm.bluemapgriefprevention.core.Rect;
import me.fernandossm.bluemapgriefprevention.core.Settings;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlueMapSinkTest {

    private static final UUID ALICE = UUID.fromString("10920508-d5d8-3eed-93d2-92f193afe7d7");

    /** A BlueMap world with its maps: only what the sink uses. */
    private static final class FakeWorld {
        final String id;
        final World bukkit;
        final BlueMapWorld blueMap;
        final List<BlueMapMap> maps;

        FakeWorld(String name, String... mapIds) {
            this.id = name;
            this.bukkit = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getName" -> name;
                        case "hashCode" -> name.hashCode();
                        case "equals" -> p == a[0];
                        default -> null;
                    });
            this.blueMap = (BlueMapWorld) Proxy.newProxyInstance(BlueMapWorld.class.getClassLoader(), new Class<?>[] {BlueMapWorld.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getId" -> name;
                        case "getMaps" -> this.maps();
                        default -> null;
                    });
            this.maps = Arrays.stream(mapIds).map(this::map).toList();
        }

        private List<BlueMapMap> maps() {
            return maps;
        }

        private BlueMapMap map(String mapId) {
            Map<String, MarkerSet> sets = new HashMap<>();
            return (BlueMapMap) Proxy.newProxyInstance(BlueMapMap.class.getClassLoader(), new Class<?>[] {BlueMapMap.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getId" -> mapId;
                        case "getWorld" -> blueMap;
                        case "getMarkerSets" -> sets;
                        default -> null;
                    });
        }

        MarkerSet set(int map) {
            return maps.get(map).getMarkerSets().get(BlueMapSink.MARKER_SET);
        }
    }

    private static BlueMapAPI api(FakeWorld... worlds) {
        return new BlueMapAPI() {
            @Override public RenderManager getRenderManager() { return null; }
            @Override public WebApp getWebApp() { return null; }
            @Override public Plugin getPlugin() { return null; }
            @Override public Collection<BlueMapMap> getMaps() {
                return Arrays.stream(worlds).flatMap(w -> w.maps.stream()).toList();
            }
            @Override public Collection<BlueMapWorld> getWorlds() {
                return Arrays.stream(worlds).map(w -> w.blueMap).toList();
            }
            @Override public Optional<BlueMapWorld> getWorld(Object world) {
                return Arrays.stream(worlds).filter(w -> w.bukkit == world).map(w -> w.blueMap).findFirst();
            }
            @Override public Optional<BlueMapMap> getMap(String id) { return Optional.empty(); }
            @Override public String getBlueMapVersion() { return "test"; }
        };
    }

    private FakeWorld overworld;
    private FakeWorld nether;
    private BlueMapSink sink;

    @BeforeEach
    void setUp() {
        overworld = new FakeWorld("world", "world", "world_flat");
        nether = new FakeWorld("world_nether", "nether");
        sink = new BlueMapSink(name -> name.equals("world") ? overworld.bukkit : name.equals("world_nether") ? nether.bukkit : null);
    }

    private static Map<String, Map<String, MarkerSpec>> markers(ClaimView... claims) {
        return Markers.of(List.of(claims), Settings.DEFAULTS);
    }

    private static ClaimView aliceAt(long id, int x) {
        return new ClaimView(id, "world", new Rect(x, 0, x + 9, 9), 64, ALICE, "Alice", false);
    }

    @Test
    void nothingIsShownBeforeBlueMapIsUp() {
        assertFalse(sink.ready());
        sink.show(markers(aliceAt(1, 0)), Settings.DEFAULTS);
        assertTrue(overworld.maps.getFirst().getMarkerSets().isEmpty());
    }

    @Test
    void everyMapOfTheWorldGetsTheClaimsLayer() {
        sink.enabled(api(overworld, nether));
        sink.show(markers(aliceAt(1, 0)), Settings.DEFAULTS);

        for (int map = 0; map < 2; map++) {
            MarkerSet set = overworld.set(map);
            assertEquals("Claims", set.getLabel());
            assertTrue(set.isToggleable());
            assertFalse(set.isDefaultHidden());
            assertEquals(1, set.getMarkers().size());
        }
        assertTrue(nether.set(0).getMarkers().isEmpty(), "the layer exists but has no claims there");
    }

    @Test
    void markersAreFlatShapesSeenThroughTheTerrain() {
        sink.enabled(api(overworld));
        List<ClaimView> ring = List.of(
                new ClaimView(1, "world", new Rect(0, 0, 29, 9), 64, ALICE, "Alice", false),
                new ClaimView(2, "world", new Rect(0, 20, 29, 29), 64, ALICE, "Alice", false),
                new ClaimView(3, "world", new Rect(0, 10, 9, 19), 64, ALICE, "Alice", false),
                new ClaimView(4, "world", new Rect(20, 10, 29, 19), 64, ALICE, "Alice", false));
        sink.show(Markers.of(ring, Settings.DEFAULTS), Settings.DEFAULTS);

        ShapeMarker m = (ShapeMarker) overworld.set(0).get("claim-" + ALICE + "-1");
        assertEquals("Claim", m.getLabel());
        assertEquals("<b>Claim of Alice</b><br>4 claims · 800 blocks", m.getDetail());
        assertEquals(List.of(new Vector2d(0, 0), new Vector2d(30, 0), new Vector2d(30, 30), new Vector2d(0, 30)),
                List.of(m.getShape().getPoints()));
        assertEquals(1, m.getHoles().size());
        assertEquals(64f, m.getShapeY());
        assertFalse(m.isDepthTestEnabled());
        int rgb = me.fernandossm.bluemapgriefprevention.core.LocatorColor.of(ALICE);
        assertEquals(rgb >> 16 & 0xFF, m.getLineColor().getRed());
        assertEquals(rgb >> 8 & 0xFF, m.getFillColor().getGreen());
        assertEquals(0.8f, m.getLineColor().getAlpha(), 1e-6);
        assertEquals(0.2f, m.getFillColor().getAlpha(), 1e-6);
        assertEquals(2, m.getLineWidth());
    }

    @Test
    void unchangedMarkersAreLeftAloneAndOthersReplacedOrRemoved() {
        sink.enabled(api(overworld));
        sink.show(markers(aliceAt(1, 0), aliceAt(2, 100), aliceAt(3, 200)), Settings.DEFAULTS);
        MarkerSet set = overworld.set(0);
        var unchanged = set.get("claim-" + ALICE + "-1");
        var moving = set.get("claim-" + ALICE + "-2");

        sink.show(markers(aliceAt(1, 0), aliceAt(2, 150)), Settings.DEFAULTS);

        assertSame(unchanged, set.get("claim-" + ALICE + "-1"));
        assertNotSame(moving, set.get("claim-" + ALICE + "-2"));
        assertEquals(2, set.getMarkers().size(), "claim 3 is gone");
    }

    @Test
    void aBlueMapReloadPutsEverythingAgain() {
        sink.enabled(api(overworld));
        sink.show(markers(aliceAt(1, 0)), Settings.DEFAULTS);
        FakeWorld reloaded = new FakeWorld("world", "world", "world_flat");
        sink = new BlueMapSink(name -> reloaded.bukkit);
        sink.enabled(api(reloaded));
        sink.show(markers(aliceAt(1, 0)), Settings.DEFAULTS);
        assertEquals(1, reloaded.set(0).getMarkers().size());
    }

    @Test
    void enablingAgainForgetsWhatWasShown() {
        BlueMapAPI api = api(overworld);
        sink.enabled(api);
        sink.show(markers(aliceAt(1, 0)), Settings.DEFAULTS);
        overworld.maps.getFirst().getMarkerSets().clear(); // what a BlueMap reload does to API marker sets
        sink.enabled(api);
        sink.show(markers(aliceAt(1, 0)), Settings.DEFAULTS);
        assertEquals(1, overworld.set(0).getMarkers().size());
    }

    @Test
    void theLayerFollowsTheConfig() {
        Settings d = Settings.DEFAULTS;
        Settings hidden = new Settings(d.worlds(), "Land", true, d.owner(), d.playerClaims(), d.adminClaims(),
                d.adminColor(), d.fillOpacity(), d.lineOpacity(), d.lineWidth(), d.resyncInterval());
        sink.enabled(api(overworld));
        sink.show(markers(aliceAt(1, 0)), Settings.DEFAULTS);
        sink.show(markers(aliceAt(1, 0)), hidden);
        assertEquals("Land", overworld.set(0).getLabel());
        assertTrue(overworld.set(0).isDefaultHidden());
    }

    @Test
    void disablingStopsDrawing() {
        sink.enabled(api(overworld));
        sink.disabled();
        assertFalse(sink.ready());
    }
}
