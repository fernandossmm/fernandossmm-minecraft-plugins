package me.fernandossm.axgravesownergrace;

import com.artillexstudios.axgraves.api.events.GraveInteractEvent;
import com.artillexstudios.axgraves.grave.Grave;
import java.io.File;
import java.time.Duration;
import java.util.logging.Level;
import me.fernandossm.axgravesownergrace.core.Grace;
import me.fernandossm.axgravesownergrace.core.Settings;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps each AxGraves grave its owner's for a grace period, then lets anyone open it. AxGraves fires its
 * {@link GraveInteractEvent} before it opens a grave, gives its experience or empties it with a sneaking click;
 * cancelling the event stops all three.
 */
// Not final: MockBukkit loads plugins in tests by subclassing them.
public class AxGravesOwnerGrace extends JavaPlugin implements Listener {

    public static final String BYPASS = "axgravesownergrace.bypass";
    /** AxGraves' own staff permission, which also opens any grave in AxGraves itself. */
    static final String AXGRAVES_ADMIN = "axgraves.admin";

    private volatile Settings settings = Settings.DEFAULTS;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig(), getLogger());
        getServer().getPluginManager().registerEvents(this, this);
        warnIfAxGravesKeepsGravesPrivate();
        getLogger().info("Graves are their owner's for " + settings.gracePeriod().toSeconds() + " s, then anyone's");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGraveInteract(GraveInteractEvent event) {
        Player opener = event.getOpener();
        Grave grave = event.getGrave();
        Duration left = Grace.remaining(opener.getUniqueId(), grave.getPlayer().getUniqueId(),
                opener.hasPermission(BYPASS) || opener.hasPermission(AXGRAVES_ADMIN),
                grave.getSpawned(), System.currentTimeMillis(), settings.gracePeriod());
        if (left.isZero()) {
            return;
        }
        event.setCancelled(true);
        String template = settings.lockedMessage();
        if (template == null || template.isBlank()) {
            return;
        }
        String owner = grave.getPlayerName() != null ? grave.getPlayerName() : String.valueOf(grave.getPlayer().getName());
        try {
            opener.sendMessage(MiniMessage.miniMessage().deserialize(template,
                    Placeholder.unparsed("owner", owner), Placeholder.unparsed("time", Grace.clock(left))));
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Cannot send messages.locked '" + template + "'", e);
        }
    }

    /** With AxGraves' interact-only-own on, it refuses everyone but the owner itself, before this plugin is asked. */
    private void warnIfAxGravesKeepsGravesPrivate() {
        Plugin axgraves = getServer().getPluginManager().getPlugin("AxGraves");
        if (axgraves == null) {
            return;
        }
        File config = new File(axgraves.getDataFolder(), "config.yml");
        if (config.isFile() && YamlConfiguration.loadConfiguration(config).getBoolean("interact-only-own", false)) {
            getLogger().warning("AxGraves has interact-only-own: true, so graves stay their owner's for good; "
                    + "set it to false for graves to open to everyone after the grace period");
        }
    }
}
