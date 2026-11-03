package me.fernandossm.multiplayerdragonrebirth;

import java.lang.reflect.Proxy;
import java.util.UUID;
import me.fernandossm.multiplayerdragonrebirth.paper.Fight;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;

/** A dragon fight that records what the plugin asks of it. MockBukkit has no End fight of its own. */
final class FakeFight implements Fight {

    private final World world;
    boolean dragonAlive;
    boolean summoning;
    boolean previouslyKilled = true;
    boolean refuseSummon;
    int summons;
    EnderDragon dragon;

    FakeFight(World world) {
        this.world = world;
    }

    /** A new dragon belonging to this fight. */
    EnderDragon spawnDragon() {
        dragon = dragon(world);
        dragonAlive = true;
        summoning = false;
        return dragon;
    }

    /** A dragon in the same world that is not the fight's, like one from /summon. */
    static EnderDragon dragon(World world) {
        UUID id = UUID.randomUUID();
        return (EnderDragon) Proxy.newProxyInstance(EnderDragon.class.getClassLoader(), new Class<?>[] {EnderDragon.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getUniqueId" -> id;
                    case "getLocation" -> world.getSpawnLocation();
                    case "getName" -> "Ender Dragon";
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakeDragon " + id;
                    default -> {
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) yield false;
                        if (type == int.class || type == long.class || type == double.class || type == float.class) yield 0;
                        yield null;
                    }
                });
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public boolean dragonAlive() {
        return dragonAlive;
    }

    @Override
    public boolean summoning() {
        return summoning;
    }

    @Override
    public boolean isFightDragon(Entity entity) {
        return dragon != null && dragon.getUniqueId().equals(entity.getUniqueId());
    }

    @Override
    public boolean summon() {
        summons++;
        if (refuseSummon) {
            return false;
        }
        summoning = true;
        return true;
    }

    @Override
    public boolean previouslyKilled() {
        return previouslyKilled;
    }

    @Override
    public String describe() {
        return "fake fight, dragon " + dragonAlive + ", summoning " + summoning + ", previously killed " + previouslyKilled;
    }
}
