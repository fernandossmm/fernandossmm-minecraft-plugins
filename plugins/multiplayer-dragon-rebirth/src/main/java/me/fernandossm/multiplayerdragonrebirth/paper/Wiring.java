package me.fernandossm.multiplayerdragonrebirth.paper;

import me.fernandossm.multiplayerdragonrebirth.MultiplayerDragonRebirth;
import org.bukkit.plugin.PluginManager;

/** Creates the listeners and the summoner. */
public final class Wiring {

    private Wiring() {}

    public static Summoner wire(MultiplayerDragonRebirth plugin, PaperFights paperFights) {
        // Looked up on every use, so tests can replace the plugin's fights after it is enabled.
        Fights fights = world -> plugin.fights().of(world);
        Summoner summoner = new Summoner(plugin, plugin::settings, plugin::policy, fights, plugin.tracker());

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new DragonListener(plugin, plugin::settings, plugin::policy, plugin.store(),
                fights, paperFights, plugin.tracker()), plugin);
        pluginManager.registerEvents(new ArrivalListener(plugin, plugin::settings, summoner), plugin);
        return summoner;
    }
}
