package me.fernandossm.bluemapbannermarkers.paper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import me.fernandossm.bluemapbannermarkers.core.Faces;
import me.fernandossm.bluemapbannermarkers.store.WaypointStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Waypoint owners' faces, saved as faces/UUID.png so they show while their owner is offline. A face is cut
 * from the skin the player has in game: on an offline-mode server that is the one SkinsRestorer applied to
 * their profile; a player without one gets the game's default skin for their UUID.
 */
public final class FaceStore {

    /** Skins come only from Mojang's texture server, which is where profile skins point. */
    private static final Set<String> SKIN_HOSTS = Set.of("textures.minecraft.net");
    private static final String DEFAULT = "default";
    private static final int MAX_SKIN_BYTES = 64 * 1024;

    private final Plugin plugin;
    private final Path folder;
    private final WaypointStore store;
    private final Function<String, @Nullable BufferedImage> textures;
    private final Runnable changed;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<UUID, BufferedImage> loaded = new ConcurrentHashMap<>();
    private final Set<UUID> fetching = ConcurrentHashMap.newKeySet();

    /** @param changed called on the main thread when a face was saved or replaced */
    public FaceStore(Plugin plugin, Path folder, WaypointStore store, Function<String, @Nullable BufferedImage> textures, Runnable changed) {
        this.plugin = plugin;
        this.folder = folder;
        this.store = store;
        this.textures = textures;
        this.changed = changed;
        this.log = plugin.getLogger();
    }

    /** The face to show for this owner, or null while there is none yet. */
    public @Nullable BufferedImage face(UUID owner) {
        BufferedImage face = loaded.get(owner);
        if (face != null) {
            return face;
        }
        Path file = folder.resolve(owner + ".png");
        if (store.skin(owner) == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            face = ImageIO.read(file.toFile());
        } catch (IOException e) {
            return null;
        }
        if (face != null) {
            loaded.put(owner, face);
        }
        return face;
    }

    /** Which skin the saved face shows; part of its file name on the map, so browsers fetch a new face. */
    public @Nullable String version(UUID owner) {
        String skin = store.skin(owner);
        return skin == null ? null : hash(skin);
    }

    /** Saves the player's face again if their skin is not the one it was cut from. Main thread. */
    public void refresh(Player player) {
        UUID owner = player.getUniqueId();
        URL skinUrl = player.getPlayerProfile().getTextures().getSkin();
        String skin = skinUrl == null ? DEFAULT : skinUrl.toString();
        if (skin.equals(store.skin(owner)) && Files.isRegularFile(folder.resolve(owner + ".png"))) {
            return;
        }
        if (skinUrl == null) {
            BufferedImage defaultSkin = textures.apply(Faces.defaultSkinPath(owner));
            if (defaultSkin != null) {
                save(owner, DEFAULT, defaultSkin);
            }
            return;
        }
        URI uri;
        try {
            uri = skinUrl.toURI();
        } catch (Exception e) {
            return;
        }
        if (!SKIN_HOSTS.contains(uri.getHost()) || !fetching.add(owner)) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            BufferedImage image = null;
            if (error == null && response.statusCode() == 200 && response.body().length <= MAX_SKIN_BYTES) {
                try {
                    image = ImageIO.read(new ByteArrayInputStream(response.body()));
                } catch (IOException ignored) {
                    // not an image: no face
                }
            }
            BufferedImage skinImage = image;
            if (!plugin.isEnabled()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                fetching.remove(owner);
                if (skinImage == null) {
                    log.warning("Could not download " + player.getName() + "'s skin from " + uri
                            + (error != null ? " (" + error + ")" : response != null ? " (HTTP " + response.statusCode() + ")" : ""));
                } else {
                    save(owner, skin, skinImage);
                }
            });
        });
    }

    private void save(UUID owner, String skin, BufferedImage skinImage) {
        BufferedImage face = Faces.face(skinImage);
        if (face == null) {
            return;
        }
        try {
            Files.createDirectories(folder);
            ImageIO.write(face, "png", folder.resolve(owner + ".png").toFile());
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not save the face of " + owner, e);
            return;
        }
        loaded.put(owner, face);
        store.skin(owner, skin);
        changed.run();
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
