package me.fernandossm.multiplayerdragonrebirth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.event.block.DragonEggFormEvent;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.fernandossm.multiplayerdragonrebirth.api.DragonSummonEvent;
import me.fernandossm.multiplayerdragonrebirth.api.FirstTimeDragonKillEvent;
import me.fernandossm.multiplayerdragonrebirth.store.PlayerStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class PluginTest {

    private static final int COUNTDOWN_TICKS = 10 * 20;

    private ServerMock server;
    private WorldMock overworld;
    private WorldMock end;
    private MultiplayerDragonRebirth plugin;
    private FakeFight fight;
    private final List<DragonSummonEvent> summonEvents = new ArrayList<>();
    private final List<FirstTimeDragonKillEvent> killEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("world");
        end = server.addSimpleWorld("world_the_end");
        plugin = MockBukkit.load(MultiplayerDragonRebirth.class);
        fight = new FakeFight(end);
        plugin.useFightsForTests(world -> world == end ? Optional.of(fight) : Optional.empty());
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void summon(DragonSummonEvent event) {
                summonEvents.add(event);
            }

            @EventHandler
            public void kill(FirstTimeDragonKillEvent event) {
                killEvents.add(event);
            }
        }, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock join(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setGameMode(GameMode.SURVIVAL);
        server.getScheduler().performTicks(25); // the join check runs a second after joining, in the overworld
        drainMessages(player);
        return player;
    }

    private void enterEnd(PlayerMock player) {
        World from = player.getWorld();
        player.setLocation(end.getSpawnLocation());
        server.getPluginManager().callEvent(new PlayerChangedWorldEvent(player, from));
    }

    private void leaveEnd(PlayerMock player) {
        player.setLocation(overworld.getSpawnLocation());
        server.getPluginManager().callEvent(new PlayerChangedWorldEvent(player, end));
    }

    private static List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(next));
        }
        return messages;
    }

    private void markDone(Player player) {
        plugin.store().markDone(player.getUniqueId(),
                new PlayerStore.Entry(player.getName(), Instant.now(), PlayerStore.Reason.ADMIN, null));
    }

    private void hit(Entity target, Player by) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(by).withDirectEntity(by).build();
        server.getPluginManager().callEvent(damage(target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source));
    }

    @SuppressWarnings({"deprecation", "removal"}) // the server's own constructor; fine for a synthetic event
    private static EntityDamageEvent damage(Entity target, EntityDamageEvent.DamageCause cause, DamageSource source) {
        return new EntityDamageEvent(target, cause, source, 5);
    }

    /** The killing blow, with the experience vanilla chose: 12,000 for a world's first dragon, else 500. */
    private EntityDeathEvent kill(EnderDragon dragon, int vanillaExperience) {
        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        EntityDeathEvent event = new EntityDeathEvent(dragon, source, new ArrayList<>(), vanillaExperience);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private DragonEggFormEvent formEgg(boolean cancelledByVanilla) {
        Block spot = end.getBlockAt(0, 65, 0);
        DragonEggFormEvent event = new DragonEggFormEvent(spot, spot.getState(), null);
        event.setCancelled(cancelledByVanilla);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Nested
    class Summoning {

        @Test
        void aNewcomerEnteringTheEndIsAnnouncedAndGetsADragonAfterTheCountdown() {
            PlayerMock alice = join("Alice");
            PlayerMock bystander = join("Bystander");
            enterEnd(alice);

            assertEquals(List.of("A dragon rises for Alice in 10 seconds."), drainMessages(bystander), "server-wide");
            server.getScheduler().performTicks(COUNTDOWN_TICKS - 1);
            assertEquals(0, fight.summons);
            server.getScheduler().performTicks(1);
            assertEquals(1, fight.summons);
            assertEquals(1, summonEvents.size());
            assertEquals(List.of(alice), summonEvents.getFirst().getNewcomers());
        }

        @Test
        void newcomersArrivingTogetherShareOneSummon() {
            PlayerMock alice = join("Alice");
            PlayerMock bob = join("Bob");
            enterEnd(alice);
            server.getScheduler().performTicks(60);
            enterEnd(bob);
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(1, fight.summons);
            assertEquals(Set.of(alice, bob), Set.copyOf(summonEvents.getFirst().getNewcomers()));
            assertEquals(1, drainMessages(alice).stream().filter(m -> m.startsWith("A dragon rises")).count());
        }

        @Test
        void noDragonIfEveryNewcomerLeftDuringTheCountdown() {
            PlayerMock alice = join("Alice");
            enterEnd(alice);
            server.getScheduler().performTicks(100);
            leaveEnd(alice);
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
            assertTrue(summonEvents.isEmpty());
        }

        @Test
        void aNewcomerWhoArrivedLaterKeepsTheSummonGoingWhenTheFirstLeaves() {
            PlayerMock alice = join("Alice");
            PlayerMock bob = join("Bob");
            enterEnd(alice);
            enterEnd(bob);
            leaveEnd(alice);
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(1, fight.summons);
            assertEquals(List.of(bob), summonEvents.getFirst().getNewcomers());
        }

        @Test
        void noCountdownWhileADragonIsAliveOrBeingSummoned() {
            fight.dragonAlive = true;
            PlayerMock alice = join("Alice");
            enterEnd(alice);
            server.getScheduler().performTicks(COUNTDOWN_TICKS * 2);

            assertEquals(0, fight.summons);
            assertTrue(drainMessages(alice).isEmpty());
        }

        @Test
        void noCountdownBeforeAFreshWorldsFirstDragonAppears() {
            fight.previouslyKilled = false; // vanilla is about to spawn the world's first dragon
            PlayerMock alice = join("Alice");
            enterEnd(alice);
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
            assertTrue(drainMessages(alice).isEmpty());
        }

        @Test
        void aDragonThatAppearsDuringTheCountdownCancelsTheSummon() {
            PlayerMock alice = join("Alice");
            enterEnd(alice);
            fight.dragonAlive = true; // players respawned it by hand meanwhile
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
        }

        @Test
        void veteransDoNotSummon() {
            PlayerMock veteran = join("Veteran");
            markDone(veteran);
            enterEnd(veteran);
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
            assertTrue(drainMessages(veteran).isEmpty());
        }

        @Test
        void creativeExemptAndVanishedPlayersDoNotSummon() {
            PlayerMock creative = join("Creative");
            creative.setGameMode(GameMode.CREATIVE);
            PlayerMock spectator = join("Spectator");
            spectator.setGameMode(GameMode.SPECTATOR);
            PlayerMock staff = join("Staff");
            staff.addAttachment(plugin, "multiplayerdragonrebirth.exempt", true);
            PlayerMock ghost = join("Ghost");
            ghost.setMetadata("vanished", new FixedMetadataValue(plugin, true));

            for (PlayerMock p : List.of(creative, spectator, staff, ghost)) {
                enterEnd(p);
            }
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
        }

        @Test
        void adventureModePlayersSummon() {
            PlayerMock alice = join("Alice");
            alice.setGameMode(GameMode.ADVENTURE);
            enterEnd(alice);
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(1, fight.summons);
        }

        @Test
        void loggingInInsideTheEndSummonsAfterTheJoinDelay() {
            PlayerMock carl = new PlayerMock(server, "Carl");
            carl.setLocation(end.getSpawnLocation());
            server.addPlayer(carl);
            server.getScheduler().performTicks(20 + COUNTDOWN_TICKS);

            assertEquals(1, fight.summons);
        }

        @Test
        void loggingInInsideTheEndDoesNothingWhenTriggerOnJoinIsOff() {
            plugin.getConfig().set("trigger-on-join", false);
            plugin.saveConfig();
            plugin.reloadSettings();
            PlayerMock carl = new PlayerMock(server, "Carl");
            carl.setLocation(end.getSpawnLocation());
            server.addPlayer(carl);
            server.getScheduler().performTicks(20 + COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
        }

        @Test
        void otherPluginsCanCancelTheSummon() {
            server.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void cancel(DragonSummonEvent event) {
                    event.setCancelled(true);
                }
            }, plugin);
            enterEnd(join("Alice"));
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
        }

        @Test
        void unmanagedWorldsAreIgnored() {
            WorldMock otherEnd = server.addSimpleWorld("other_end");
            PlayerMock alice = join("Alice");
            alice.setLocation(otherEnd.getSpawnLocation());
            server.getPluginManager().callEvent(new PlayerChangedWorldEvent(alice, overworld));
            server.getScheduler().performTicks(COUNTDOWN_TICKS);

            assertEquals(0, fight.summons);
            assertTrue(drainMessages(alice).isEmpty());
        }

        @Test
        void anEmptyCountdownMessageIsNotSent() {
            plugin.getConfig().set("messages.countdown", "");
            plugin.saveConfig();
            plugin.reloadSettings();
            PlayerMock alice = join("Alice");
            enterEnd(alice);

            assertTrue(drainMessages(alice).isEmpty());
            server.getScheduler().performTicks(COUNTDOWN_TICKS);
            assertEquals(1, fight.summons);
        }
    }

    @Nested
    class Kills {

        @Test
        void aKillWithANewcomerIsFirstTime() {
            PlayerMock veteran = join("Veteran");
            markDone(veteran);
            PlayerMock alice = join("Alice");
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, veteran);
            hit(dragon, alice);
            EntityDeathEvent death = kill(dragon, 500);

            assertEquals(12_000, death.getDroppedExp(), "Paper drops what the death event says");
            assertEquals(1, killEvents.size());
            assertEquals(List.of(alice.getUniqueId()), killEvents.getFirst().getNewcomers());
            assertEquals(List.of(veteran.getUniqueId(), alice.getUniqueId()), killEvents.getFirst().getParticipants());
            assertEquals(PlayerStore.Reason.KILL, plugin.store().get(alice.getUniqueId()).orElseThrow().reason());
            assertEquals("world_the_end", plugin.store().get(alice.getUniqueId()).orElseThrow().world());
            assertEquals(PlayerStore.Reason.ADMIN, plugin.store().get(veteran.getUniqueId()).orElseThrow().reason(), "keeps the first entry");
            assertTrue(drainMessages(veteran).contains("The dragon has fallen! Its egg and power await."));
        }

        @Test
        void aKillByVeteransOnlyIsNotFirstTime() {
            PlayerMock veteran = join("Veteran");
            markDone(veteran);
            PlayerMock watcher = join("Watcher"); // a newcomer who never hit it
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, veteran);
            EntityDeathEvent death = kill(dragon, 500);

            assertEquals(500, death.getDroppedExp());
            assertTrue(killEvents.isEmpty());
            assertFalse(plugin.store().isDone(watcher.getUniqueId()), "watching does not count");
            assertFalse(drainMessages(veteran).contains("The dragon has fallen! Its egg and power await."));
            assertTrue(formEgg(true).isCancelled(), "no egg after a kill that is not first-time");
        }

        @Test
        void veteransKillingAWorldsFirstDragonKeepVanillasRewards() {
            PlayerMock veteran = join("Veteran");
            markDone(veteran);
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, veteran);
            EntityDeathEvent death = kill(dragon, 12_000); // what vanilla gives the world's first dragon

            assertEquals(12_000, death.getDroppedExp(), "never less than vanilla");
            assertFalse(formEgg(false).isCancelled(), "vanilla's own first egg is left alone");
        }

        @Test
        void moreExperienceSetByAnotherPluginIsKept() {
            PlayerMock alice = join("Alice");
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, alice);
            assertEquals(50_000, kill(dragon, 50_000).getDroppedExp());
        }

        @Test
        void aDeathCancelledByAnotherPluginRecordsNothing() {
            server.getPluginManager().registerEvents(new Listener() {
                @EventHandler(priority = EventPriority.HIGH)
                public void cancel(EntityDeathEvent event) {
                    event.setCancelled(true);
                }
            }, plugin);
            PlayerMock alice = join("Alice");
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, alice);
            kill(dragon, 500);

            assertTrue(killEvents.isEmpty());
            assertFalse(plugin.store().isDone(alice.getUniqueId()));
        }

        @Test
        void damageFromCreativeVanishedOrExemptPlayersDoesNotMakeAKillFirstTime() {
            PlayerMock creative = join("Creative");
            creative.setGameMode(GameMode.CREATIVE);
            PlayerMock ghost = join("Ghost");
            ghost.setMetadata("vanished", new FixedMetadataValue(plugin, true));
            PlayerMock staff = join("Staff");
            staff.addAttachment(plugin, "multiplayerdragonrebirth.exempt", true);
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, creative);
            hit(dragon, ghost);
            hit(dragon, staff);
            EntityDeathEvent death = kill(dragon, 500);

            assertEquals(500, death.getDroppedExp());
            assertTrue(killEvents.isEmpty());
            assertFalse(plugin.store().isDone(creative.getUniqueId()));
            assertFalse(plugin.store().isDone(ghost.getUniqueId()));
            assertTrue(plugin.store().isDone(staff.getUniqueId()), "exempt staff who fought are recorded");
        }

        @Test
        void indirectDamageCountsByWhoCausedIt() {
            PlayerMock archer = join("Archer");
            EnderDragon dragon = fight.spawnDragon();
            DamageSource arrow = DamageSource.builder(DamageType.ARROW).withCausingEntity(archer).withDirectEntity(dragon).build();
            server.getPluginManager().callEvent(damage(dragon, EntityDamageEvent.DamageCause.PROJECTILE, arrow));
            kill(dragon, 500);

            assertEquals(List.of(archer.getUniqueId()), killEvents.getFirst().getNewcomers());
        }

        @Test
        void dragonsOutsideTheFightAreIgnored() {
            PlayerMock alice = join("Alice");
            fight.spawnDragon();
            EnderDragon summoned = FakeFight.dragon(end);
            hit(summoned, alice);
            EntityDeathEvent death = kill(summoned, 500);

            assertEquals(500, death.getDroppedExp());
            assertTrue(killEvents.isEmpty());
            assertFalse(plugin.store().isDone(alice.getUniqueId()));
        }

        @Test
        void participantsAreForgottenAfterEachKill() {
            PlayerMock alice = join("Alice");
            EnderDragon first = fight.spawnDragon();
            hit(first, alice);
            kill(first, 500);

            PlayerMock bob = join("Bob");
            markDone(bob);
            EnderDragon second = fight.spawnDragon();
            hit(second, bob);
            EntityDeathEvent death = kill(second, 500);

            assertEquals(1, killEvents.size(), "Alice's damage does not carry over to the second dragon");
            assertEquals(500, death.getDroppedExp());
        }

        @Test
        void theEggFormsForAFirstTimeKillEvenIfVanillaWouldNot() {
            PlayerMock alice = join("Alice");
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, alice);
            kill(dragon, 500);

            assertFalse(formEgg(true).isCancelled());
            assertTrue(formEgg(true).isCancelled(), "only one egg per first-time kill");
        }

        private void eggPerNewcomer(boolean on) {
            plugin.getConfig().set("egg-per-newcomer", on);
            plugin.saveConfig();
            plugin.reloadSettings();
        }

        /** Two newcomers and a veteran damage the dragon, which then dies. */
        private void killWithTwoNewcomers() {
            PlayerMock veteran = join("Veteran");
            markDone(veteran);
            PlayerMock alice = join("Alice");
            PlayerMock bob = join("Bob");
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, veteran);
            hit(dragon, alice);
            hit(dragon, bob);
            kill(dragon, 500);
        }

        @Test
        void eggPerNewcomerStacksOneMoreEggPerExtraNewcomer() {
            eggPerNewcomer(true);
            killWithTwoNewcomers();
            DragonEggFormEvent egg = formEgg(true); // forms at y 65; MockBukkit does not place it itself
            server.getScheduler().performTicks(1);

            assertFalse(egg.isCancelled());
            assertEquals(Material.DRAGON_EGG, end.getBlockAt(0, 66, 0).getType(), "the second newcomer's egg");
            assertEquals(Material.AIR, end.getBlockAt(0, 67, 0).getType(), "two newcomers, two eggs; the veteran gets none");
        }

        @Test
        void oneEggPerKillByDefault() {
            killWithTwoNewcomers();
            formEgg(true);
            server.getScheduler().performTicks(1);

            assertEquals(Material.AIR, end.getBlockAt(0, 66, 0).getType());
        }

        @Test
        void extraEggsGoAboveEggsFromEarlierKills() {
            eggPerNewcomer(true);
            end.getBlockAt(0, 66, 0).setType(Material.DRAGON_EGG);
            killWithTwoNewcomers();
            formEgg(true);
            server.getScheduler().performTicks(1);

            assertEquals(Material.DRAGON_EGG, end.getBlockAt(0, 67, 0).getType());
            assertEquals(Material.AIR, end.getBlockAt(0, 68, 0).getType());
        }

        @Test
        void noExtraEggsWhenAnotherPluginCancelsTheEgg() {
            eggPerNewcomer(true);
            server.getPluginManager().registerEvents(new Listener() {
                @EventHandler(priority = EventPriority.HIGHEST)
                public void cancel(DragonEggFormEvent event) {
                    event.setCancelled(true);
                }
            }, plugin);
            killWithTwoNewcomers();
            formEgg(true);
            server.getScheduler().performTicks(1);

            assertEquals(Material.AIR, end.getBlockAt(0, 66, 0).getType());
        }

        @Test
        void anEggWhoseSpotIsTakenIsDroppedInstead() {
            PlayerMock alice = join("Alice");
            EnderDragon dragon = fight.spawnDragon();
            hit(dragon, alice);
            kill(dragon, 500);
            end.getBlockAt(0, 65, 0).setType(Material.DRAGON_EGG);
            DragonEggFormEvent egg = formEgg(false);

            assertTrue(egg.isCancelled());
            assertEquals(Material.DRAGON_EGG, end.getBlockAt(0, 65, 0).getType(), "the egg already there is untouched");
            List<Item> dropped = end.getEntitiesByClass(Item.class).stream()
                    .filter(i -> i.getItemStack().getType() == Material.DRAGON_EGG).toList();
            assertEquals(1, dropped.size());
            assertTrue(dropped.getFirst().isUnlimitedLifetime(), "a dropped egg never despawns");
        }
    }

    @Nested
    class Commands {

        private PlayerMock admin;

        @BeforeEach
        void admin() {
            admin = join("Admin");
            admin.setOp(true);
        }

        private List<String> run(String command) {
            drainMessages(admin);
            assertTrue(server.dispatchCommand(admin, command), command);
            return drainMessages(admin);
        }

        @Test
        void checkMarkAndReset() {
            PlayerMock alice = join("Alice");
            assertEquals(List.of("Alice is a newcomer."), run("mdr check Alice"));
            assertEquals(List.of("Alice now counts as having fought the dragon."), run("mdr mark Alice"));
            assertTrue(plugin.store().isDone(alice.getUniqueId()));
            assertTrue(run("mdr check Alice").getFirst().startsWith("Alice has fought the dragon: admin"));
            assertEquals(List.of("Alice had already fought the dragon."), run("mdr mark alice"));
            assertEquals(List.of("Alice is a newcomer again."), run("mdr reset Alice"));
            assertFalse(plugin.store().isDone(alice.getUniqueId()));
            assertEquals(List.of("No player named Nobody has joined this server."), run("mdr check Nobody"));
        }

        @Test
        void offlinePlayersCanBeManaged() {
            PlayerMock alice = join("Alice");
            markDone(alice);
            alice.disconnect();
            assertTrue(run("mdr check Alice").getFirst().startsWith("Alice has fought the dragon"));
            assertEquals(List.of("Alice is a newcomer again."), run("mdr reset Alice"));
        }

        @Test
        void resetAllNeedsConfirmation() {
            markDone(join("Alice"));
            markDone(join("Bob"));
            assertTrue(run("mdr reset-all").getFirst().contains("Confirm with /mdr reset-all confirm"));
            assertEquals(2, plugin.store().size());
            assertEquals(List.of("2 players are newcomers again."), run("mdr reset-all confirm"));
            assertEquals(0, plugin.store().size());
        }

        @Test
        void markExistingRecordsEveryoneWhoJoined() {
            join("Alice");
            join("Bob").disconnect();
            List<String> reply = run("mdr mark-existing");
            assertEquals(List.of("3 more players who have joined the server now count as having fought the dragon."), reply);
            assertEquals(PlayerStore.Reason.EXISTING_PLAYER, plugin.store().get(admin.getUniqueId()).orElseThrow().reason());
        }

        @Test
        void summonInTheCurrentWorldOrANamedOne() {
            admin.setLocation(end.getSpawnLocation());
            assertEquals(List.of("Summoning the dragon in world_the_end."), run("mdr summon"));
            assertEquals(1, fight.summons);
            assertEquals(List.of("There is already a dragon in world_the_end, or one is being summoned."), run("mdr summon world_the_end"));
            assertEquals(List.of("world is not a managed End world."), run("mdr summon world"));
        }

        @Test
        void aRefusedSummonIsReported() {
            fight.refuseSummon = true;
            assertTrue(run("mdr summon world_the_end").getFirst().startsWith("The server refused to summon a dragon"));
        }

        @Test
        void statusDescribesTheFight() {
            PlayerMock alice = join("Alice");
            enterEnd(alice);
            List<String> status = run("mdr status");
            assertTrue(status.getFirst().startsWith("world_the_end: fake fight"), status::toString);
            assertTrue(status.get(1).contains("countdown") && status.get(1).contains("Alice"), status::toString);
        }

        @Test
        void playersWithoutPermissionCannotUseIt() {
            PlayerMock alice = join("Alice");
            server.dispatchCommand(alice, "mdr mark Alice");
            assertFalse(plugin.store().isDone(alice.getUniqueId()));
        }

        @Test
        void reloadAppliesConfigChanges() throws Exception {
            Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "countdown-seconds: 2\n");
            assertEquals(List.of("Reloaded config.yml."), run("mdr reload"));
            assertEquals(2, plugin.settings().countdownSeconds());
        }
    }

    @Nested
    class Startup {

        @Test
        void anUnreadableStoreDisablesThePlugin() throws Exception {
            server.getPluginManager().disablePlugin(plugin);
            Files.writeString(plugin.getDataFolder().toPath().resolve("players.json"), "{ broken");
            server.getPluginManager().enablePlugin(plugin);

            assertFalse(plugin.isEnabled());
            assertEquals("{ broken", Files.readString(plugin.getDataFolder().toPath().resolve("players.json")), "left as it was");
        }

        @Test
        void aRestartKeepsTheRecords() {
            PlayerMock alice = join("Alice");
            markDone(alice);
            server.getPluginManager().disablePlugin(plugin);
            server.getPluginManager().enablePlugin(plugin);

            assertTrue(plugin.isEnabled());
            assertTrue(plugin.store().isDone(alice.getUniqueId()));
            assertFalse(plugin.store().created());
        }
    }

    /** First runs need players to exist before the plugin loads, so they use their own server. */
    @Nested
    class FirstRun {

        @BeforeEach
        void freshServer() {
            MockBukkit.unmock();
            server = MockBukkit.mock();
            server.addSimpleWorld("world");
        }

        @Test
        void markExistingRecordsEveryoneWhoHasJoined() {
            PlayerMock old = server.addPlayer("Old");
            old.disconnect();
            MultiplayerDragonRebirth fresh = MockBukkit.load(MultiplayerDragonRebirth.class);

            assertEquals(PlayerStore.Reason.EXISTING_PLAYER, fresh.store().get(old.getUniqueId()).orElseThrow().reason());
            assertFalse(fresh.store().isDone(server.addPlayer("New").getUniqueId()));
        }

        @Test
        void noneLeavesEveryoneANewcomer() {
            PlayerMock old = server.addPlayer("Old");
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            config.set("first-run", "NONE");
            MultiplayerDragonRebirth fresh = MockBukkit.loadWithConfig(MultiplayerDragonRebirth.class, config);

            assertFalse(fresh.store().isDone(old.getUniqueId()));
            assertTrue(Files.exists(fresh.getDataFolder().toPath().resolve("players.json")), "written so the next start is not a first run");
        }

        @Test
        void theFirstRunHappensOnlyOnce() {
            MultiplayerDragonRebirth fresh = MockBukkit.load(MultiplayerDragonRebirth.class);
            PlayerMock later = server.addPlayer("Later");
            server.getPluginManager().disablePlugin(fresh);
            server.getPluginManager().enablePlugin(fresh);

            assertFalse(fresh.store().isDone(later.getUniqueId()), "a player new after the first run stays a newcomer");
            assertNull(fresh.store().get(later.getUniqueId()).orElse(null));
            assertNotNull(fresh.store());
        }
    }
}
