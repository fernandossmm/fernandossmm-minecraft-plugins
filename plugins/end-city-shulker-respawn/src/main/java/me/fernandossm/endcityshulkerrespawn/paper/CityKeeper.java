package me.fernandossm.endcityshulkerrespawn.paper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;
import me.fernandossm.endcityshulkerrespawn.api.CityShulkerRespawnEvent;
import me.fernandossm.endcityshulkerrespawn.config.Settings;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.core.Spot;
import me.fernandossm.endcityshulkerrespawn.store.ShulkerTag;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;

/** Learns cities from their shulkers, notices deaths, and brings shulkers back. */
public final class CityKeeper {

    private final Logger log;
    private final Supplier<Settings> settings;
    private final Supplier<Cities> cities;
    private final ShulkerTag.Keys tags;
    private final Supplier<Claims> claims;
    private final LongSupplier clock;
    private final Consumer<Shulker> keepForever;

    /** @param keepForever makes a new shulker never despawn, like a city's own */
    public CityKeeper(Logger log, Supplier<Settings> settings, Supplier<Cities> cities, ShulkerTag.Keys tags,
                      Supplier<Claims> claims, LongSupplier clock, Consumer<Shulker> keepForever) {
        this.log = log;
        this.settings = settings;
        this.cities = cities;
        this.tags = tags;
        this.claims = claims;
        this.clock = clock;
        this.keepForever = keepForever;
    }

    /**
     * The first time a chunk of the city loads, its shulkers become the city's spots there. Returns how many
     * spots were learned; 0 if the chunk had been learned before.
     */
    public int learn(City city, int chunkX, int chunkZ, Collection<? extends Entity> entities) {
        CityData data = city.load();
        if (data.learned(chunkX, chunkZ)) {
            return 0;
        }
        int learned = 0;
        for (Entity entity : entities) {
            if (entity instanceof Shulker shulker && !shulker.isDead()
                    && city.contains(shulker.getX(), shulker.getY(), shulker.getZ()) && owner(city, data, shulker).isEmpty()) {
                int index = data.addSpot(spotOf(shulker));
                tags.write(shulker, new ShulkerTag(city.key(), index, data.generation()));
                learned++;
            }
        }
        data.markLearned(chunkX, chunkZ);
        city.save(data);
        if (learned > 0) {
            log.fine(() -> "Learned " + data.spots().size() + " shulker spots so far in the End city at " + city.key());
        }
        return learned;
    }

    /**
     * Vanilla generates a city's shulkers at markers in the city's template and settles each on its first
     * tick: it picks a solid face to stick to, or teleports up to 8 blocks when there is none. A chunk is
     * learned before that, so its spots start where the markers are. Whenever living city shulkers are seen
     * (their chunk loading, and a few seconds later), their spots move to where they have settled. A shulker that
     * players carried off (outside the city, or further than vanilla ever teleports) leaves its spot alone.
     */
    public void settle(City city, Collection<? extends Entity> entities) {
        CityData data = null;
        boolean changed = false;
        for (Entity entity : entities) {
            if (!(entity instanceof Shulker shulker) || shulker.isDead()
                    || !city.contains(shulker.getX(), shulker.getY(), shulker.getZ())) {
                continue;
            }
            if (data == null) {
                data = city.load();
            }
            Optional<Integer> index = owner(city, data, shulker);
            if (index.isEmpty()) {
                continue;
            }
            Spot spot = data.spot(index.get());
            Location at = shulker.getLocation();
            if (Math.abs(at.getBlockX() - spot.x()) <= SETTLE_DISTANCE && Math.abs(at.getBlockY() - spot.y()) <= SETTLE_DISTANCE
                    && Math.abs(at.getBlockZ() - spot.z()) <= SETTLE_DISTANCE) {
                changed |= data.settled(index.get(), at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                        Spot.Face.valueOf(shulker.getAttachedFace().name()));
            }
        }
        if (changed) {
            city.save(data);
        }
    }

    /** Vanilla teleports an unsettled shulker up to 8 blocks along each axis; a little margin on top. */
    private static final int SETTLE_DISTANCE = 12;

    /** Forgets everything about the city and learns it again from the shulkers in its loaded chunks. */
    public int learnAgain(City city) {
        city.save(city.load().forgotten());
        int learned = 0;
        World world = city.world();
        int minX = (int) Math.floor(city.box().getMinX()) >> 4;
        int maxX = (int) Math.floor(city.box().getMaxX()) >> 4;
        int minZ = (int) Math.floor(city.box().getMinZ()) >> 4;
        int maxZ = (int) Math.floor(city.box().getMaxZ()) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    learned += learn(city, cx, cz, List.of(world.getChunkAt(cx, cz).getEntities()));
                }
            }
        }
        return learned;
    }

    /** A shulker is gone (dead, or removed by something other than its chunk unloading). */
    public void gone(Shulker shulker) {
        Optional<ShulkerTag> tag = tags.read(shulker);
        if (tag.isEmpty()) {
            return;
        }
        Optional<City> city = cities.get().byKey(shulker.getWorld(), tag.get().city(), shulker.getLocation());
        if (city.isEmpty()) {
            return;
        }
        CityData data = city.get().load();
        if (owns(data, tag.get()) && data.died(tag.get().spot(), clock.getAsLong(), settings.get().cooldown())) {
            city.get().save(data);
        }
    }

    /** Brings back the shulkers of this chunk's spots whose cooldown is over; returns how many. */
    public int refill(City city, int chunkX, int chunkZ) {
        World world = city.world();
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            return 0;
        }
        CityData data = city.load();
        int filled = 0;
        for (int index : data.dueIn(chunkX, chunkZ, clock.getAsLong())) {
            if (respawn(city, data, index)) {
                data.refilled(index);
                filled++;
            }
        }
        if (filled > 0) {
            city.save(data);
        }
        return filled;
    }

    /** Spawns the spot's shulker if the spot is free; false leaves the spot due for the next load. */
    private boolean respawn(City city, CityData data, int index) {
        Spot spot = data.spot(index);
        World world = city.world();
        Block block = world.getBlockAt(spot.x(), spot.y(), spot.z());
        BlockFace face = attachment(block, BlockFace.valueOf(spot.face().name()));
        Location at = new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5);
        // Only a shulker in this very block takes the spot; one in the block below or beside it does not.
        boolean taken = world.getNearbyEntitiesByType(Shulker.class, at, 1.5).stream()
                .anyMatch(s -> s.getLocation().getBlock().equals(block));
        if (!block.getType().isAir()
                || taken
                || claims.get().claimed(at)
                || !new CityShulkerRespawnEvent(at, city.key(), index).callEvent()) {
            return false;
        }
        world.spawn(at, Shulker.class, shulker -> {
            if (face != null) {
                shulker.setAttachedFace(face);
            }
            if (spot.color() != Spot.NO_COLOR) {
                shulker.setColor(DyeColor.values()[spot.color()]);
            }
            keepForever.accept(shulker);
            tags.write(shulker, new ShulkerTag(city.key(), index, data.generation()));
        });
        return true;
    }

    /**
     * The face to stick the new shulker to: the one learned if its block is solid, or else any solid one.
     * Null if nothing around is solid (a spot never settled, or players mined around it): the shulker is
     * then left to vanilla, which settles it as it settled the city's own, teleporting it if it must.
     */
    private static BlockFace attachment(Block block, BlockFace learned) {
        if (block.getRelative(learned).getType().isSolid()) {
            return learned;
        }
        for (BlockFace face : ATTACHABLE) {
            if (block.getRelative(face).getType().isSolid()) {
                return face;
            }
        }
        return null;
    }

    private static final BlockFace[] ATTACHABLE = {
        BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST};

    /** The spot this shulker owns in this city, if its tag is current. */
    public Optional<Integer> owner(City city, CityData data, Shulker shulker) {
        return tags.read(shulker)
                .filter(t -> t.city().equals(city.key()) && owns(data, t))
                .map(ShulkerTag::spot);
    }

    private static boolean owns(CityData data, ShulkerTag tag) {
        return tag.generation() == data.generation() && data.hasSpot(tag.spot());
    }

    private static Spot spotOf(Shulker shulker) {
        DyeColor color = shulker.getColor();
        return new Spot(shulker.getLocation().getBlockX(), shulker.getLocation().getBlockY(), shulker.getLocation().getBlockZ(),
                Spot.Face.valueOf(shulker.getAttachedFace().name()),
                color == null ? Spot.NO_COLOR : color.ordinal(),
                Spot.ALIVE);
    }
}
