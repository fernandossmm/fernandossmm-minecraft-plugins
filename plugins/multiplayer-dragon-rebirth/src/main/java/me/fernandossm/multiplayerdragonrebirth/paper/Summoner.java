package me.fernandossm.multiplayerdragonrebirth.paper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import me.fernandossm.multiplayerdragonrebirth.api.DragonSummonEvent;
import me.fernandossm.multiplayerdragonrebirth.config.Settings;
import me.fernandossm.multiplayerdragonrebirth.core.FightTracker;
import me.fernandossm.multiplayerdragonrebirth.core.NewcomerPolicy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Starts a countdown when a newcomer arrives in a quiet End, and summons the dragon when it ends if a
 * newcomer is still there. One countdown per world: newcomers arriving during it join it.
 */
public final class Summoner {

    /** @param newcomers who started or joined the countdown, by UUID, with their names */
    public record Countdown(BukkitTask task, Map<UUID, String> newcomers, long endsAtMillis) {}

    private final Plugin plugin;
    private final Logger log;
    private final Supplier<Settings> settings;
    private final Supplier<NewcomerPolicy> policy;
    private final Fights fights;
    private final FightTracker tracker;
    private final Map<UUID, Countdown> countdowns = new HashMap<>();

    Summoner(Plugin plugin, Supplier<Settings> settings, Supplier<NewcomerPolicy> policy, Fights fights, FightTracker tracker) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.settings = settings;
        this.policy = policy;
        this.fights = fights;
        this.tracker = tracker;
    }

    /** A player is now in this world: entered it, or logged in there. */
    public void onArrival(Player player) {
        World world = player.getWorld();
        Optional<Fight> fight = fights.of(world);
        if (fight.isEmpty() || !canSummon(player)) {
            return;
        }
        Countdown running = countdowns.get(world.getUID());
        if (running != null) {
            running.newcomers().putIfAbsent(player.getUniqueId(), player.getName());
            return;
        }
        if (!fight.get().quiet()) {
            return; // they fight the dragon that is already there, or about to be
        }
        int seconds = settings.get().countdownSeconds();
        Map<UUID, String> newcomers = new LinkedHashMap<>();
        newcomers.put(player.getUniqueId(), player.getName());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> countdownEnded(world), seconds * 20L);
        countdowns.put(world.getUID(), new Countdown(task, newcomers, System.currentTimeMillis() + seconds * 1000L));
        Broadcasts.send(settings.get().countdownMessage(), log,
                Broadcasts.newcomers(newcomers.values()), Broadcasts.seconds(seconds), Broadcasts.world(world));
    }

    private void countdownEnded(World world) {
        countdowns.remove(world.getUID());
        if (Bukkit.getWorld(world.getUID()) == null) {
            return;
        }
        Optional<Fight> fight = fights.of(world);
        if (fight.isEmpty() || !fight.get().quiet()) {
            return;
        }
        List<Player> newcomers = world.getPlayers().stream().filter(this::canSummon).toList();
        if (newcomers.isEmpty()) {
            log.info("No newcomer is left in " + world.getName() + " at the end of the countdown; not summoning");
            return;
        }
        if (!new DragonSummonEvent(world, newcomers).callEvent()) {
            return;
        }
        summon(fight.get());
    }

    /** Summons now, without a countdown or newcomer check. Check {@link Fight#quiet()} first. */
    public boolean summon(Fight fight) {
        tracker.clear(fight.world().getUID());
        boolean started = fight.summon();
        if (started) {
            log.info("Summoning the dragon in " + fight.world().getName());
        } else {
            log.warning("The server refused to summon a dragon in " + fight.world().getName() + ": " + fight.describe());
        }
        return started;
    }

    public Optional<Countdown> countdown(World world) {
        return Optional.ofNullable(countdowns.get(world.getUID()));
    }

    public void cancelAll() {
        countdowns.values().forEach(c -> c.task().cancel());
        countdowns.clear();
    }

    private boolean canSummon(Player player) {
        return policy.get().canSummon(PaperFacts.of(player, settings.get().freeTheEndCountsAsDone()));
    }
}
