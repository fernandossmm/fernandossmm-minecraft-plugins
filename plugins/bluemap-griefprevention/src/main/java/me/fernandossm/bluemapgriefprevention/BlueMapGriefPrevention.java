package me.fernandossm.bluemapgriefprevention;

import com.mojang.brigadier.Command;
import de.bluecolored.bluemap.api.BlueMapAPI;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.function.Consumer;
import me.fernandossm.bluemapgriefprevention.core.Settings;
import me.fernandossm.bluemapgriefprevention.paper.BlueMapSink;
import me.fernandossm.bluemapgriefprevention.paper.ClaimListener;
import me.fernandossm.bluemapgriefprevention.paper.ClaimSource;
import me.fernandossm.bluemapgriefprevention.paper.GriefPreventionSource;
import me.fernandossm.bluemapgriefprevention.paper.Sync;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

// Not final: MockBukkit loads plugins in tests by subclassing them.
public class BlueMapGriefPrevention extends JavaPlugin {

    public static final String PERMISSION = "bluemapgriefprevention.admin";

    private volatile Settings settings = Settings.DEFAULTS;
    private ClaimSource source = new GriefPreventionSource();
    private BlueMapSink sink;
    private Sync sync;
    private Consumer<BlueMapAPI> onBlueMapEnable;
    private Consumer<BlueMapAPI> onBlueMapDisable;
    private boolean commandsRegistered;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig(), getLogger());
        sink = new BlueMapSink(Bukkit::getWorld);
        sync = new Sync(this, this::settings, () -> source, sink);
        getServer().getPluginManager().registerEvents(new ClaimListener(sync), this);
        // BlueMap calls these from its own threads, at startup and on every /bluemap reload.
        onBlueMapEnable = api -> Bukkit.getScheduler().runTask(this, () -> {
            sink.enabled(api);
            sync.syncNow();
        });
        onBlueMapDisable = api -> Bukkit.getScheduler().runTask(this, sink::disabled);
        BlueMapAPI.onEnable(onBlueMapEnable);
        BlueMapAPI.onDisable(onBlueMapDisable);
        sync.startPeriodic();
        if (!commandsRegistered) {
            registerCommands();
            commandsRegistered = true;
        }
    }

    @Override
    public void onDisable() {
        if (onBlueMapEnable != null) {
            BlueMapAPI.unregisterListener(onBlueMapEnable);
            BlueMapAPI.unregisterListener(onBlueMapDisable);
        }
        if (sync != null) {
            sync.stopPeriodic();
        }
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("bmgp")
                        .requires(s -> s.getSender().hasPermission(PERMISSION))
                        .then(Commands.literal("resync").executes(c -> {
                            sync.syncNow();
                            c.getSource().getSender().sendMessage(Component.text(
                                    sink.ready() ? "Claims redrawn." : "BlueMap is not loaded yet; claims are drawn when it is.", NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("reload").executes(c -> {
                            reloadSettings();
                            c.getSource().getSender().sendMessage(Component.text("Reloaded config.yml and redrew the claims.", NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .build(),
                "BlueMap GriefPrevention administration", List.of("bluemapgriefprevention")));
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
        sync.startPeriodic();
        sync.syncNow();
    }

    public Settings settings() {
        return settings;
    }

    void useSourceForTests(ClaimSource source) {
        this.source = source;
    }

    BlueMapSink sink() {
        return sink;
    }

    Sync sync() {
        return sync;
    }
}
