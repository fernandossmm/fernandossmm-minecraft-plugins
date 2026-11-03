package me.fernandossm.multiplayerdragonrebirth.paper;

import java.util.Optional;
import org.bukkit.World;

/** Finds the dragon fight of an End world the plugin manages. */
@FunctionalInterface
public interface Fights {

    /** Empty when the world is not managed or has no dragon fight. */
    Optional<Fight> of(World world);
}
