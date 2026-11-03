package me.fernandossm.multiplayerdragonrebirth.api;

import java.util.List;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A dragon is about to be summoned because newcomers are in the End. Cancelling it skips this summoning; the
 * next newcomer to arrive tries again. Not called for summonings forced with /mdr summon.
 */
public final class DragonSummonEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final List<Player> newcomers;
    private boolean cancelled;

    public DragonSummonEvent(World world, List<Player> newcomers) {
        this.world = world;
        this.newcomers = List.copyOf(newcomers);
    }

    public World getWorld() {
        return world;
    }

    /** The newcomers in the End when the countdown ended. */
    public List<Player> getNewcomers() {
        return newcomers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
