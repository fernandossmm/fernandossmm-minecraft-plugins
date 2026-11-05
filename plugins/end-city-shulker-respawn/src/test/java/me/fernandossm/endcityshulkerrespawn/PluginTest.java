package me.fernandossm.endcityshulkerrespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import me.fernandossm.endcityshulkerrespawn.FakeCities.FakeCity;
import me.fernandossm.endcityshulkerrespawn.api.CityShulkerRespawnEvent;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.core.Spot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class PluginTest {

    private static final long COOLDOWN = Duration.ofHours(16).toMillis();

    private ServerMock server;
    private WorldMock end;
    private EndCityShulkerRespawn plugin;
    private final FakeCities cities = new FakeCities();
    private FakeCity city;
    private final Set<Location> claimed = new HashSet<>();
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final List<Shulker> keptForever = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setDifficulty(Difficulty.NORMAL);
        plugin = MockBukkit.load(EndCityShulkerRespawn.class);
        // Chunks 0..1 by 0..0, from y 60 to 100.
        city = cities.add(end, new BoundingBox(0, 60, 0, 31, 100, 15));
        plugin.useForTests(cities, location -> claimed.contains(location.toBlockLocation()), clock::get, keptForever::add);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A shulker attached to the block below it, which is made solid, as in a real city. */
    private Shulker shulker(int x, int y, int z) {
        end.getBlockAt(x, y - 1, z).setType(Material.PURPUR_BLOCK);
        return end.spawn(new Location(end, x + 0.5, y, z + 0.5), Shulker.class, s -> s.setAttachedFace(BlockFace.DOWN));
    }

    private List<Shulker> shulkersIn(int chunkX, int chunkZ) {
        return end.getEntitiesByClass(Shulker.class).stream()
                .filter(s -> s.isValid() && s.getLocation().getBlockX() >> 4 == chunkX && s.getLocation().getBlockZ() >> 4 == chunkZ)
                .toList();
    }

    /** The chunk's entities load, as when a player arrives; the refill runs on the next tick. */
    private void load(int chunkX, int chunkZ) {
        List<Entity> entities = new ArrayList<>(shulkersIn(chunkX, chunkZ));
        server.getPluginManager().callEvent(new EntitiesLoadEvent(end.getChunkAt(chunkX, chunkZ), entities));
        server.getScheduler().performTicks(1);
    }

    private void kill(Shulker shulker) {
        server.getPluginManager().callEvent(new EntityRemoveEvent(shulker, EntityRemoveEvent.Cause.DEATH));
        shulker.remove();
    }

    private CityData data() {
        return city.load();
    }

    private Spot spotAt(int x) {
        return data().spots().stream().filter(s -> s.x() == x).findFirst().orElseThrow();
    }

    @Nested
    class Learning {

        @Test
        void theFirstLoadLearnsTheCitysShulkersInThatChunk() {
            shulker(4, 70, 4);
            shulker(8, 80, 8);
            shulker(9, 110, 9); // above the city
            load(0, 0);

            assertEquals(2, data().spots().size());
            assertTrue(data().learned(0, 0));
            Spot spot = spotAt(8);
            assertEquals(List.of(8, 80, 8), List.of(spot.x(), spot.y(), spot.z()));
            assertEquals(Spot.Face.DOWN, spot.face());
            assertTrue(data().spots().stream().allMatch(Spot::alive));
        }

        @Test
        void laterLoadsLearnNothingMore() {
            shulker(4, 70, 4);
            load(0, 0);
            shulker(5, 70, 5); // bred by a player afterwards
            load(0, 0);

            assertEquals(1, data().spots().size());
        }

        @Test
        void eachChunkIsLearnedWhenItLoads() {
            shulker(4, 70, 4);
            shulker(20, 70, 4); // chunk 1,0
            load(0, 0);
            assertEquals(1, data().spots().size());
            load(1, 0);
            assertEquals(2, data().spots().size());
        }

        @Test
        void chunksOutsideCitiesAreIgnored() {
            shulker(40, 70, 4); // chunk 2,0
            load(2, 0);
            assertTrue(data().spots().isEmpty());
        }
    }

    @Nested
    class Settling {

        @Test
        void aSpotFollowsItsShulkerToWhereItSettled() {
            Shulker s = shulker(4, 70, 4);
            load(0, 0);
            end.getBlockAt(6, 72, 4).setType(Material.PURPUR_BLOCK);
            s.teleport(new Location(end, 5.5, 72, 4.5));
            s.setAttachedFace(BlockFace.EAST);
            load(0, 0); // seen again, as when the chunk loads on a later visit

            Spot spot = data().spots().getFirst();
            assertEquals(List.of(5, 72, 4), List.of(spot.x(), spot.y(), spot.z()));
            assertEquals(Spot.Face.EAST, spot.face());
            assertTrue(spot.alive());
        }

        @Test
        void aShulkerCarriedFarOrOutOfTheCityLeavesItsSpotAlone() {
            Shulker far = shulker(4, 70, 4);
            Shulker out = shulker(8, 70, 8);
            load(0, 0);
            far.teleport(new Location(end, 4.5, 90, 4.5));    // 20 blocks up, still in the city
            out.teleport(new Location(end, 8.5, 70, 20.5));   // outside the city's box, in chunk 0,1
            load(0, 0);
            load(0, 1);

            assertEquals(70, spotAt(4).y());
            assertEquals(8, spotAt(8).z());
        }
    }

    @Nested
    class Deaths {

        @Test
        void aDeathStartsTheSpotsCooldown() {
            Shulker s = shulker(4, 70, 4);
            load(0, 0);
            kill(s);

            assertEquals(clock.get() + COOLDOWN, data().spots().getFirst().dueAt());
        }

        @Test
        void anyRemovalButUnloadingCounts() {
            Shulker unloaded = shulker(4, 70, 4);
            Shulker discarded = shulker(6, 70, 4);
            load(0, 0);
            server.getPluginManager().callEvent(new EntityRemoveEvent(unloaded, EntityRemoveEvent.Cause.UNLOAD));
            server.getPluginManager().callEvent(new EntityRemoveEvent(discarded, EntityRemoveEvent.Cause.DISCARD));

            assertTrue(spotAt(4).alive());
            assertFalse(spotAt(6).alive());
        }

        @Test
        void bredShulkersBelongToNoSpot() {
            shulker(4, 70, 4);
            load(0, 0);
            kill(shulker(5, 70, 5));

            assertTrue(data().spots().getFirst().alive());
        }
    }

    @Nested
    class Refilling {

        private Shulker original;

        @BeforeEach
        void cityWithOneDeadShulker() {
            original = shulker(4, 70, 4);
            original.setColor(DyeColor.BLUE);
            load(0, 0);
            kill(original);
        }

        @Test
        void theSpotRefillsOnTheFirstLoadAfterTheCooldown() {
            clock.addAndGet(COOLDOWN - 1);
            load(0, 0);
            assertTrue(shulkersIn(0, 0).isEmpty(), "not before the cooldown is over");

            clock.addAndGet(1);
            load(0, 0);
            List<Shulker> back = shulkersIn(0, 0);
            assertEquals(1, back.size());
            Shulker s = back.getFirst();
            assertEquals(new Location(end, 4.5, 70, 4.5), s.getLocation());
            assertEquals(BlockFace.DOWN, s.getAttachedFace());
            assertEquals(null, s.getColor(), "the default purple, whatever colour the one before had");
            assertEquals(List.of(s), keptForever, "never despawns, like the city's own");
            assertTrue(data().spots().getFirst().alive());
        }

        @Test
        void theNewShulkerOwnsTheSpotAgain() {
            clock.addAndGet(COOLDOWN);
            load(0, 0);
            kill(shulkersIn(0, 0).getFirst());

            assertFalse(data().spots().getFirst().alive(), "its death starts a new cooldown");
        }

        @Test
        void neverMoreThanTheCityHad() {
            clock.addAndGet(COOLDOWN);
            load(0, 0);
            clock.addAndGet(COOLDOWN * 10);
            load(0, 0);

            assertEquals(1, shulkersIn(0, 0).size());
        }

        @Test
        void onlyTheLoadingChunksSpotsRefill() {
            Shulker other = shulker(20, 70, 4); // chunk 1,0
            load(1, 0);
            kill(other);
            clock.addAndGet(COOLDOWN);
            load(0, 0);

            assertEquals(1, shulkersIn(0, 0).size());
            assertTrue(shulkersIn(1, 0).isEmpty());
        }

        @Test
        void aBlockedSpotWaitsForTheNextLoad() {
            clock.addAndGet(COOLDOWN);
            end.getBlockAt(4, 70, 4).setType(Material.STONE);
            load(0, 0);
            assertTrue(shulkersIn(0, 0).isEmpty());
            assertFalse(data().spots().getFirst().alive());

            end.getBlockAt(4, 70, 4).setType(Material.AIR);
            load(0, 0);
            assertEquals(1, shulkersIn(0, 0).size());
        }

        @Test
        void aSpotWithNothingSolidAroundItWaitsUntilThereIs() {
            clock.addAndGet(COOLDOWN);
            end.getBlockAt(4, 69, 4).setType(Material.AIR);
            load(0, 0);
            assertTrue(shulkersIn(0, 0).isEmpty(), "vanilla would teleport it away, and the spot would follow it");
            assertFalse(data().spots().getFirst().alive(), "still due");

            end.getBlockAt(4, 69, 4).setType(Material.PURPUR_BLOCK);
            load(0, 0);
            assertEquals(1, shulkersIn(0, 0).size(), "back once there is something to stick to");
        }

        @Test
        void ifTheLearnedFaceIsGoneTheShulkerSticksToAnotherSolidFace() {
            clock.addAndGet(COOLDOWN);
            end.getBlockAt(4, 69, 4).setType(Material.AIR);
            end.getBlockAt(5, 70, 4).setType(Material.END_STONE_BRICKS);
            load(0, 0);
            assertEquals(BlockFace.EAST, shulkersIn(0, 0).getFirst().getAttachedFace());
        }

        @Test
        void aShulkerAlreadyOnTheSpotBlocksIt() {
            clock.addAndGet(COOLDOWN);
            shulker(4, 70, 4); // a bred shulker moved there
            load(0, 0);
            assertEquals(1, shulkersIn(0, 0).size());
            assertFalse(data().spots().getFirst().alive());
        }

        @Test
        void aShulkerInTheBlockBelowDoesNotTakeTheSpot() {
            clock.addAndGet(COOLDOWN);
            end.getBlockAt(4, 69, 4).setType(Material.AIR);
            end.getBlockAt(4, 68, 4).setType(Material.PURPUR_BLOCK);
            end.spawn(new Location(end, 4.5, 69, 4.5), Shulker.class); // a neighbour spot's shulker, right below
            end.getBlockAt(5, 70, 4).setType(Material.PURPUR_BLOCK);
            load(0, 0);
            assertEquals(2, shulkersIn(0, 0).size());
            assertTrue(data().spots().getFirst().alive());
        }

        @Test
        void claimedSpotsStayEmpty() {
            clock.addAndGet(COOLDOWN);
            claimed.add(new Location(end, 4, 70, 4));
            load(0, 0);
            assertTrue(shulkersIn(0, 0).isEmpty());
        }

        @Test
        void claimsCanBeIgnoredInTheConfig() {
            plugin.getConfig().set("respect-griefprevention-claims", false);
            plugin.saveConfig();
            plugin.reloadSettings();
            clock.addAndGet(COOLDOWN);
            claimed.add(new Location(end, 4, 70, 4));
            load(0, 0);
            assertEquals(1, shulkersIn(0, 0).size());
        }

        @Test
        void nothingRefillsOnPeaceful() {
            clock.addAndGet(COOLDOWN);
            end.setDifficulty(Difficulty.PEACEFUL);
            load(0, 0);
            assertTrue(shulkersIn(0, 0).isEmpty());
        }

        @Test
        void otherPluginsCanCancelARefill() {
            server.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void cancel(CityShulkerRespawnEvent event) {
                    event.setCancelled(true);
                }
            }, plugin);
            clock.addAndGet(COOLDOWN);
            load(0, 0);
            assertTrue(shulkersIn(0, 0).isEmpty());
        }

        @Test
        void otherPluginsCanColourTheNewShulker() {
            server.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void colour(CityShulkerRespawnEvent event) {
                    event.setColor(DyeColor.LIME);
                }
            }, plugin);
            clock.addAndGet(COOLDOWN);
            load(0, 0);
            assertEquals(DyeColor.LIME, shulkersIn(0, 0).getFirst().getColor());
        }

        @Test
        void theCooldownIsConfigurable() {
            plugin.getConfig().set("cooldown", "30m");
            plugin.saveConfig();
            plugin.reloadSettings();
            clock.addAndGet(COOLDOWN);
            load(0, 0);
            Shulker back = shulkersIn(0, 0).getFirst();
            kill(back);
            assertEquals(clock.get() + Duration.ofMinutes(30).toMillis(), data().spots().getFirst().dueAt());
        }
    }

    @Nested
    class Commands {

        private PlayerMock admin;

        @BeforeEach
        void adminInTheCity() {
            admin = server.addPlayer("Admin");
            admin.setOp(true);
            admin.setLocation(new Location(end, 10, 75, 5));
        }

        private List<String> run(String command) {
            drain();
            assertTrue(server.dispatchCommand(admin, command), command);
            return drain();
        }

        private List<String> drain() {
            List<String> messages = new ArrayList<>();
            Component next;
            while ((next = admin.nextComponentMessage()) != null) {
                messages.add(PlainTextComponentSerializer.plainText().serialize(next));
            }
            return messages;
        }

        @Test
        void statusDescribesTheCity() {
            shulker(4, 70, 4);
            Shulker dead = shulker(6, 70, 4);
            load(0, 0);
            kill(dead);
            claimed.add(new Location(end, 4, 70, 4));

            List<String> status = run("ecsr status");
            assertEquals("End city at 0,60,0: 2 spots, learned from 1 chunks; 1 alive, 0 ready to come back, 1 cooling down (next in 16h).", status.get(0));
            assertEquals("1 spots are inside a land claim and stay empty.", status.get(1));
        }

        @Test
        void refillMakesDeadSpotsDueNow() {
            Shulker dead = shulker(4, 70, 4);
            load(0, 0);
            kill(dead);

            assertEquals(List.of("1 shulkers will come back the next time their chunks load."), run("ecsr refill"));
            load(0, 0);
            assertEquals(1, shulkersIn(0, 0).size());
        }

        @Test
        void learnStartsOverFromTheShulkersThere() {
            Shulker gone = shulker(4, 70, 4);
            load(0, 0);
            kill(gone);
            Shulker moved = shulker(8, 70, 8); // a shulker a player put in the city
            List<String> reply = run("ecsr learn");

            assertTrue(reply.getFirst().startsWith("Learned the city again: 1 spots"), reply::toString);
            assertEquals(1, data().spots().size());
            assertEquals(8, data().spots().getFirst().x());
            assertEquals(1, data().generation());
            kill(moved);
            assertFalse(data().spots().getFirst().alive(), "re-learned shulkers own their new spots");
        }

        @Test
        void outsideACityItSaysSo() {
            admin.setLocation(new Location(end, 200, 75, 200));
            assertEquals(List.of("You are not in an End city of a managed world."), run("ecsr status"));
        }

        @Test
        void reloadReadsTheConfig() throws Exception {
            java.nio.file.Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "cooldown: 2h\n");
            assertEquals(List.of("Reloaded config.yml."), run("ecsr reload"));
            assertEquals(Duration.ofHours(2), plugin.settings().cooldown());
        }

        @Test
        void playersWithoutPermissionCannotUseIt() {
            PlayerMock player = server.addPlayer("Player");
            player.setLocation(new Location(end, 10, 75, 5));
            shulker(4, 70, 4);
            load(0, 0);
            server.dispatchCommand(player, "ecsr learn");
            assertEquals(0, data().generation());
        }
    }
}
