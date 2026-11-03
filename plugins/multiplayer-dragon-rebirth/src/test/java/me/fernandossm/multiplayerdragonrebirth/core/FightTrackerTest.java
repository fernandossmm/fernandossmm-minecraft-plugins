package me.fernandossm.multiplayerdragonrebirth.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.fernandossm.multiplayerdragonrebirth.core.FightTracker.Participant;
import org.junit.jupiter.api.Test;

class FightTrackerTest {

    private final FightTracker tracker = new FightTracker();
    private final UUID end = UUID.randomUUID();
    private final UUID otherEnd = UUID.randomUUID();

    @Test
    void recordsEachPlayerOncePerWorldInOrder() {
        Participant a = new Participant(UUID.randomUUID(), "a", true);
        Participant b = new Participant(UUID.randomUUID(), "b", true);
        tracker.record(end, a);
        tracker.record(end, b);
        tracker.record(end, a);
        tracker.record(otherEnd, b);

        assertEquals(List.of(a, b), tracker.participants(end));
        assertEquals(List.of(b), tracker.participants(otherEnd));
    }

    @Test
    void takeReturnsAndForgetsOnlyThatWorld() {
        Participant a = new Participant(UUID.randomUUID(), "a", true);
        tracker.record(end, a);
        tracker.record(otherEnd, a);

        assertEquals(List.of(a), tracker.take(end));
        assertTrue(tracker.participants(end).isEmpty());
        assertTrue(tracker.take(end).isEmpty());
        assertEquals(List.of(a), tracker.participants(otherEnd));
    }

    @Test
    void newcomersAreEligibleParticipantsWhoHaveNotFought() {
        Participant veteran = new Participant(UUID.randomUUID(), "veteran", true);
        Participant staff = new Participant(UUID.randomUUID(), "staff", false);
        Participant newcomer = new Participant(UUID.randomUUID(), "new", true);
        List<Participant> all = List.of(veteran, staff, newcomer);

        assertEquals(List.of(newcomer), FightTracker.newcomers(all, Set.of(veteran.id())::contains));
        assertTrue(FightTracker.newcomers(List.of(veteran, staff), Set.of(veteran.id())::contains).isEmpty());
    }
}
