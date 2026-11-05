package me.fernandossm.endcityshulkerrespawn.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import me.fernandossm.endcityshulkerrespawn.core.Spot.Face;
import org.junit.jupiter.api.Test;

class CityDataTest {

    private static final Duration COOLDOWN = Duration.ofHours(16);
    private static final long NOW = 1_000_000;

    private static Spot spot(int x, int z) {
        return new Spot(x, 70, z, Face.DOWN, Spot.ALIVE);
    }

    @Test
    void aNewSpotIsAlive() {
        CityData data = CityData.empty();
        int i = data.addSpot(spot(5, 5).dueBy(42));
        assertTrue(data.spot(i).alive());
        assertEquals(1, data.alive());
    }

    @Test
    void aDeadSpotIsDueAfterTheCooldownAndOnlyInItsChunk() {
        CityData data = CityData.empty();
        int a = data.addSpot(spot(5, 5));      // chunk 0,0
        int b = data.addSpot(spot(20, 5));     // chunk 1,0
        assertTrue(data.died(a, NOW, COOLDOWN));
        assertTrue(data.died(b, NOW, COOLDOWN));

        assertEquals(List.of(), data.dueIn(0, 0, NOW + COOLDOWN.toMillis() - 1));
        assertEquals(List.of(a), data.dueIn(0, 0, NOW + COOLDOWN.toMillis()));
        assertEquals(List.of(b), data.dueIn(1, 0, NOW + COOLDOWN.toMillis()));
    }

    @Test
    void dyingTwiceKeepsTheFirstCooldown() {
        CityData data = CityData.empty();
        int a = data.addSpot(spot(5, 5));
        assertTrue(data.died(a, NOW, COOLDOWN));
        assertFalse(data.died(a, NOW + 5000, COOLDOWN));
        assertEquals(NOW + COOLDOWN.toMillis(), data.spot(a).dueAt());
    }

    @Test
    void refillingMakesTheSpotAliveAgain() {
        CityData data = CityData.empty();
        int a = data.addSpot(spot(5, 5));
        data.died(a, NOW, COOLDOWN);
        data.refilled(a);
        assertTrue(data.spot(a).alive());
        assertTrue(data.dueIn(0, 0, Long.MAX_VALUE).isEmpty());
    }

    @Test
    void settlingMovesASpotAndKeepsItsState() {
        CityData data = CityData.empty();
        int a = data.addSpot(new Spot(1, 70, 1, Face.DOWN, Spot.ALIVE));
        data.died(a, NOW, COOLDOWN);
        assertTrue(data.settled(a, 2, 71, 1, Face.EAST));
        assertFalse(data.settled(a, 2, 71, 1, Face.EAST));
        assertEquals(new Spot(2, 71, 1, Face.EAST, NOW + COOLDOWN.toMillis()), data.spot(a));
    }

    @Test
    void negativeChunksAreFound() {
        CityData data = CityData.empty();
        int a = data.addSpot(spot(-1, -17)); // chunk -1,-2
        data.died(a, NOW, COOLDOWN);
        assertEquals(List.of(a), data.dueIn(-1, -2, Long.MAX_VALUE));
        assertTrue(data.dueIn(0, 0, Long.MAX_VALUE).isEmpty());
    }

    @Test
    void dueNowAndNextDue() {
        CityData data = CityData.empty();
        int a = data.addSpot(spot(1, 1));
        int b = data.addSpot(spot(2, 2));
        data.addSpot(spot(3, 3));
        data.died(a, NOW, Duration.ofHours(1));
        data.died(b, NOW, Duration.ofHours(2));
        assertEquals(OptionalLong.of(NOW + 3_600_000), data.nextDue(NOW));

        assertEquals(2, data.dueNow(NOW));
        assertEquals(List.of(a, b), data.dueIn(0, 0, NOW));
        assertEquals(OptionalLong.empty(), data.nextDue(NOW));
    }

    @Test
    void aSpotDueAtTimeZeroIsStillDeadNotAlive() {
        CityData data = CityData.empty();
        int a = data.addSpot(spot(1, 1));
        data.died(a, NOW, COOLDOWN);
        data.dueNow(0);
        assertFalse(data.spot(a).alive(), "0 means alive, so a dead spot is due at 1 at the earliest");
        assertEquals(List.of(a), data.dueIn(0, 0, 1));
    }

    @Test
    void learnedChunksAndChunkKeysAreDistinct() {
        CityData data = CityData.empty();
        data.markLearned(1, -1);
        assertTrue(data.learned(1, -1));
        assertFalse(data.learned(-1, 1));
        assertNotEquals(CityData.chunkKey(1, -1), CityData.chunkKey(-1, 1));
        assertNotEquals(CityData.chunkKey(0, -1), CityData.chunkKey(-1, 0));
    }

    @Test
    void forgettingStartsANewGeneration() {
        CityData data = new CityData(List.of(spot(1, 1)), Set.of(CityData.chunkKey(0, 0)), 3);
        CityData forgotten = data.forgotten();
        assertEquals(4, forgotten.generation());
        assertTrue(forgotten.spots().isEmpty());
        assertFalse(forgotten.learned(0, 0));
    }
}
