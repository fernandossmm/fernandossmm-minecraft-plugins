package me.fernandossm.discordsrvfailclosed;

import java.util.Optional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Refuses every login while DiscordSRV's linked-account requirement is not in force ({@link Gate}). When DiscordSRV
 * disables itself, Bukkit drops its listeners and with them that requirement; this plugin keeps the server closed
 * instead. While the requirement is in force it does nothing, and DiscordSRV decides who joins.
 */
// Not final: MockBukkit loads plugins in tests by subclassing them.
public class DiscordSrvFailClosed extends JavaPlugin implements Listener {

    private volatile Settings settings = Settings.DEFAULTS;
    private volatile Gate gate;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig(), getLogger());
        gate = new Gate(getServer().getPluginManager()::getPlugin, getLogger());
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Highest, so a plugin that allows logins at a lower priority cannot open the server again. A login another
     * plugin already refused is left with that plugin's message.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        gate.check().ifPresent(refusal -> {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, settings.message());
            getLogger().warning("Refused the login of " + event.getName() + ": " + refusal.text());
        });
    }

    /** Once the server has started: whether it is open, so a closed server is plain in the startup log. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        report();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (Gate.DISCORDSRV.equals(event.getPlugin().getName())) {
            getLogger().severe("DiscordSRV was disabled: every login is refused until it is enabled again");
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (Gate.DISCORDSRV.equals(event.getPlugin().getName())) {
            report();
        }
    }

    private void report() {
        Optional<Refusal> refusal = gate.check();
        if (refusal.isPresent()) {
            getLogger().severe("Every login is refused: " + refusal.get().text());
        } else {
            getLogger().info("DiscordSRV requires a linked account; logins are left to it");
        }
    }

    public Settings settings() {
        return settings;
    }

    void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
    }

    void useForTests(Gate gate) {
        this.gate = gate;
    }
}
