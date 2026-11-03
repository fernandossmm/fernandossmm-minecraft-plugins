package me.fernandossm.multiplayerdragonrebirth.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import me.fernandossm.multiplayerdragonrebirth.store.PlayerStore.Entry;
import me.fernandossm.multiplayerdragonrebirth.store.PlayerStore.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerStoreTest {

    private static final Logger LOG = Logger.getAnonymousLogger();
    private static final Instant WHEN = Instant.parse("2026-11-03T12:00:00Z");

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("players.json");
    }

    @Test
    void aMissingFileIsAFirstRun() throws Exception {
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            assertTrue(store.created());
            assertEquals(0, store.size());
        }
        // close() writes the file, so the next start is not a first run.
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            assertFalse(store.created());
        }
    }

    @Test
    void everythingSurvivesAReopen() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            store.markDone(a, new Entry("Alice", WHEN, Reason.KILL, "world_the_end"));
            store.markDone(b, new Entry("Bob", WHEN, Reason.EXISTING_PLAYER, null));
        }
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            assertEquals(Optional.of(new Entry("Alice", WHEN, Reason.KILL, "world_the_end")), store.get(a));
            assertEquals(Optional.of(new Entry("Bob", WHEN, Reason.EXISTING_PLAYER, null)), store.get(b));
        }
        assertFalse(Files.exists(dir.resolve("players.json.tmp")), "the temporary file is renamed away");
    }

    @Test
    void aPlayerKeepsTheirFirstEntry() throws Exception {
        UUID a = UUID.randomUUID();
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            assertTrue(store.markDone(a, new Entry("Alice", WHEN, Reason.EXISTING_PLAYER, null)));
            assertFalse(store.markDone(a, new Entry("Alice", WHEN.plusSeconds(60), Reason.KILL, "world_the_end")));
            assertEquals(Reason.EXISTING_PLAYER, store.get(a).orElseThrow().reason());
        }
    }

    @Test
    void markAllDoneCountsOnlyNewPlayers() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            store.markDone(a, new Entry("Alice", WHEN, Reason.KILL, null));
            Map<UUID, Entry> both = new LinkedHashMap<>();
            both.put(a, new Entry("Alice", WHEN, Reason.EXISTING_PLAYER, null));
            both.put(b, new Entry("Bob", WHEN, Reason.EXISTING_PLAYER, null));
            assertEquals(1, store.markAllDone(both));
            assertEquals(0, store.markAllDone(both));
            assertEquals(Reason.KILL, store.get(a).orElseThrow().reason());
        }
    }

    @Test
    void resetAndResetAll() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            store.markDone(a, new Entry("Alice", WHEN, Reason.KILL, null));
            store.markDone(b, new Entry("Bob", WHEN, Reason.KILL, null));
            assertTrue(store.reset(a));
            assertFalse(store.reset(a));
            assertFalse(store.isDone(a));
            assertEquals(1, store.resetAll());
            assertEquals(0, store.size());
        }
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            assertEquals(0, store.size());
            assertFalse(store.created(), "resetting everyone is not a first run");
        }
    }

    @Test
    void findByNameIgnoresCaseAndPrefersTheLatest() throws Exception {
        UUID old = UUID.randomUUID();
        UUID renamed = UUID.randomUUID();
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            store.markDone(old, new Entry("Steve", WHEN, Reason.KILL, null));
            store.markDone(renamed, new Entry("steve", WHEN, Reason.KILL, null));
            assertEquals(Optional.of(renamed), store.findByName("STEVE"));
            assertEquals(Optional.empty(), store.findByName("Alex"));
        }
    }

    @Test
    void anUnreadableFileIsRefusedRatherThanTreatedAsEmpty() throws Exception {
        Files.writeString(file(), "{ this is not json");
        assertThrows(StoreException.class, () -> PlayerStore.open(file(), LOG));

        Files.writeString(file(), "");
        assertThrows(StoreException.class, () -> PlayerStore.open(file(), LOG));

        Files.writeString(file(), "{\"version\":1,\"players\":{\"not-a-uuid\":{\"name\":\"x\"}}}");
        assertThrows(StoreException.class, () -> PlayerStore.open(file(), LOG));

        Files.writeString(file(), "{\"version\":99,\"players\":{}}");
        assertThrows(StoreException.class, () -> PlayerStore.open(file(), LOG));
    }

    @Test
    void missingOptionalFieldsAreTolerated() throws Exception {
        UUID a = UUID.randomUUID();
        Files.writeString(file(), "{\"version\":1,\"players\":{\"" + a + "\":{}}}");
        try (PlayerStore store = PlayerStore.open(file(), LOG)) {
            assertTrue(store.isDone(a));
            assertEquals(Reason.ADMIN, store.get(a).orElseThrow().reason());
        }
    }
}
