package me.fernandossm.bluemapskinsrestorerheads.paper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Minecraft's own textures (the default skins), read from the client jar BlueMap downloads to
 * render its maps: nothing of Mojang's is shipped with this plugin, and nothing more is downloaded.
 * BlueMap keeps the jar in its data folder ("data" in plugins/BlueMap/core.conf, "bluemap" by default).
 */
public final class ClientTextures implements Function<String, @Nullable BufferedImage> {

    private static final Pattern DATA = Pattern.compile("(?m)^\\s*data\\s*[:=]\\s*\"?([^\"#\\r\\n]+?)\"?\\s*(#.*)?$");

    private final Path serverRoot;
    private final String minecraftVersion;
    private final Logger log;
    private final Map<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();
    private volatile @Nullable ZipFile jar;
    private boolean warned;

    public ClientTextures(Path serverRoot, String minecraftVersion, Logger log) {
        this.serverRoot = serverRoot;
        this.minecraftVersion = minecraftVersion;
        this.log = log;
    }

    /** Opens the jar if it is not open yet (BlueMap downloads it on its first start). */
    public synchronized boolean available() {
        if (jar != null) {
            return true;
        }
        Path found = find();
        if (found == null) {
            if (!warned) {
                log.warning("Minecraft's client jar is not in BlueMap's data folder yet (BlueMap needs accept-download: true"
                        + " in core.conf). Players without a skin get no head until it is there.");
                warned = true;
            }
            return false;
        }
        try {
            jar = new ZipFile(found.toFile());
            cache.clear();
            return true;
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not open " + found, e);
            return false;
        }
    }

    private @Nullable Path find() {
        Path dataFolder = serverRoot.resolve("bluemap");
        Path coreConf = serverRoot.resolve("plugins").resolve("BlueMap").resolve("core.conf");
        try {
            if (Files.exists(coreConf)) {
                Matcher m = DATA.matcher(Files.readString(coreConf, StandardCharsets.UTF_8));
                if (m.find()) {
                    dataFolder = serverRoot.resolve(m.group(1).strip());
                }
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not read " + coreConf, e);
        }
        Path exact = dataFolder.resolve("minecraft-client-" + minecraftVersion + ".jar");
        if (Files.isRegularFile(exact)) {
            return exact;
        }
        if (!Files.isDirectory(dataFolder)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dataFolder)) {
            return files.filter(p -> p.getFileName().toString().matches("minecraft-client-.*\\.jar"))
                    .max(Comparator.comparing(p -> p.toFile().lastModified())).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** A texture by its path in the jar, such as "assets/minecraft/textures/entity/banner/creeper.png". */
    @Override
    public @Nullable BufferedImage apply(String path) {
        ZipFile zip = jar;
        if (zip == null) {
            return null;
        }
        return cache.computeIfAbsent(path, p -> {
            ZipEntry entry = zip.getEntry(p);
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return Optional.ofNullable(ImageIO.read(in));
            } catch (IOException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    public synchronized void close() {
        if (jar != null) {
            try {
                jar.close();
            } catch (IOException ignored) {
                // closing on shutdown
            }
            jar = null;
        }
    }
}
