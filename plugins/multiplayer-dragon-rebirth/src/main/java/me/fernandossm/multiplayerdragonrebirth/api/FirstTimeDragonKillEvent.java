package me.fernandossm.multiplayerdragonrebirth.api;

import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A dragon died and at least one newcomer had damaged it, so it drops its egg and 12,000 experience. Called
 * when the dragon's health reaches zero, before its death animation.
 */
public final class FirstTimeDragonKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final EnderDragon dragon;
    private final List<UUID> participants;
    private final List<UUID> newcomers;

    public FirstTimeDragonKillEvent(World world, EnderDragon dragon, List<UUID> participants, List<UUID> newcomers) {
        this.world = world;
        this.dragon = dragon;
        this.participants = List.copyOf(participants);
        this.newcomers = List.copyOf(newcomers);
    }

    public World getWorld() {
        return world;
    }

    public EnderDragon getDragon() {
        return dragon;
    }

    /** Every player who damaged the dragon. */
    public List<UUID> getParticipants() {
        return participants;
    }

    /** The participants for whom this was their first dragon kill. */
    public List<UUID> getNewcomers() {
        return newcomers;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
