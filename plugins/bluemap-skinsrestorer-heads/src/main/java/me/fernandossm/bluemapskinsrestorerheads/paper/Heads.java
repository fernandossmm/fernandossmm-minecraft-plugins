package me.fernandossm.bluemapskinsrestorerheads.paper;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import me.fernandossm.bluemapskinsrestorerheads.core.Skins;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Each online player's skin as they have it in game, which on an offline-mode server is the one SkinsRestorer
 * applied. It is BlueMap's skin provider, so BlueMap draws the head with it when a player joins; and because
 * SkinsRestorer applies a skin a moment after joining, or whenever a player changes it, the head is also
 * written again into every map whenever the skin changes.
 */
public final class Heads implements SkinProvider {

    /** How long BlueMap's request on join waits for the skin before taking the default one. */
    private static final long WAIT_MS = 10_000;
    /** How soon a skin that could not be downloaded is tried again. */
    private static final long RETRY_MS = 60_000;
    private static final String DEFAULT = "default";

    private record Skin(String key, BufferedImage image) {}

    private final Plugin plugin;
    private final Function<String, @Nullable BufferedImage> textures;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** The skin each online player's head shows, by the key of the skin it was drawn from. */
    private final Map<UUID, Skin> skins = new ConcurrentHashMap<>();
    private final Set<UUID> fetching = ConcurrentHashMap.newKeySet();
    /** A skin that could not be had, and when: not tried again before RETRY_MS. */
    private final Map<UUID, Map.Entry<String, Long>> failed = new ConcurrentHashMap<>();

    public Heads(Plugin plugin, Function<String, @Nullable BufferedImage> textures) {
        this.plugin = plugin;
        this.textures = textures;
        this.log = plugin.getLogger();
    }

    /** Called by BlueMap, off the main thread, when a player joins. */
    @Override
    public Optional<BufferedImage> load(UUID player) {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            Skin skin = skins.get(player);
            if (skin != null) {
                return Optional.of(skin.image());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Skin skin = skins.get(player);
        if (skin != null) {
            return Optional.of(skin.image());
        }
        // Never empty while a default exists: BlueMap deletes the head when it gets no skin.
        return Optional.ofNullable(textures.apply(Skins.defaultSkinPath(player)));
    }

    /** Looks at the skin the player has now; if the head shows another one, draws it again. Main thread. */
    public void check(Player player) {
        UUID id = player.getUniqueId();
        URL url = player.getPlayerProfile().getTextures().getSkin();
        String key = url == null ? DEFAULT : url.toString();
        Skin current = skins.get(id);
        if (current != null && current.key().equals(key)) {
            return;
        }
        Map.Entry<String, Long> failure = failed.get(id);
        if (failure != null && failure.getKey().equals(key) && System.currentTimeMillis() - failure.getValue() < RETRY_MS) {
            return;
        }
        if (!fetching.add(id)) {
            return;
        }
        String name = player.getName();
        if (url == null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> done(id, name, key, textures.apply(Skins.defaultSkinPath(id)), "the default skin is not in BlueMap's client jar yet"));
            return;
        }
        URI uri = Skins.downloadable(key);
        if (uri == null) {
            done(id, name, key, null, "its skin is not on " + Skins.SKIN_HOST + ": " + key);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            BufferedImage image = null;
            String problem = null;
            if (error != null) {
                problem = "could not download " + uri + " (" + error + ")";
            } else if (response.statusCode() != 200 || response.body().length > Skins.MAX_SKIN_BYTES) {
                problem = "could not download " + uri + " (HTTP " + response.statusCode() + ", " + response.body().length + " bytes)";
            } else {
                try {
                    image = ImageIO.read(new ByteArrayInputStream(response.body()));
                } catch (IOException ignored) {
                    // not an image
                }
                if (!Skins.isSkin(image)) {
                    image = null;
                    problem = uri + " is not a skin";
                }
            }
            done(id, name, key, image, problem);
        });
    }

    /** Forgets a player who left: the head stays on the map, and is checked again when they rejoin. */
    public void forget(UUID player) {
        skins.remove(player);
        failed.remove(player);
    }

    /** Draws every online player's head again on the next check, for example after BlueMap reloaded its maps. */
    public void forgetAll() {
        skins.clear();
        failed.clear();
    }

    private void done(UUID id, String name, String key, @Nullable BufferedImage image, @Nullable String problem) {
        fetching.remove(id);
        if (image == null) {
            Map.Entry<String, Long> previous = failed.put(id, Map.entry(key, System.currentTimeMillis()));
            if (previous == null || !previous.getKey().equals(key)) {
                log.warning("No head for " + name + ": " + problem);
            }
            return;
        }
        failed.remove(id);
        BufferedImage shown = Skins.asShownInGame(image);
        skins.put(id, new Skin(key, shown));
        write(id, shown);
    }

    /** The head, drawn as BlueMap draws its own, into every map's assets. Any thread. */
    private void write(UUID id, BufferedImage skin) {
        BlueMapAPI.getInstance().ifPresent(api -> {
            BufferedImage head;
            try {
                head = api.getPlugin().getPlayerMarkerIconFactory().apply(id, skin);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "BlueMap could not draw the head of " + id, e);
                return;
            }
            for (BlueMapMap map : api.getMaps()) {
                try (OutputStream out = map.getAssetStorage().writeAsset("playerheads/" + id + ".png")) {
                    ImageIO.write(head, "png", out);
                } catch (IOException e) {
                    log.log(Level.WARNING, "Could not write the head of " + id + " to map " + map.getId(), e);
                }
            }
        });
    }
}
