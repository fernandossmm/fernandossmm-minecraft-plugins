package me.fernandossm.bluemapbannermarkers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bluecolored.bluemap.api.AssetStorage;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.RenderManager;
import de.bluecolored.bluemap.api.WebApp;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.plugin.Plugin;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import me.fernandossm.bluemapbannermarkers.core.BannerDesign;
import me.fernandossm.bluemapbannermarkers.core.BannerImage;
import me.fernandossm.bluemapbannermarkers.core.Settings;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlueMapSinkTest {

    private static final UUID ALICE = UUID.fromString("10920508-d5d8-3eed-93d2-92f193afe7d7");
    private static final BannerDesign RED = new BannerDesign(0xB02E26, List.of());

    /** A map's asset storage, in memory; counts writes. */
    private static final class Assets implements AssetStorage {
        final Map<String, byte[]> files = new HashMap<>();
        int writes;

        @Override
        public OutputStream writeAsset(String name) {
            writes++;
            return new ByteArrayOutputStream() {
                @Override
                public void close() {
                    files.put(name, toByteArray());
                }
            };
        }

        @Override
        public Optional<InputStream> readAsset(String name) {
            return Optional.ofNullable(files.get(name)).map(ByteArrayInputStream::new);
        }

        @Override
        public boolean assetExists(String name) {
            return files.containsKey(name);
        }

        @Override
        public String getAssetUrl(String name) {
            return "maps/test/assets/" + name;
        }

        @Override
        public void deleteAsset(String name) {
            files.remove(name);
        }

        BufferedImage image(String name) throws Exception {
            return ImageIO.read(new ByteArrayInputStream(files.get(name)));
        }
    }

    private static final class FakeWorld {
        final World bukkit;
        final BlueMapWorld blueMap;
        final List<BlueMapMap> maps;
        final Map<String, Assets> assets = new HashMap<>();

        FakeWorld(String name, String... mapIds) {
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
                        case "getMaps" -> maps();
                        default -> null;
                    });
            this.maps = Arrays.stream(mapIds).map(this::map).toList();
        }

        private List<BlueMapMap> maps() {
            return maps;
        }

        private BlueMapMap map(String mapId) {
            Map<String, MarkerSet> sets = new HashMap<>();
            Assets storage = new Assets();
            assets.put(mapId, storage);
            return (BlueMapMap) Proxy.newProxyInstance(BlueMapMap.class.getClassLoader(), new Class<?>[] {BlueMapMap.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "getId" -> mapId;
                        case "getWorld" -> blueMap;
                        case "getMarkerSets" -> sets;
                        case "getAssetStorage" -> storage;
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

    private final Map<String, BufferedImage> textures = new HashMap<>();
    private boolean texturesAvailable = true;
    private @Nullable BufferedImage face;
    private @Nullable String faceVersion;
    private FakeWorld overworld;
    private FakeWorld nether;
    private BlueMapSink sink;

    @BeforeEach
    void setUp() {
        BufferedImage base = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                base.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        textures.put(BannerImage.texturePath("minecraft:base"), base);
        overworld = new FakeWorld("world", "world", "world_caves");
        nether = new FakeWorld("world_nether", "nether");
        sink = new BlueMapSink(name -> name.equals("world") ? overworld.bukkit : name.equals("world_nether") ? nether.bukkit : null,
                textures::get, () -> texturesAvailable, new BlueMapSink.FaceSource() {
                    @Override
                    public @Nullable BufferedImage face(UUID owner) {
                        return face;
                    }

                    @Override
                    public @Nullable String version(UUID owner) {
                        return faceVersion;
                    }
                }, Logger.getAnonymousLogger());
        sink.enabled(api(overworld, nether));
    }

    private static Waypoint home(int scale) {
        return new Waypoint(7, "Home <3", ALICE, "Alice", "world", 10, 64, -20, RED, scale);
    }

    @Test
    void aWaypointIsTheBannerOnEveryMapOfItsWorld() throws Exception {
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        for (int map = 0; map < 2; map++) {
            MarkerSet set = overworld.set(map);
            assertEquals("Waypoints", set.getLabel());
            assertFalse(set.isDefaultHidden());
            POIMarker marker = (POIMarker) set.get("waypoint-7");
            assertEquals("Home &lt;3", marker.getLabel(), "BlueMap's builder escapes labels: names cannot inject HTML");
            assertEquals(10.5, marker.getPosition().getX());
            assertEquals(64, marker.getPosition().getY());
            assertEquals(-19.5, marker.getPosition().getZ());
            String icon = BlueMapSink.ASSETS + "banner-" + RED.key() + "-1x.png";
            assertEquals("maps/test/assets/" + icon, marker.getIconAddress());
            assertEquals(10, marker.getAnchor().getX(), "the icon's foot is on the banner");
            assertEquals(40, marker.getAnchor().getY());
            BufferedImage image = overworld.assets.get(overworld.maps.get(map).getId()).image(icon);
            assertEquals(20, image.getWidth());
            assertEquals(0xFFB02E26, image.getRGB(5, 5));
        }
        assertNull(nether.set(0) == null ? null : nether.set(0).get("waypoint-7"));
    }

    @Test
    void enlargedWaypointsHaveADoubleIcon() throws Exception {
        sink.show(List.of(home(2)), Settings.DEFAULTS);
        POIMarker marker = (POIMarker) overworld.set(0).get("waypoint-7");
        assertEquals(20, marker.getAnchor().getX());
        assertEquals(80, marker.getAnchor().getY());
        assertEquals(40, overworld.assets.get("world").image(BlueMapSink.ASSETS + "banner-" + RED.key() + "-2x.png").getWidth());
    }

    @Test
    void thePopupNamesTheOwnerWithTheirFaceOnceThereIsOne() throws Exception {
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        POIMarker before = (POIMarker) overworld.set(0).get("waypoint-7");
        assertEquals("<div style=\"max-width:16em;overflow-wrap:anywhere\"><b>Home &lt;3</b><br>Placed by Alice</div>",
                before.getDetail());

        face = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        faceVersion = "abcd1234";
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        POIMarker after = (POIMarker) overworld.set(0).get("waypoint-7");
        assertNotSame(before, after, "a new face replaces the marker");
        String faceAsset = BlueMapSink.ASSETS + "face-" + ALICE + "-abcd1234.png";
        assertTrue(after.getDetail().contains("<img src=\"maps/test/assets/" + faceAsset + "\""), after.getDetail());
        assertTrue(after.getDetail().contains("Placed by Alice"));
        assertTrue(overworld.assets.get("world").assetExists(faceAsset));
    }

    @Test
    void thePopupLineIsTranslatableAndATranslationRedrawsIt() {
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        YamlConfiguration c = new YamlConfiguration();
        c.set("messages.placed-by", "Colocado por <owner> <i>");
        sink.show(List.of(home(1)), Settings.from(c, Logger.getAnonymousLogger()));
        assertEquals("<div style=\"max-width:16em;overflow-wrap:anywhere\"><b>Home &lt;3</b><br>Colocado por Alice &lt;i&gt;</div>",
                ((POIMarker) overworld.set(0).get("waypoint-7")).getDetail(), "a changed text replaces the marker; the template is escaped too");
    }

    @Test
    void unchangedWaypointsAreLeftAloneAndImagesWrittenOnce() {
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        POIMarker first = (POIMarker) overworld.set(0).get("waypoint-7");
        int writes = overworld.assets.get("world").writes;
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        assertSame(first, overworld.set(0).get("waypoint-7"));
        assertEquals(writes, overworld.assets.get("world").writes);
    }

    @Test
    void renamedWaypointsAreReplacedAndRemovedOnesDisappear() {
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        sink.show(List.of(home(1).withName("Base")), Settings.DEFAULTS);
        assertEquals("Base", overworld.set(0).get("waypoint-7").getLabel());
        sink.show(List.of(), Settings.DEFAULTS);
        assertNull(overworld.set(0).get("waypoint-7"));
    }

    @Test
    void withoutTheClientTexturesTheDefaultIconIsUsed() {
        texturesAvailable = false;
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        POIMarker marker = (POIMarker) overworld.set(0).get("waypoint-7");
        assertTrue(marker.getIconAddress().contains("poi"), marker.getIconAddress());
        assertTrue(overworld.assets.get("world").files.isEmpty());
    }

    @Test
    void afterABlueMapReloadEverythingIsPutAgain() {
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        FakeWorld fresh = new FakeWorld("world", "world");
        overworld = fresh;
        sink.enabled(api(fresh));
        sink.show(List.of(home(1)), Settings.DEFAULTS);
        assertTrue(fresh.set(0).get("waypoint-7") instanceof POIMarker);
    }
}
