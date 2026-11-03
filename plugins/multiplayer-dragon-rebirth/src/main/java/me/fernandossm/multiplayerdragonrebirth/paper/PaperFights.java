package me.fernandossm.multiplayerdragonrebirth.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import me.fernandossm.multiplayerdragonrebirth.config.Settings;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** {@link Fights} backed by Paper's {@link DragonBattle}. */
public final class PaperFights implements Fights {

    /**
     * Where the crystals stand relative to {@link DragonBattle#getEndPortalLocation()}, which is at the
     * height of the portal's bedrock rim: on the rim, three blocks out in each direction, where players
     * place them to respawn the dragon by hand.
     */
    private static final int[][] CRYSTAL_OFFSETS = {{3, 1, 0}, {-3, 1, 0}, {0, 1, 3}, {0, 1, -3}};

    private final Supplier<Settings> settings;
    private final NamespacedKey crystalKey;

    public PaperFights(Supplier<Settings> settings, NamespacedKey crystalKey) {
        this.settings = settings;
        this.crystalKey = crystalKey;
    }

    @Override
    public Optional<Fight> of(World world) {
        if (world.getEnvironment() != World.Environment.THE_END || !settings.get().manages(world.getName())) {
            return Optional.empty();
        }
        DragonBattle battle = world.getEnderDragonBattle();
        return battle == null ? Optional.empty() : Optional.of(new PaperFight(world, battle));
    }

    /** Crystals the plugin placed on the exit portal; they must not be destroyed while summoning. */
    public boolean isSummoningCrystal(Entity entity) {
        return entity instanceof EnderCrystal && entity.getPersistentDataContainer().has(crystalKey);
    }

    private final class PaperFight implements Fight {

        private final World world;
        private final DragonBattle battle;

        PaperFight(World world, DragonBattle battle) {
            this.world = world;
            this.battle = battle;
        }

        @Override
        public World world() {
            return world;
        }

        @Override
        public boolean dragonAlive() {
            return battle.getEnderDragon() != null;
        }

        @Override
        public boolean summoning() {
            return battle.getRespawnPhase() != DragonBattle.RespawnPhase.NONE;
        }

        @Override
        public boolean isFightDragon(Entity entity) {
            EnderDragon dragon = battle.getEnderDragon();
            return dragon != null && dragon.getUniqueId().equals(entity.getUniqueId());
        }

        @Override
        public boolean summon() {
            removeLeftoverCrystals();
            if (battle.getEndPortalLocation() == null) {
                battle.generateEndPortal(false);
            }
            Location portal = battle.getEndPortalLocation();
            if (portal == null) {
                return false;
            }
            // The sequence needs its crystals: vanilla aborts it on the first tick in which none is alive,
            // so they are invulnerable (and their damage is cancelled, for players in Creative mode).
            List<EnderCrystal> crystals = new ArrayList<>();
            for (int[] offset : CRYSTAL_OFFSETS) {
                Location at = portal.clone().add(offset[0] + 0.5, offset[1], offset[2] + 0.5);
                crystals.add(world.spawn(at, EnderCrystal.class, crystal -> {
                    crystal.setShowingBottom(false);
                    crystal.setInvulnerable(true);
                    crystal.setPersistent(true);
                    crystal.getPersistentDataContainer().set(crystalKey, PersistentDataType.BOOLEAN, true);
                }));
            }
            boolean started = battle.initiateRespawn(crystals);
            if (!started) {
                crystals.forEach(Entity::remove);
            }
            return started;
        }

        /** Crystals left by a summoning that was interrupted, for example by a restart. */
        private void removeLeftoverCrystals() {
            for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
                if (isSummoningCrystal(crystal)) {
                    crystal.remove();
                }
            }
        }

        @Override
        public boolean previouslyKilled() {
            return battle.hasBeenPreviouslyKilled();
        }

        @Override
        public String describe() {
            return "dragon " + (battle.getEnderDragon() != null ? "alive" : "absent")
                    + ", summoning phase " + battle.getRespawnPhase()
                    + ", previously killed " + battle.hasBeenPreviouslyKilled()
                    + ", gateways " + battle.getGatewayCount() + "/20";
        }
    }
}
