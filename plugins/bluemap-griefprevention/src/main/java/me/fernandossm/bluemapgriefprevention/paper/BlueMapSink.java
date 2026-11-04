package me.fernandossm.bluemapgriefprevention.paper;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import me.fernandossm.bluemapgriefprevention.core.MarkerSpec;
import me.fernandossm.bluemapgriefprevention.core.Outline;
import me.fernandossm.bluemapgriefprevention.core.Settings;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * BlueMap's maps. Our markers live in one marker set per map. BlueMap forgets marker sets added through
 * its API when it reloads, so on every (re)load everything is put again; in between, only markers whose
 * spec changed are replaced.
 */
public final class BlueMapSink implements MapSink {

    public static final String MARKER_SET = "griefprevention-claims";

    private final Function<String, @Nullable World> worlds;
    private volatile @Nullable BlueMapAPI api;
    /** Per map id, what our marker set holds now. */
    private final Map<String, Map<String, MarkerSpec>> shown = new HashMap<>();

    /** @param worlds Bukkit worlds by name (Bukkit::getWorld) */
    public BlueMapSink(Function<String, @Nullable World> worlds) {
        this.worlds = worlds;
    }

    /** BlueMap has (re)loaded: its maps are new and hold none of our markers. */
    public void enabled(BlueMapAPI api) {
        this.api = api;
        shown.clear();
    }

    public void disabled() {
        this.api = null;
        shown.clear();
    }

    @Override
    public boolean ready() {
        return api != null;
    }

    @Override
    public void show(Map<String, Map<String, MarkerSpec>> markersByWorld, Settings settings) {
        BlueMapAPI api = this.api;
        if (api == null) {
            return;
        }
        for (BlueMapMap map : api.getMaps()) {
            Map<String, MarkerSpec> wanted = Map.of();
            for (Map.Entry<String, Map<String, MarkerSpec>> e : markersByWorld.entrySet()) {
                World world = worlds.apply(e.getKey());
                if (world != null && api.getWorld(world).map(w -> w.getId().equals(map.getWorld().getId())).orElse(false)) {
                    wanted = e.getValue();
                }
            }
            apply(map, wanted, settings);
        }
    }

    private void apply(BlueMapMap map, Map<String, MarkerSpec> wanted, Settings settings) {
        MarkerSet set = map.getMarkerSets().computeIfAbsent(MARKER_SET, id -> new MarkerSet(settings.layerLabel()));
        set.setLabel(settings.layerLabel());
        set.setToggleable(true);
        set.setDefaultHidden(settings.hiddenByDefault());
        Map<String, MarkerSpec> before = shown.getOrDefault(map.getId(), Map.of());
        for (String id : before.keySet()) {
            if (!wanted.containsKey(id)) {
                set.remove(id);
            }
        }
        wanted.forEach((id, spec) -> {
            if (!spec.equals(before.get(id)) || set.get(id) == null) {
                set.put(id, marker(spec));
            }
        });
        shown.put(map.getId(), Map.copyOf(wanted));
    }

    static ShapeMarker marker(MarkerSpec spec) {
        Outline outline = spec.outline();
        return ShapeMarker.builder()
                .label(spec.label())
                .detail(spec.detail())
                .shape(shape(outline.outer()), spec.y())
                .holes(outline.holes().stream().map(BlueMapSink::shape).toArray(Shape[]::new))
                .centerPosition()
                .depthTestEnabled(false)
                .lineWidth(spec.lineWidth())
                .lineColor(new Color(spec.color(), spec.lineOpacity()))
                .fillColor(new Color(spec.color(), spec.fillOpacity()))
                .build();
    }

    private static Shape shape(List<Outline.Point> ring) {
        return new Shape(ring.stream().map(p -> new Vector2d(p.x(), p.z())).toList());
    }
}
