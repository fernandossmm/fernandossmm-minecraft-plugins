package me.fernandossm.bluemapbannermarkers;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.bluecolored.bluemap.api.BlueMapAPI;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.function.Consumer;
import me.fernandossm.bluemapbannermarkers.core.Settings;
import me.fernandossm.bluemapbannermarkers.core.Waypoint;
import me.fernandossm.bluemapbannermarkers.paper.BlueMapSink;
import me.fernandossm.bluemapbannermarkers.paper.BuildCheck;
import me.fernandossm.bluemapbannermarkers.paper.ClientTextures;
import me.fernandossm.bluemapbannermarkers.paper.Displays;
import me.fernandossm.bluemapbannermarkers.paper.FaceStore;
import me.fernandossm.bluemapbannermarkers.paper.GriefPreventionBuildCheck;
import me.fernandossm.bluemapbannermarkers.paper.WaypointListener;
import me.fernandossm.bluemapbannermarkers.paper.Waypoints;
import me.fernandossm.bluemapbannermarkers.store.WaypointStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

// Not final: MockBukkit loads plugins in tests by subclassing them.
public class BlueMapBannerMarkers extends JavaPlugin {

    public static final String ADMIN = "bluemapbannermarkers.admin";

    private volatile Settings settings = Settings.DEFAULTS;
    private @Nullable BuildCheck buildCheck;
    private ClientTextures textures;
    private WaypointStore store;
    private FaceStore faces;
    private Displays displays;
    private BlueMapSink sink;
    private Waypoints waypoints;
    private Consumer<BlueMapAPI> onBlueMapEnable;
    private Consumer<BlueMapAPI> onBlueMapDisable;
    private boolean commandsRegistered;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig(), getLogger());
        textures = new ClientTextures(getServer().getWorldContainer().toPath().toAbsolutePath().normalize(),
                getServer().getMinecraftVersion(), getLogger());
        store = new WaypointStore(getDataPath().resolve("waypoints.json"), getLogger());
        store.load();
        displays = new Displays(this);
        // The face store tells the map to redraw once the waypoints exist; they are set just below.
        faces = new FaceStore(this, getDataPath().resolve("faces"), store, textures, () -> waypoints.requestRedraw());
        sink = new BlueMapSink(Bukkit::getWorld, textures, textures::available, new BlueMapSink.FaceSource() {
            @Override
            public java.awt.image.@Nullable BufferedImage face(java.util.UUID owner) {
                return faces.face(owner);
            }

            @Override
            public @Nullable String version(java.util.UUID owner) {
                return faces.version(owner);
            }
        }, getLogger());
        waypoints = new Waypoints(this, this::settings, store, displays, sink, this::buildCheck);
        getServer().getPluginManager().registerEvents(new WaypointListener(this, waypoints, faces), this);
        // BlueMap calls these from its own threads, at startup and on every /bluemap reload.
        onBlueMapEnable = api -> Bukkit.getScheduler().runTask(this, () -> {
            textures.available();
            sink.enabled(api);
            waypoints.redrawNow();
            Bukkit.getOnlinePlayers().forEach(faces::refresh);
        });
        onBlueMapDisable = api -> Bukkit.getScheduler().runTask(this, sink::disabled);
        BlueMapAPI.onEnable(onBlueMapEnable);
        BlueMapAPI.onDisable(onBlueMapDisable);
        waypoints.checkLoaded();
        waypoints.startPeriodic();
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
        if (waypoints != null) {
            waypoints.stopPeriodic();
        }
        if (displays != null) {
            displays.hideAll();
        }
        if (textures != null) {
            textures.close();
        }
    }

    /** GriefPrevention's build trust if it is running, anyone otherwise. Decided on first use, after every plugin loaded. */
    private BuildCheck buildCheck() {
        if (buildCheck == null) {
            buildCheck = getServer().getPluginManager().isPluginEnabled("GriefPrevention") ? new GriefPreventionBuildCheck() : BuildCheck.ANYONE;
        }
        return buildCheck;
    }

    public Settings settings() {
        return settings;
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("bbm")
                        .requires(s -> s.getSender().hasPermission(ADMIN))
                        .then(Commands.literal("list")
                                .executes(c -> list(c.getSource().getSender(), null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(c -> list(c.getSource().getSender(), StringArgumentType.getString(c, "player")))))
                        .then(Commands.literal("delete").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
                            Waypoint removed = waypoints.remove(IntegerArgumentType.getInteger(c, "id"),
                                    "deleted by " + c.getSource().getSender().getName());
                            reply(c, removed == null ? Component.text("No waypoint with that id.", NamedTextColor.RED)
                                    : Component.text("Deleted waypoint \"" + removed.name() + "\" of " + removed.ownerName()
                                    + "; the banner stays.", NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        })))
                        .then(Commands.literal("scale").then(Commands.argument("size", IntegerArgumentType.integer(1, 2))
                                .executes(c -> scale(c, null))
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(c -> scale(c, IntegerArgumentType.getInteger(c, "id"))))))
                        .then(Commands.literal("reload").executes(c -> {
                            reloadConfig();
                            settings = Settings.from(getConfig(), getLogger());
                            waypoints.redrawNow();
                            reply(c, Component.text("Reloaded config.yml.", NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .build(),
                "BlueMap Banner Markers administration", List.of("bluemapbannermarkers")));
    }

    private int list(CommandSender sender, @Nullable String player) {
        List<Waypoint> shown = waypoints.all().stream().filter(w -> player == null || w.ownerName().equalsIgnoreCase(player)).toList();
        sender.sendMessage(Component.text(shown.size() + (shown.size() == 1 ? " waypoint" : " waypoints")
                + (player == null ? "" : " of " + player), NamedTextColor.GOLD));
        for (Waypoint w : shown) {
            sender.sendMessage(Component.text("#" + w.id() + " \"" + w.name() + "\" of " + w.ownerName() + " in " + w.world()
                    + " at " + w.x() + " " + w.y() + " " + w.z() + (w.scale() == 2 ? ", large" : ""), NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int scale(CommandContext<CommandSourceStack> c, @Nullable Integer id) {
        int size = IntegerArgumentType.getInteger(c, "size");
        Waypoint target;
        if (id != null) {
            target = store.byId(id).orElse(null);
        } else if (c.getSource().getSender() instanceof Player player) {
            Block block = player.getTargetBlockExact(8);
            World world = player.getWorld();
            target = block == null ? null : store.at(world.getName(), block.getX(), block.getY(), block.getZ()).orElse(null);
        } else {
            reply(c, Component.text("From the console, give the waypoint's id: /bbm scale <1|2> <id>.", NamedTextColor.RED));
            return 0;
        }
        if (target == null) {
            reply(c, Component.text(id != null ? "No waypoint with that id." : "You are not looking at a waypoint banner.", NamedTextColor.RED));
            return 0;
        }
        waypoints.scale(target, size);
        reply(c, Component.text("Waypoint \"" + target.name() + "\" is now shown at " + size + "x on the map.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static void reply(CommandContext<CommandSourceStack> c, Component message) {
        c.getSource().getSender().sendMessage(message);
    }

    Waypoints waypoints() {
        return waypoints;
    }

    BlueMapSink sink() {
        return sink;
    }
}
