package me.fernandossm.bluemapbannermarkers.paper;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Whether a player may turn the banner at this block into a waypoint. */
public interface BuildCheck {

    BuildCheck ANYONE = (player, block) -> true;

    boolean mayBuild(Player player, Block block);
}
