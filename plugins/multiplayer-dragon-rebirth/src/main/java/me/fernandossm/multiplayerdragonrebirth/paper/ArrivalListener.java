package me.fernandossm.multiplayerdragonrebirth.paper;

import java.util.function.Supplier;
import me.fernandossm.multiplayerdragonrebirth.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/** Players entering an End world, or logging in inside one. */
public final class ArrivalListener implements Listener {

    /** Vanish and permission plugins set up a joining player during the join; check a second later. */
    private static final long JOIN_DELAY_TICKS = 20;

    private final Plugin plugin;
    private final Supplier<Settings> settings;
    private final Summoner summoner;

    ArrivalListener(Plugin plugin, Supplier<Settings> settings, Summoner summoner) {
        this.plugin = plugin;
        this.settings = settings;
        this.summoner = summoner;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        summoner.onArrival(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.get().triggerOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                summoner.onArrival(player);
            }
        }, JOIN_DELAY_TICKS);
    }
}
