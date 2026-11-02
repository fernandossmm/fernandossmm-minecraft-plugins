package me.fernandossm.dragonrebirth;

import org.bukkit.plugin.java.JavaPlugin;

// Not final: MockBukkit loads plugins in tests by subclassing them.
public class DragonRebirth extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Enabled; no features yet.");
    }
}
