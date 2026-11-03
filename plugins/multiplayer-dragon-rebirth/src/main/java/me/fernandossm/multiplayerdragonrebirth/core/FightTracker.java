package me.fernandossm.multiplayerdragonrebirth.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Players who damaged the current dragon, per End world. Held in memory only: a fight that spans a restart
 * counts the damage dealt after the restart.
 */
public final class FightTracker {

    /**
     * @param eligible  could make the kill first-time (not exempt, and not excluded by "Free the End");
     *                  whether they are still a newcomer is checked at the kill
     */
    public record Participant(UUID id, String name, boolean eligible) {}

    private final Map<UUID, Map<UUID, Participant>> byWorld = new HashMap<>();

    public void record(UUID world, Participant participant) {
        byWorld.computeIfAbsent(world, w -> new LinkedHashMap<>()).put(participant.id(), participant);
    }

    public List<Participant> participants(UUID world) {
        Map<UUID, Participant> participants = byWorld.get(world);
        return participants == null ? List.of() : List.copyOf(participants.values());
    }

    /** Returns the participants of the dragon that just died and forgets them. */
    public List<Participant> take(UUID world) {
        Map<UUID, Participant> participants = byWorld.remove(world);
        return participants == null ? List.of() : List.copyOf(participants.values());
    }

    public void clear(UUID world) {
        byWorld.remove(world);
    }

    /** Participants who make a kill first-time: eligible and not yet recorded as having fought. */
    public static List<Participant> newcomers(List<Participant> participants, Predicate<UUID> isDone) {
        return participants.stream().filter(p -> p.eligible() && !isDone.test(p.id())).toList();
    }
}
