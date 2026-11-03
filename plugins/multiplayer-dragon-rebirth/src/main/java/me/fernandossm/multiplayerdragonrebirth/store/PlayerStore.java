package me.fernandossm.multiplayerdragonrebirth.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Players recorded as having fought the dragon. Kept in a JSON file; every change is written in the background by
 * replacing the file atomically, so a crash leaves either the old or the new file, never a partial one.
 *
 * <p>Not thread-safe: called from the server thread only.
 */
public final class PlayerStore implements AutoCloseable {

    public enum Reason {
        /** Damaged a dragon that died. */
        KILL,
        /** Had joined the server before the plugin first ran, or was marked with /mdr mark-existing. */
        EXISTING_PLAYER,
        /** Marked by an admin with /mdr mark. */
        ADMIN
    }

    public record Entry(String name, Instant since, Reason reason, @Nullable String world) {}

    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Logger log;
    private final boolean created;
    private final Map<UUID, Entry> players;
    private final ExecutorService writer;

    private PlayerStore(Path file, Logger log, boolean created, Map<UUID, Entry> players) {
        this.file = file;
        this.log = log;
        this.created = created;
        this.players = players;
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MultiplayerDragonRebirth-store");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Loads the file, or starts empty if it does not exist.
     *
     * @throws StoreException if the file exists but cannot be read; the plugin must not run on an empty
     *                        store in that case, or every player would become a newcomer again
     */
    public static PlayerStore open(Path file, Logger log) throws StoreException {
        if (!Files.exists(file)) {
            return new PlayerStore(file, log, true, new LinkedHashMap<>());
        }
        try {
            Data data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (data == null) {
                throw new StoreException(file + " is empty");
            }
            if (data.version > FORMAT_VERSION) {
                throw new StoreException(file + " was written by a newer version of the plugin (format " + data.version + ")");
            }
            Map<UUID, Entry> players = new LinkedHashMap<>();
            if (data.players != null) {
                for (Map.Entry<String, EntryData> e : data.players.entrySet()) {
                    EntryData d = e.getValue();
                    players.put(UUID.fromString(e.getKey()), new Entry(
                            d.name == null ? "" : d.name,
                            d.since == null ? Instant.EPOCH : Instant.parse(d.since),
                            d.reason == null ? Reason.ADMIN : Reason.valueOf(d.reason),
                            d.world));
                }
            }
            return new PlayerStore(file, log, false, players);
        } catch (IOException | JsonParseException | IllegalArgumentException | DateTimeParseException e) {
            throw new StoreException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** True when the file did not exist: this is the plugin's first run on this server. */
    public boolean created() {
        return created;
    }

    public boolean isDone(UUID player) {
        return players.containsKey(player);
    }

    public Optional<Entry> get(UUID player) {
        return Optional.ofNullable(players.get(player));
    }

    /** The most recently recorded player with this name, ignoring case. */
    public Optional<UUID> findByName(String name) {
        UUID found = null;
        for (Map.Entry<UUID, Entry> e : players.entrySet()) {
            if (e.getValue().name().equalsIgnoreCase(name)) {
                found = e.getKey();
            }
        }
        return Optional.ofNullable(found);
    }

    public int size() {
        return players.size();
    }

    /** Records a player as having fought; a player already recorded keeps their original entry. */
    public boolean markDone(UUID player, Entry entry) {
        if (players.containsKey(player)) {
            return false;
        }
        players.put(player, entry);
        save();
        return true;
    }

    /** Records several players at once with a single write; returns how many were new. */
    public int markAllDone(Map<UUID, Entry> entries) {
        int added = 0;
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            if (players.putIfAbsent(e.getKey(), e.getValue()) == null) {
                added++;
            }
        }
        if (added > 0) {
            save();
        }
        return added;
    }

    public boolean reset(UUID player) {
        boolean removed = players.remove(player) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public int resetAll() {
        int count = players.size();
        players.clear();
        save();
        return count;
    }

    /** Queues a write of the current state. */
    public void save() {
        String json = serialize();
        writer.execute(() -> write(json));
    }

    /** Writes the current state and waits for it, and every write queued before it, to finish. */
    public void flush() {
        save();
        try {
            writer.submit(() -> {}).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.log(Level.SEVERE, "Timed out writing " + file, e);
        }
    }

    /** Writes the current state, waits for every queued write to finish, and stops writing. */
    @Override
    public void close() {
        if (writer.isShutdown()) {
            return;
        }
        save();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                log.severe("Timed out writing " + file + "; the last changes may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String serialize() {
        Data data = new Data();
        data.version = FORMAT_VERSION;
        data.players = new LinkedHashMap<>();
        players.forEach((id, e) -> {
            EntryData d = new EntryData();
            d.name = e.name();
            d.since = e.since().toString();
            d.reason = e.reason().name();
            d.world = e.world();
            data.players.put(id.toString(), d);
        });
        return GSON.toJson(data);
    }

    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Cannot write " + file, e);
        }
    }

    public static String describe(Reason reason) {
        return reason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // The file's shape. Fields are read and written by Gson.
    private static final class Data {
        int version;
        Map<String, EntryData> players;
    }

    private static final class EntryData {
        String name;
        String since;
        String reason;
        String world;
    }
}
