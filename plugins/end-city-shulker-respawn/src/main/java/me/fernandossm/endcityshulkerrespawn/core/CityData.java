package me.fernandossm.endcityshulkerrespawn.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What the plugin knows about one End city: its spots, the chunks it has learned from, and a generation
 * that changes every time the city is learned again from scratch, so that shulkers tagged before then are
 * no longer taken for spot owners.
 */
public final class CityData {

    private final List<Spot> spots;
    private final Set<Long> learnedChunks;
    private final int generation;

    public CityData(List<Spot> spots, Set<Long> learnedChunks, int generation) {
        this.spots = new ArrayList<>(spots);
        this.learnedChunks = new LinkedHashSet<>(learnedChunks);
        this.generation = generation;
    }

    public static CityData empty() {
        return new CityData(List.of(), Set.of(), 0);
    }

    /** The same city with nothing learned, for learning it again from the shulkers it has now. */
    public CityData forgotten() {
        return new CityData(List.of(), Set.of(), generation + 1);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public List<Spot> spots() {
        return List.copyOf(spots);
    }

    public Spot spot(int index) {
        return spots.get(index);
    }

    public boolean hasSpot(int index) {
        return index >= 0 && index < spots.size();
    }

    public Set<Long> learnedChunks() {
        return Set.copyOf(learnedChunks);
    }

    public int generation() {
        return generation;
    }

    public boolean learned(int chunkX, int chunkZ) {
        return learnedChunks.contains(chunkKey(chunkX, chunkZ));
    }

    public void markLearned(int chunkX, int chunkZ) {
        learnedChunks.add(chunkKey(chunkX, chunkZ));
    }

    /** Adds a spot with a living shulker and returns its index. */
    public int addSpot(Spot spot) {
        spots.add(spot.refilled());
        return spots.size() - 1;
    }

    /** Indexes of the spots in this chunk whose shulker is dead and whose cooldown is over. */
    public List<Integer> dueIn(int chunkX, int chunkZ, long nowMillis) {
        List<Integer> due = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i);
            if (s.chunkX() == chunkX && s.chunkZ() == chunkZ && s.due(nowMillis)) {
                due.add(i);
            }
        }
        return due;
    }

    /** Starts the spot's cooldown; false if its shulker was already dead. */
    public boolean died(int index, long nowMillis, Duration cooldown) {
        Spot s = spots.get(index);
        if (!s.alive()) {
            return false;
        }
        spots.set(index, s.died(nowMillis, cooldown));
        return true;
    }

    /** The spot's shulker has settled elsewhere: the spot moves there, keeping its state. False if unchanged. */
    public boolean settled(int index, int x, int y, int z, Spot.Face face) {
        Spot s = spots.get(index);
        if (s.x() == x && s.y() == y && s.z() == z && s.face() == face) {
            return false;
        }
        spots.set(index, new Spot(x, y, z, face, s.dueAt()));
        return true;
    }

    public void refilled(int index) {
        spots.set(index, spots.get(index).refilled());
    }

    /** Makes every dead spot due now; returns how many. */
    public int dueNow(long nowMillis) {
        int count = 0;
        for (int i = 0; i < spots.size(); i++) {
            if (!spots.get(i).alive()) {
                spots.set(i, spots.get(i).dueBy(nowMillis));
                count++;
            }
        }
        return count;
    }

    public long alive() {
        return spots.stream().filter(Spot::alive).count();
    }

    /** When the next dead spot becomes due, if any is still cooling down. */
    public OptionalLong nextDue(long nowMillis) {
        return spots.stream().filter(s -> !s.alive() && !s.due(nowMillis)).mapToLong(Spot::dueAt).min();
    }
}
