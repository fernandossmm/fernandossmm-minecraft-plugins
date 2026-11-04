package me.fernandossm.endcityshulkerrespawn.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntBiFunction;
import me.fernandossm.endcityshulkerrespawn.EndCityShulkerRespawn;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.core.Durations;
import me.fernandossm.endcityshulkerrespawn.core.Spot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /ecsr: inspect the End city you are in, make it refill, re-learn it, reload the config. */
public final class AdminCommands {

    public static final String PERMISSION = "endcityshulkerrespawn.admin";

    private final EndCityShulkerRespawn plugin;

    public AdminCommands(EndCityShulkerRespawn plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(build(), "End City Shulker Respawn administration", List.of("endcityshulkerrespawn")));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ecsr")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("status").executes(c -> inCity(c.getSource().getSender(), this::status)))
                .then(Commands.literal("refill").executes(c -> inCity(c.getSource().getSender(), this::refill)))
                .then(Commands.literal("learn").executes(c -> inCity(c.getSource().getSender(), this::learn)))
                .then(Commands.literal("reload").executes(c -> reload(c.getSource().getSender())))
                .build();
    }

    /** Runs the action on the city the player stands in. */
    private int inCity(CommandSender sender, ToIntBiFunction<CommandSender, City> action) {
        if (!(sender instanceof Player player)) {
            return reply(sender, "Stand in an End city to use this.", NamedTextColor.RED);
        }
        Optional<City> city = plugin.cities().at(player.getLocation());
        return city.isPresent()
                ? action.applyAsInt(sender, city.get())
                : reply(sender, "You are not in an End city of a managed world.", NamedTextColor.RED);
    }

    int status(CommandSender sender, City city) {
        CityData data = city.load();
        long now = plugin.clock().getAsLong();
        long alive = data.alive();
        long ready = data.spots().stream().filter(s -> s.due(now)).count();
        long cooling = data.spots().size() - alive - ready;
        StringBuilder text = new StringBuilder("End city at " + city.key() + ": " + data.spots().size() + " spots, learned from "
                + data.learnedChunks().size() + " chunks; " + alive + " alive, " + ready + " ready to come back, " + cooling + " cooling down");
        data.nextDue(now).ifPresent(at -> text.append(" (next in ").append(Durations.format(Duration.ofMillis(at - now))).append(")"));
        reply(sender, text.append('.').toString(), NamedTextColor.LIGHT_PURPLE);
        long claimed = data.spots().stream().filter(s -> plugin.claims().claimed(location(city, s))).count();
        if (claimed > 0) {
            reply(sender, claimed + " spots are inside a land claim and stay empty.", NamedTextColor.GRAY);
        }
        return Command.SINGLE_SUCCESS;
    }

    int refill(CommandSender sender, City city) {
        CityData data = city.load();
        int due = data.dueNow(plugin.clock().getAsLong());
        city.save(data);
        return reply(sender, due + " shulkers will come back the next time their chunks load.", NamedTextColor.LIGHT_PURPLE);
    }

    int learn(CommandSender sender, City city) {
        int learned = plugin.keeper().learnAgain(city);
        return reply(sender, "Learned the city again: " + learned + " spots from the shulkers in it now. "
                + "Chunks that are not loaded are learned when they next load.", NamedTextColor.LIGHT_PURPLE);
    }

    int reload(CommandSender sender) {
        plugin.reloadSettings();
        return reply(sender, "Reloaded config.yml.", NamedTextColor.GREEN);
    }

    private static Location location(City city, Spot spot) {
        return new Location(city.world(), spot.x() + 0.5, spot.y(), spot.z() + 0.5);
    }

    private static int reply(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
        return Command.SINGLE_SUCCESS;
    }
}
