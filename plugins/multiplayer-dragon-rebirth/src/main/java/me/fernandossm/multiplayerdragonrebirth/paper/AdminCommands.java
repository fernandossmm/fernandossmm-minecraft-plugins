package me.fernandossm.multiplayerdragonrebirth.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import me.fernandossm.multiplayerdragonrebirth.MultiplayerDragonRebirth;
import me.fernandossm.multiplayerdragonrebirth.core.FightTracker.Participant;
import me.fernandossm.multiplayerdragonrebirth.store.PlayerStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /mdr: inspect fights and players, change who counts as a newcomer, force a summon, reload. */
public final class AdminCommands {

    private record Target(UUID id, String name) {}

    private final MultiplayerDragonRebirth plugin;

    public AdminCommands(MultiplayerDragonRebirth plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(build(), "Multiplayer Dragon Rebirth administration", List.of("multiplayerdragonrebirth")));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mdr")
                .requires(source -> source.getSender().hasPermission(PaperFacts.ADMIN_PERMISSION))
                .then(Commands.literal("status")
                        .executes(c -> status(sender(c), null))
                        .then(worldArgument().executes(c -> withWorld(c, world -> status(sender(c), world)))))
                .then(Commands.literal("check").then(playerArgument().executes(c -> check(sender(c), player(c)))))
                .then(Commands.literal("mark").then(playerArgument().executes(c -> mark(sender(c), player(c)))))
                .then(Commands.literal("reset").then(playerArgument().executes(c -> reset(sender(c), player(c)))))
                .then(Commands.literal("reset-all")
                        .executes(c -> reply(sender(c), "This makes every player a newcomer again. Confirm with /mdr reset-all confirm", NamedTextColor.YELLOW))
                        .then(Commands.literal("confirm").executes(c -> resetAll(sender(c)))))
                .then(Commands.literal("mark-existing").executes(c -> markExisting(sender(c))))
                .then(Commands.literal("summon")
                        .executes(c -> summon(sender(c), null))
                        .then(worldArgument().executes(c -> withWorld(c, world -> summon(sender(c), world)))))
                .then(Commands.literal("reload").executes(c -> reload(sender(c))))
                .build();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.word()).suggests((context, builder) -> {
            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
            return builder.buildFuture();
        });
    }

    /** A world by name; plain words rather than Paper's world argument, which needs namespaced keys. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> worldArgument() {
        return Commands.argument("world", StringArgumentType.word()).suggests((context, builder) -> {
            Bukkit.getWorlds().forEach(w -> builder.suggest(w.getName()));
            return builder.buildFuture();
        });
    }

    private static int withWorld(CommandContext<CommandSourceStack> context, ToIntFunction<World> action) {
        String name = StringArgumentType.getString(context, "world");
        World world = Bukkit.getWorld(name);
        return world != null ? action.applyAsInt(world) : reply(sender(context), "No world named " + name + ".", NamedTextColor.RED);
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender();
    }

    private static String player(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "player");
    }

    int status(CommandSender sender, World only) {
        List<World> worlds = only != null ? List.of(only) : Bukkit.getWorlds();
        boolean any = false;
        for (World world : worlds) {
            Optional<Fight> fight = plugin.fights().of(world);
            if (fight.isEmpty()) {
                continue;
            }
            any = true;
            sender.sendMessage(Component.text(world.getName() + ": " + fight.get().describe(), NamedTextColor.LIGHT_PURPLE));
            plugin.summoner().countdown(world).ifPresent(c -> sender.sendMessage(Component.text(
                    "  countdown: " + Math.max(0, (c.endsAtMillis() - System.currentTimeMillis()) / 1000) + "s left, for "
                            + String.join(", ", c.newcomers().values()))));
            List<String> participants = plugin.tracker().participants(world.getUID()).stream().map(Participant::name).toList();
            if (!participants.isEmpty()) {
                sender.sendMessage(Component.text("  damaged the current dragon: " + String.join(", ", participants)));
            }
        }
        if (!any) {
            return reply(sender, only == null ? "No End world is managed." : only.getName() + " is not a managed End world.", NamedTextColor.RED);
        }
        sender.sendMessage(Component.text(plugin.store().size() + " players have fought the dragon.", NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    int check(CommandSender sender, String name) {
        Optional<Target> target = resolve(name);
        if (target.isEmpty()) {
            return unknown(sender, name);
        }
        Optional<PlayerStore.Entry> entry = plugin.store().get(target.get().id());
        if (entry.isEmpty()) {
            return reply(sender, target.get().name() + " is a newcomer.", NamedTextColor.GREEN);
        }
        PlayerStore.Entry e = entry.get();
        return reply(sender, target.get().name() + " has fought the dragon: " + PlayerStore.describe(e.reason())
                + ", since " + e.since() + (e.world() != null ? ", in " + e.world() : "") + ".", NamedTextColor.GOLD);
    }

    int mark(CommandSender sender, String name) {
        Optional<Target> target = resolve(name);
        if (target.isEmpty()) {
            return unknown(sender, name);
        }
        boolean added = plugin.store().markDone(target.get().id(),
                new PlayerStore.Entry(target.get().name(), Instant.now(), PlayerStore.Reason.ADMIN, null));
        return reply(sender, target.get().name() + (added ? " now counts as having fought the dragon." : " had already fought the dragon."), NamedTextColor.GOLD);
    }

    int reset(CommandSender sender, String name) {
        Optional<Target> target = resolve(name);
        if (target.isEmpty()) {
            return unknown(sender, name);
        }
        boolean removed = plugin.store().reset(target.get().id());
        return reply(sender, target.get().name() + (removed ? " is a newcomer again." : " was already a newcomer."), NamedTextColor.GREEN);
    }

    int resetAll(CommandSender sender) {
        int count = plugin.store().resetAll();
        return reply(sender, count + " players are newcomers again.", NamedTextColor.GREEN);
    }

    int markExisting(CommandSender sender) {
        int count = plugin.markExistingPlayers();
        return reply(sender, count + " more players who have joined the server now count as having fought the dragon.", NamedTextColor.GOLD);
    }

    int summon(CommandSender sender, World world) {
        if (world == null) {
            if (!(sender instanceof Player player)) {
                return reply(sender, "Name the End world: /mdr summon <world>", NamedTextColor.RED);
            }
            world = player.getWorld();
        }
        Optional<Fight> fight = plugin.fights().of(world);
        if (fight.isEmpty()) {
            return reply(sender, world.getName() + " is not a managed End world.", NamedTextColor.RED);
        }
        if (!fight.get().quiet()) {
            return reply(sender, "There is already a dragon in " + world.getName() + ", or one is being summoned.", NamedTextColor.RED);
        }
        boolean started = plugin.summoner().summon(fight.get());
        return started
                ? reply(sender, "Summoning the dragon in " + world.getName() + ".", NamedTextColor.LIGHT_PURPLE)
                : reply(sender, "The server refused to summon a dragon: " + fight.get().describe(), NamedTextColor.RED);
    }

    int reload(CommandSender sender) {
        plugin.reloadSettings();
        return reply(sender, "Reloaded config.yml.", NamedTextColor.GREEN);
    }

    /** An online player, a player the server knows by name, or a player in the store under that name. */
    private Optional<Target> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName()));
        }
        OfflinePlayer known = Bukkit.getOfflinePlayerIfCached(name);
        if (known != null) {
            return Optional.of(new Target(known.getUniqueId(), known.getName() != null ? known.getName() : name));
        }
        return plugin.store().findByName(name).map(id -> new Target(id, plugin.store().get(id).map(PlayerStore.Entry::name).orElse(name)));
    }

    private static int unknown(CommandSender sender, String name) {
        return reply(sender, "No player named " + name + " has joined this server.", NamedTextColor.RED);
    }

    private static int reply(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
        return Command.SINGLE_SUCCESS;
    }
}
