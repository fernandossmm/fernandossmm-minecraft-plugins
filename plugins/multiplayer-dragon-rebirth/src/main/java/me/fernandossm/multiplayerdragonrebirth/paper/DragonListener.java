package me.fernandossm.multiplayerdragonrebirth.paper;

import io.papermc.paper.event.block.DragonEggFormEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import me.fernandossm.multiplayerdragonrebirth.api.FirstTimeDragonKillEvent;
import me.fernandossm.multiplayerdragonrebirth.config.Settings;
import me.fernandossm.multiplayerdragonrebirth.core.FightTracker;
import me.fernandossm.multiplayerdragonrebirth.core.FightTracker.Participant;
import me.fernandossm.multiplayerdragonrebirth.core.NewcomerPolicy;
import me.fernandossm.multiplayerdragonrebirth.core.PlayerFacts;
import me.fernandossm.multiplayerdragonrebirth.store.PlayerStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Who damages the dragon, what its death gives, where the egg goes, and protection of summoning crystals.
 *
 * <p>The death event fires when the killing blow lands; the dragon then flies to the portal and plays its
 * death animation, dropping the experience that the event set and, at the very end, forming the egg.
 */
public final class DragonListener implements Listener {

    /** What the world's first dragon drops; later dragons drop 500. */
    static final int FIRST_TIME_EXPERIENCE = 12_000;

    private final Plugin plugin;
    private final Logger log;
    private final Supplier<Settings> settings;
    private final Supplier<NewcomerPolicy> policy;
    private final PlayerStore store;
    private final Fights fights;
    private final PaperFights paperFights;
    private final FightTracker tracker;
    /** Worlds whose dragon died in a first-time kill and whose eggs have not formed yet, with how many. */
    private final Map<UUID, Integer> eggsDue = new HashMap<>();
    /** Eggs beyond the one vanilla forms, to stack on it once it has formed. */
    private final Map<UUID, Integer> extraEggs = new HashMap<>();

    DragonListener(Plugin plugin, Supplier<Settings> settings, Supplier<NewcomerPolicy> policy, PlayerStore store,
                   Fights fights, PaperFights paperFights, FightTracker tracker) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.settings = settings;
        this.policy = policy;
        this.store = store;
        this.fights = fights;
        this.paperFights = paperFights;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void protectSummoningCrystals(EntityDamageEvent event) {
        if (paperFights.isSummoningCrystal(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordDamage(EntityDamageEvent event) {
        Entity hit = event.getEntity() instanceof ComplexEntityPart part ? part.getParent() : event.getEntity();
        if (!(hit instanceof EnderDragon dragon)
                || !(event.getDamageSource().getCausingEntity() instanceof Player player)) {
            return;
        }
        Optional<Fight> fight = fights.of(dragon.getWorld());
        if (fight.isEmpty() || !fight.get().isFightDragon(dragon)) {
            return;
        }
        PlayerFacts facts = PaperFacts.of(player, settings.get().freeTheEndCountsAsDone());
        NewcomerPolicy rules = policy.get();
        if (rules.canParticipate(facts)) {
            tracker.record(dragon.getWorld().getUID(),
                    new Participant(facts.id(), facts.name(), rules.countsAsNewcomerWhenParticipating(facts)));
        }
    }

    /**
     * Sets the experience a first-time kill drops. Paper decides the dragon's experience from this event
     * (vanilla's own choice of 12,000 or 500 is overridden by it), so this is the only place to change it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void setFirstTimeExperience(EntityDeathEvent event) {
        if (fightDragon(event).isPresent() && !newcomers(event.getEntity().getWorld(), false).isEmpty()) {
            event.setDroppedExp(Math.max(event.getDroppedExp(), FIRST_TIME_EXPERIENCE));
        }
    }

    /** Records the kill: participants have now fought; a first-time kill is announced and will get its egg. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordKill(EntityDeathEvent event) {
        Optional<Fight> fight = fightDragon(event);
        if (fight.isEmpty()) {
            return;
        }
        World world = event.getEntity().getWorld();
        List<Participant> participants = tracker.participants(world.getUID());
        List<Participant> newcomers = newcomers(world, true);

        Instant now = Instant.now();
        Map<UUID, PlayerStore.Entry> entries = new LinkedHashMap<>();
        participants.forEach(p -> entries.put(p.id(), new PlayerStore.Entry(p.name(), now, PlayerStore.Reason.KILL, world.getName())));
        store.markAllDone(entries);

        if (newcomers.isEmpty()) {
            eggsDue.remove(world.getUID());
            return;
        }
        eggsDue.put(world.getUID(), settings.get().eggPerNewcomer() ? newcomers.size() : 1);
        List<String> names = newcomers.stream().map(Participant::name).toList();
        log.info("First-time dragon kill in " + world.getName() + " for " + String.join(", ", names));
        new FirstTimeDragonKillEvent(world, (EnderDragon) event.getEntity(),
                participants.stream().map(Participant::id).toList(),
                newcomers.stream().map(Participant::id).toList()).callEvent();
        Broadcasts.send(settings.get().firstTimeKillMessage(), log, Broadcasts.newcomers(names), Broadcasts.world(world));
    }

    /**
     * End of the death animation. For a first-time kill the egg forms even though vanilla only gives one
     * for the world's first dragon; with egg-per-newcomer, more are stacked on it a tick later. If the egg's
     * spot is taken, the eggs are dropped there instead of replacing the block.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEggForm(DragonEggFormEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (fights.of(world).isEmpty()) {
            return;
        }
        Integer due = eggsDue.remove(world.getUID());
        if (due != null) {
            event.setCancelled(false);
        }
        int eggs = due != null ? due : 1;
        if (event.isCancelled()) {
            return;
        }
        if (!block.getType().isAir()) {
            event.setCancelled(true);
            dropEggs(block.getLocation().add(0.5, 1.2, 0.5), eggs);
        } else if (eggs > 1) {
            extraEggs.put(world.getUID(), eggs - 1);
        }
    }

    /** Only once no other plugin can cancel the egg any more: extra eggs go where the first one did. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void stackExtraEggs(DragonEggFormEvent event) {
        Integer extra = extraEggs.remove(event.getBlock().getWorld().getUID());
        if (extra != null && !event.isCancelled()) {
            Block egg = event.getBlock();
            Bukkit.getScheduler().runTask(plugin, () -> stackEggs(egg, extra));
        }
    }

    /**
     * Places eggs on top of the column that starts at {@code egg}. Blocks rather than items: the column
     * stands in the middle of the open exit portal, and an item rolling off it would be sent to the
     * Overworld. Whatever does not fit is dropped on top.
     */
    private void stackEggs(Block egg, int count) {
        Block above = egg.getRelative(BlockFace.UP);
        int placed = 0;
        while (placed < count && above.getY() < egg.getWorld().getMaxHeight()) {
            if (above.getType().isAir()) {
                above.setType(Material.DRAGON_EGG, false);
                placed++;
            } else if (above.getType() != Material.DRAGON_EGG) {
                break;
            }
            above = above.getRelative(BlockFace.UP);
        }
        if (placed < count) {
            dropEggs(above.getRelative(BlockFace.DOWN).getLocation().add(0.5, 1.2, 0.5), count - placed);
        }
    }

    private Optional<Fight> fightDragon(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return Optional.empty();
        }
        return fights.of(dragon.getWorld()).filter(fight -> fight.isFightDragon(dragon));
    }

    /** The participants for whom this kill is the first; with {@code forget}, the fight's record is cleared. */
    private List<Participant> newcomers(World world, boolean forget) {
        List<Participant> participants = forget ? tracker.take(world.getUID()) : tracker.participants(world.getUID());
        return FightTracker.newcomers(participants, store::isDone);
    }

    private void dropEggs(Location at, int count) {
        at.getWorld().dropItem(at, new ItemStack(Material.DRAGON_EGG, count), item -> {
            item.setVelocity(new Vector());
            item.setUnlimitedLifetime(true);
            item.setInvulnerable(true);
        });
        log.info("No room for " + count + " dragon egg(s) on the exit portal; dropped at "
                + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
    }
}
