package me.fernandossm.multiplayerdragonrebirth.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewcomerPolicyTest {

    private final UUID done = UUID.randomUUID();
    private final NewcomerPolicy policy = new NewcomerPolicy(Set.of(done)::contains, false);

    private static PlayerFacts player(UUID id) {
        return new PlayerFacts(id, "p", true, false, false, false);
    }

    @Test
    void aPlayerWhoHasNotFoughtIsANewcomerAndCanSummon() {
        PlayerFacts p = player(UUID.randomUUID());
        assertTrue(policy.isNewcomer(p));
        assertTrue(policy.canSummon(p));
    }

    @Test
    void aPlayerWhoHasFoughtIsNot() {
        PlayerFacts p = player(done);
        assertFalse(policy.isNewcomer(p));
        assertFalse(policy.canSummon(p));
    }

    @Test
    void exemptPlayersAreNeverNewcomers() {
        PlayerFacts p = new PlayerFacts(UUID.randomUUID(), "staff", true, false, true, false);
        assertFalse(policy.isNewcomer(p));
        assertFalse(policy.canSummon(p));
        assertFalse(policy.countsAsNewcomerWhenParticipating(p));
    }

    @Test
    void creativeAndSpectatorPlayersNeitherSummonNorParticipate() {
        PlayerFacts p = new PlayerFacts(UUID.randomUUID(), "builder", false, false, false, false);
        assertTrue(policy.isNewcomer(p));
        assertFalse(policy.canSummon(p));
        assertFalse(policy.canParticipate(p));
    }

    @Test
    void vanishedPlayersNeitherSummonNorParticipate() {
        PlayerFacts p = new PlayerFacts(UUID.randomUUID(), "ghost", true, true, false, false);
        assertFalse(policy.canSummon(p));
        assertFalse(policy.canParticipate(p));
    }

    @Test
    void freeTheEndOnlyCountsWhenEnabled() {
        PlayerFacts p = new PlayerFacts(UUID.randomUUID(), "old", true, false, false, true);
        assertTrue(policy.isNewcomer(p));
        assertTrue(policy.countsAsNewcomerWhenParticipating(p));

        NewcomerPolicy withAdvancement = new NewcomerPolicy(id -> false, true);
        assertFalse(withAdvancement.isNewcomer(p));
        assertFalse(withAdvancement.canSummon(p));
        assertFalse(withAdvancement.countsAsNewcomerWhenParticipating(p));
    }
}
