package me.fernandossm.bluemapbannermarkers.paper;

import de.bluecolored.bluemap.api.AssetStorage;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import me.fernandossm.bluemapbannermarkers.core.BannerImage;
import me.fernandossm.bluemapbannermarkers.core.Faces;
import me.fernandossm.bluemapbannermarkers.core.Names;
import me.fernandossm.bluemapbannermarkers.core.Settings;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * BlueMap's maps: one marker per waypoint on every map of its world, its icon the banner itself, its popup
 * the owner's face. Images are written into each map's own asset storage, next to its tiles, so a map
 * served as static files shows them too. BlueMap forgets marker sets added through its API when it
 * reloads, so on every (re)load everything is put again; in between, only changed markers are replaced.
 */
public final class BlueMapSink {

    public static final String MARKER_SET = "banner-waypoints";
    static final String ASSETS = "bannermarkers/";

    /** What decides a marker's look; a marker is put again only when this changes. */
    private record Shown(Waypoint waypoint, @Nullable String face, boolean icons) {}

    /** The owner's face image and which skin it shows, or null. */
    public interface FaceSource {
        @Nullable BufferedImage face(UUID owner);

        @Nullable String version(UUID owner);
    }

    private final Function<String, @Nullable World> worlds;
    private final Function<String, @Nullable BufferedImage> textures;
    private final java.util.function.BooleanSupplier texturesAvailable;
    private final FaceSource faces;
    private final Logger log;
    private volatile @Nullable BlueMapAPI api;
    /** Per map id: what our marker set holds, and which of our assets its storage already has. */
    private final Map<String, Map<String, Shown>> shown = new HashMap<>();
    private final Map<String, Set<String>> written = new HashMap<>();

    public BlueMapSink(Function<String, @Nullable World> worlds, Function<String, @Nullable BufferedImage> textures,
                       java.util.function.BooleanSupplier texturesAvailable, FaceSource faces, Logger log) {
        this.worlds = worlds;
        this.textures = textures;
        this.texturesAvailable = texturesAvailable;
        this.faces = faces;
        this.log = log;
    }

    /** BlueMap has (re)loaded: its maps are new and hold none of our markers. */
    public void enabled(BlueMapAPI api) {
        this.api = api;
        shown.clear();
        written.clear();
    }

    public void disabled() {
        this.api = null;
        shown.clear();
        written.clear();
    }

    public boolean ready() {
        return api != null;
    }

    public void show(List<Waypoint> waypoints, Settings settings) {
        BlueMapAPI api = this.api;
        if (api == null) {
            return;
        }
        boolean icons = texturesAvailable.getAsBoolean();
        for (BlueMapMap map : api.getMaps()) {
            List<Waypoint> here = waypoints.stream().filter(w -> {
                World world = worlds.apply(w.world());
                return world != null && api.getWorld(world).map(bw -> bw.getId().equals(map.getWorld().getId())).orElse(false);
            }).toList();
            apply(map, here, settings, icons);
        }
    }

    private void apply(BlueMapMap map, List<Waypoint> waypoints, Settings settings, boolean icons) {
        MarkerSet set = map.getMarkerSets().computeIfAbsent(MARKER_SET, id -> new MarkerSet(settings.layerLabel()));
        set.setLabel(settings.layerLabel());
        set.setToggleable(true);
        set.setDefaultHidden(settings.hiddenByDefault());
        Map<String, Shown> before = shown.getOrDefault(map.getId(), Map.of());
        Map<String, Shown> now = new HashMap<>();
        for (Waypoint w : waypoints) {
            String id = markerId(w);
            Shown look = new Shown(w, faces.version(w.owner()), icons);
            now.put(id, look);
            if (!look.equals(before.get(id)) || set.get(id) == null) {
                set.put(id, marker(map, look));
            }
        }
        for (String id : before.keySet()) {
            if (!now.containsKey(id)) {
                set.remove(id);
            }
        }
        shown.put(map.getId(), now);
    }

    static String markerId(Waypoint w) {
        return "waypoint-" + w.id();
    }

    private POIMarker marker(BlueMapMap map, Shown look) {
        Waypoint w = look.waypoint();
        POIMarker.Builder builder = POIMarker.builder()
                .label(w.name())
                .position(w.x() + 0.5, w.y(), w.z() + 0.5);
        String icon = look.icons() ? iconAsset(map, w) : null;
        if (icon != null) {
            builder.icon(map.getAssetStorage().getAssetUrl(icon), BannerImage.WIDTH * w.scale() / 2, BannerImage.HEIGHT * w.scale());
        } else {
            builder.defaultIcon();
        }
        String face = look.face() != null ? faceAsset(map, w.owner(), look.face()) : null;
        return builder.detail(detail(w, face == null ? null : map.getAssetStorage().getAssetUrl(face))).build();
    }

    static String detail(Waypoint w, @Nullable String faceUrl) {
        // Wrapped, even inside a word, so a long name (or one from before a lower max-name-length) stays narrow.
        String text = "<div style=\"max-width:16em;overflow-wrap:anywhere\"><b>" + Names.escape(w.name()) + "</b><br>Placed by "
                + Names.escape(w.ownerName()) + "</div>";
        if (faceUrl == null) {
            return text;
        }
        int size = 8 * Faces.SCALE;
        return "<div style=\"display:flex;align-items:center;gap:8px\"><img src=\"" + Names.escape(faceUrl) + "\" alt=\"\" width=\""
                + size + "\" height=\"" + size + "\" style=\"image-rendering:pixelated\">" + text + "</div>";
    }

    private @Nullable String iconAsset(BlueMapMap map, Waypoint w) {
        String name = ASSETS + "banner-" + w.design().key() + "-" + w.scale() + "x.png";
        return ensure(map, name, () -> BannerImage.render(w.design(), textures, w.scale())) ? name : null;
    }

    private @Nullable String faceAsset(BlueMapMap map, UUID owner, String version) {
        String name = ASSETS + "face-" + owner + "-" + version + ".png";
        return ensure(map, name, () -> faces.face(owner)) ? name : null;
    }

    /** Writes the image into the map's storage unless it is there already. */
    private boolean ensure(BlueMapMap map, String name, java.util.function.Supplier<@Nullable BufferedImage> image) {
        Set<String> done = written.computeIfAbsent(map.getId(), id -> new HashSet<>());
        if (done.contains(name)) {
            return true;
        }
        AssetStorage storage = map.getAssetStorage();
        try {
            if (!storage.assetExists(name)) {
                BufferedImage img = image.get();
                if (img == null) {
                    return false;
                }
                try (OutputStream out = storage.writeAsset(name)) {
                    ImageIO.write(img, "png", out);
                }
            }
            done.add(name);
            return true;
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not write " + name + " to BlueMap map " + map.getId(), e);
            return false;
        }
    }
}
