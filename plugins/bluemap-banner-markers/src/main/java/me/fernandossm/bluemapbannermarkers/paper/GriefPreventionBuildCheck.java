package me.fernandossm.bluemapbannermarkers.paper;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** GriefPrevention's answer: build trust in the claim the banner is in, or no claim there. Loaded only with GriefPrevention. */
public final class GriefPreventionBuildCheck implements BuildCheck {

    @Override
    public boolean mayBuild(Player player, Block block) {
        return GriefPrevention.instance.allowBuild(player, block.getLocation(), block.getType()) == null;
    }
}
