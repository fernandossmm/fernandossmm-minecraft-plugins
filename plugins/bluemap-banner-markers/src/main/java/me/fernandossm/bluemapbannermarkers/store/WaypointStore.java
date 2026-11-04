package me.fernandossm.bluemapbannermarkers.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.fernandossm.bluemapbannermarkers.core.BannerDesign;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import org.jspecify.annotations.Nullable;

/**
 * Every waypoint, in waypoints.json, rewritten after each change (a temporary file moved over the old one,
 * so a crash leaves either the old or the new file). Also remembers which skin each owner's saved face was
 * cut from, so it is fetched again only when that skin changes.
 */
public final class WaypointStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Logger log;
    private final Map<Integer, Waypoint> waypoints = new LinkedHashMap<>();
    private final Map<UUID, String> skins = new LinkedHashMap<>();
    private int nextId = 1;

    public WaypointStore(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** What the file holds; field names are the file format. */
    private static final class Data {
        int nextId = 1;
        List<Entry> waypoints = new ArrayList<>();
        Map<String, String> skins = new LinkedHashMap<>();
    }

    private static final class Entry {
        int id;
        String name;
        String owner;
        String ownerName;
        String world;
        int x;
        int y;
        int z;
        String baseColor;
        List<Layer> patterns = new ArrayList<>();
        int scale = 1;
    }

    private static final class Layer {
        String pattern;
        String color;
    }

    public void load() {
        waypoints.clear();
        skins.clear();
        nextId = 1;
        if (!Files.exists(file)) {
            return;
        }
        try {
            Data data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (data == null) {
                return;
            }
            for (Entry e : data.waypoints) {
                List<BannerDesign.Layer> layers = e.patterns.stream()
                        .map(l -> new BannerDesign.Layer(l.pattern, Integer.parseInt(l.color, 16))).toList();
                waypoints.put(e.id, new Waypoint(e.id, e.name, UUID.fromString(e.owner), e.ownerName, e.world, e.x, e.y, e.z,
                        new BannerDesign(Integer.parseInt(e.baseColor, 16), layers), e.scale == 2 ? 2 : 1));
            }
            data.skins.forEach((uuid, skin) -> skins.put(UUID.fromString(uuid), skin));
            nextId = Math.max(data.nextId, waypoints.keySet().stream().mapToInt(i -> i + 1).max().orElse(1));
        } catch (IOException | JsonParseException | IllegalArgumentException | NullPointerException e) {
            log.log(Level.SEVERE, "Could not read " + file + "; starting with no waypoints. The file is left as it was.", e);
            waypoints.clear();
            skins.clear();
            // Never overwrite a file we could not read: keep a copy before the next save replaces it.
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".unreadable"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copy) {
                log.log(Level.SEVERE, "Could not keep a copy of " + file, copy);
            }
        }
    }

    private void save() {
        Data data = new Data();
        data.nextId = nextId;
        for (Waypoint w : waypoints.values()) {
            Entry e = new Entry();
            e.id = w.id();
            e.name = w.name();
            e.owner = w.owner().toString();
            e.ownerName = w.ownerName();
            e.world = w.world();
            e.x = w.x();
            e.y = w.y();
            e.z = w.z();
            e.baseColor = hex(w.design().baseColor());
            for (BannerDesign.Layer l : w.design().layers()) {
                Layer layer = new Layer();
                layer.pattern = l.asset();
                layer.color = hex(l.color());
                e.patterns.add(layer);
            }
            e.scale = w.scale();
            data.waypoints.add(e);
        }
        skins.forEach((uuid, skin) -> data.skins.put(uuid.toString(), skin));
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save " + file, e);
        }
    }

    private static String hex(int rgb) {
        return String.format("%06x", rgb & 0xFFFFFF);
    }

    public List<Waypoint> all() {
        return List.copyOf(waypoints.values());
    }

    public Optional<Waypoint> byId(int id) {
        return Optional.ofNullable(waypoints.get(id));
    }

    public Optional<Waypoint> at(String world, int x, int y, int z) {
        return waypoints.values().stream().filter(w -> w.at(world, x, y, z)).findFirst();
    }

    public List<Waypoint> inChunk(String world, int chunkX, int chunkZ) {
        return waypoints.values().stream().filter(w -> w.inChunk(world, chunkX, chunkZ)).toList();
    }

    public List<Waypoint> ownedBy(UUID owner) {
        return waypoints.values().stream().filter(w -> w.owner().equals(owner))
                .sorted(Comparator.comparingInt(Waypoint::id)).toList();
    }

    /** A new waypoint with the next id. */
    public Waypoint add(String name, UUID owner, String ownerName, String world, int x, int y, int z, BannerDesign design) {
        Waypoint w = new Waypoint(nextId++, name, owner, ownerName, world, x, y, z, design, 1);
        waypoints.put(w.id(), w);
        save();
        return w;
    }

    /** Replaces the waypoint with the same id. */
    public void update(Waypoint w) {
        if (waypoints.containsKey(w.id())) {
            waypoints.put(w.id(), w);
            save();
        }
    }

    public @Nullable Waypoint remove(int id) {
        Waypoint removed = waypoints.remove(id);
        if (removed != null) {
            save();
        }
        return removed;
    }

    /** The skin the owner's saved face was cut from ("default" for the game's default skin), if any. */
    public @Nullable String skin(UUID owner) {
        return skins.get(owner);
    }

    public void skin(UUID owner, String skin) {
        if (!skin.equals(skins.put(owner, skin))) {
            save();
        }
    }
}
