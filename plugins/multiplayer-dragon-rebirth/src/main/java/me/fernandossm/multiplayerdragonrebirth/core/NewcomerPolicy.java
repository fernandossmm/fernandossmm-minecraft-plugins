package me.fernandossm.multiplayerdragonrebirth.core;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who is a newcomer, who may summon a dragon, and whose damage counts towards a kill.
 *
 * @param isDone                  whether a player is recorded as having fought the dragon
 * @param freeTheEndCountsAsDone  the config option of the same name
 */
public record NewcomerPolicy(Predicate<UUID> isDone, boolean freeTheEndCountsAsDone) {

    /** Has never taken part in a dragon kill and is not excluded from being one. */
    public boolean isNewcomer(PlayerFacts player) {
        return !player.exempt()
                && !(freeTheEndCountsAsDone && player.hasFreeTheEnd())
                && !isDone.test(player.id());
    }

    /** Arriving in the End summons a dragon for this player. */
    public boolean canSummon(PlayerFacts player) {
        return player.playableMode() && !player.vanished() && isNewcomer(player);
    }

    /** Damage dealt by this player makes them a participant in the kill. */
    public boolean canParticipate(PlayerFacts player) {
        return player.playableMode() && !player.vanished();
    }

    /** A participant who could still make a kill first-time, judged when they dealt damage. */
    public boolean countsAsNewcomerWhenParticipating(PlayerFacts player) {
        return !player.exempt() && !(freeTheEndCountsAsDone && player.hasFreeTheEnd());
    }
}
