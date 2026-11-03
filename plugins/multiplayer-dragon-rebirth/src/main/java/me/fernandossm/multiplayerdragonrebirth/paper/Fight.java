package me.fernandossm.multiplayerdragonrebirth.paper;

import org.bukkit.World;
import org.bukkit.entity.Entity;

/** The dragon fight of one managed End world. An interface so the listeners can be tested without a real fight. */
public interface Fight {

    World world();

    /** The fight's dragon exists and is loaded, alive or in its death animation. */
    boolean dragonAlive();

    /** A dragon is being summoned (the crystal and pillar sequence is running). */
    boolean summoning();

    /** The entity is this fight's dragon, as opposed to one spawned by other means (/summon, spawn eggs). */
    boolean isFightDragon(Entity entity);

    /**
     * Places four end crystals on the exit portal and starts the vanilla summoning sequence, which plays
     * only while those crystals exist. False if the server refused (for example, a dragon already exists).
     */
    boolean summon();

    /**
     * Vanilla's "first dragon has been killed" flag. While it is down, the world's first dragon is alive,
     * possibly in chunks that are not loaded, or about to appear.
     */
    boolean previouslyKilled();

    /** One line for /mdr status. */
    String describe();

    /** No dragon is alive, coming, or being summoned: a dragon may be summoned. */
    default boolean quiet() {
        return !dragonAlive() && !summoning() && previouslyKilled();
    }
}
