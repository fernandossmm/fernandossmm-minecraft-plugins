package me.fernandossm.e2eharness;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import me.fernandossm.multiplayerdragonrebirth.api.DragonSummonEvent;
import me.fernandossm.multiplayerdragonrebirth.api.FirstTimeDragonKillEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Console commands for the end-to-end suite. Every reply is one JSON object, so the suite can parse it.
 * Players attack through vanilla's /damage command instead, so the plugin sees real damage events.
 */
public final class E2eHarness extends JavaPlugin implements Listener {

    private static final NamespacedKey SUMMONING_CRYSTAL = new NamespacedKey("multiplayerdragonrebirth", "summoning_crystal");

    private int summonEvents;
    private final List<String> killNewcomers = new ArrayList<>();
    private int killEvents;
    private int lastDeathExp = -1;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("mdrtest")
                        .then(Commands.literal("state").executes(c -> reply(c.getSource(), state())))
                        .then(Commands.literal("portal").executes(c -> reply(c.getSource(), portal())))
                        .then(Commands.literal("clearorbs").executes(c -> reply(c.getSource(), clearOrbs())))
                        .then(Commands.literal("vanilla-respawn").executes(c -> reply(c.getSource(), vanillaRespawn())))
                        .then(Commands.literal("vanish").then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("on", StringArgumentType.word()).executes(c -> reply(c.getSource(),
                                        vanish(StringArgumentType.getString(c, "player"), Boolean.parseBoolean(StringArgumentType.getString(c, "on"))))))))
                        .build(),
                "End-to-end test harness"));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("ecsrtest")
                        .then(Commands.literal("city").then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> toFile(c.getSource(), "e2e-city.json",
                                        CityProbe.city(end(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z")))))))
                        .then(Commands.literal("claim").then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> reply(c.getSource(),
                                        Claims.claim(end(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z")))))))
                        .then(Commands.literal("unclaim").executes(c -> reply(c.getSource(), Claims.unclaimAll())))
                        .then(Commands.literal("whereis").then(Commands.argument("player", StringArgumentType.word())
                                .executes(c -> reply(c.getSource(), CityProbe.whereIs(StringArgumentType.getString(c, "player"))))))
                        .build(),
                "End-to-end test harness: End cities"));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("gptest")
                        .then(Commands.literal("claim").then(word("world").then(word("owner")
                                .then(integer("x1").then(integer("z1").then(integer("x2").then(integer("z2").executes(c -> reply(c.getSource(),
                                        GpOps.claim(str(c, "world"), str(c, "owner"), num(c, "x1"), num(c, "z1"), num(c, "x2"), num(c, "z2")))))))))))
                        .then(Commands.literal("resize").then(integer("id")
                                .then(integer("x1").then(integer("z1").then(integer("x2").then(integer("z2").executes(c -> reply(c.getSource(),
                                        GpOps.resize(num(c, "id"), num(c, "x1"), num(c, "z1"), num(c, "x2"), num(c, "z2"))))))))))
                        .then(Commands.literal("transfer").then(integer("id").then(word("owner")
                                .executes(c -> reply(c.getSource(), GpOps.transfer(num(c, "id"), str(c, "owner")))))))
                        .then(Commands.literal("delete").then(integer("id")
                                .executes(c -> reply(c.getSource(), GpOps.delete(num(c, "id"))))))
                        .then(Commands.literal("locator").then(word("player")
                                .executes(c -> reply(c.getSource(), GpOps.locatorColor(str(c, "player"))))))
                        .build(),
                "End-to-end test harness: GriefPrevention claims"));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("skintest").then(word("player").executes(c -> {
                    org.bukkit.entity.Player player = getServer().getPlayerExact(str(c, "player"));
                    JsonObject o = new JsonObject();
                    if (player != null) {
                        var profile = player.getPlayerProfile();
                        java.net.URL skin = profile.getTextures().getSkin();
                        o.addProperty("skin", skin == null ? null : skin.toString());
                        o.addProperty("properties", profile.getProperties().stream().map(p -> p.getName()).toList().toString());
                    }
                    return reply(c.getSource(), o);
                })).build(),
                "End-to-end test harness: skins"));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("bbmtest")
                        .then(Commands.literal("displays").then(word("world").then(integer("x").then(integer("y").then(integer("z")
                                .then(integer("r").executes(c -> reply(c.getSource(), BbmOps.displays(str(c, "world"),
                                        num(c, "x"), num(c, "y"), num(c, "z"), num(c, "r"))))))))))
                        .then(Commands.literal("pixel").then(integer("x").then(integer("y")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(c -> reply(c.getSource(), BbmOps.pixel(str(c, "path"), num(c, "x"), num(c, "y"))))))))
                        .then(Commands.literal("skin").then(word("player").then(integer("x").then(integer("y")
                                .executes(c -> reply(c.getSource(), BbmOps.skin(str(c, "player"), num(c, "x"), num(c, "y"))))))))
                        .build(),
                "End-to-end test harness: banner waypoints"));
    }

    @EventHandler
    public void onSummon(DragonSummonEvent event) {
        summonEvents++;
    }

    /** The experience Paper will drop: it takes it from the death event, as left by every plugin. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            lastDeathExp = event.getDroppedExp();
        }
    }

    @EventHandler
    public void onKill(FirstTimeDragonKillEvent event) {
        killEvents++;
        killNewcomers.clear();
        event.getNewcomers().forEach(id -> killNewcomers.add(Bukkit.getOfflinePlayer(id).getName()));
    }

    private static World end() {
        World end = Bukkit.getWorld("world_the_end");
        if (end == null) {
            throw new IllegalStateException("world_the_end is not loaded");
        }
        return end;
    }

    private JsonObject state() {
        World end = end();
        DragonBattle battle = end.getEnderDragonBattle();
        JsonObject o = new JsonObject();
        EnderDragon dragon = battle.getEnderDragon();
        o.addProperty("dragon", dragon != null);
        o.addProperty("dragonHealth", dragon != null ? dragon.getHealth() : 0);
        o.addProperty("dragonPhase", dragon != null ? dragon.getPhase().name() : "");
        o.addProperty("dragonDeathTicks", dragon != null ? dragon.getDeathAnimationTicks() : 0);
        o.addProperty("dragonValid", dragon != null && dragon.isValid());
        o.addProperty("dragonsInWorld", end.getEntitiesByClass(EnderDragon.class).size());
        o.addProperty("phase", battle.getRespawnPhase().name());
        o.addProperty("previouslyKilled", battle.hasBeenPreviouslyKilled());
        o.addProperty("gateways", battle.getGatewayCount());
        Location portal = battle.getEndPortalLocation();
        if (portal != null) {
            o.add("portal", xyz(portal));
            JsonArray eggs = new JsonArray();
            for (int y = portal.getBlockY(); y < portal.getBlockY() + 12; y++) {
                if (end.getBlockAt(portal.getBlockX(), y, portal.getBlockZ()).getType() == Material.DRAGON_EGG) {
                    eggs.add(y);
                }
            }
            o.add("eggBlocks", eggs);
        }
        JsonArray crystals = new JsonArray();
        for (EnderCrystal crystal : end.getEntitiesByClass(EnderCrystal.class)) {
            if (portal != null && crystal.getLocation().distance(portal) < 8) {
                JsonObject c = xyz(crystal.getLocation());
                c.addProperty("invulnerable", crystal.isInvulnerable());
                c.addProperty("summoning", crystal.getPersistentDataContainer().has(SUMMONING_CRYSTAL));
                crystals.add(c);
            }
        }
        o.add("portalCrystals", crystals);
        int eggItems = 0;
        boolean eggItemsPermanent = true;
        for (Item item : end.getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == Material.DRAGON_EGG) {
                eggItems += item.getItemStack().getAmount();
                eggItemsPermanent &= item.isUnlimitedLifetime();
            }
        }
        o.addProperty("eggItems", eggItems);
        o.addProperty("eggItemsPermanent", eggItemsPermanent);
        long xp = 0;
        for (ExperienceOrb orb : end.getEntitiesByClass(ExperienceOrb.class)) {
            xp += (long) orb.getExperience() * orb.getCount();
        }
        o.addProperty("xp", xp);
        o.addProperty("lastDeathExp", lastDeathExp);
        o.addProperty("summonEvents", summonEvents);
        o.addProperty("killEvents", killEvents);
        JsonArray newcomers = new JsonArray();
        killNewcomers.forEach(newcomers::add);
        o.add("lastKillNewcomers", newcomers);
        return o;
    }

    /** The exit portal's blocks, to check where crystals stand. */
    private JsonObject portal() {
        World end = end();
        Location portal = end.getEnderDragonBattle().getEndPortalLocation();
        JsonObject o = new JsonObject();
        if (portal == null) {
            o.addProperty("error", "no portal");
            return o;
        }
        o.add("portal", xyz(portal));
        for (int dy = -1; dy <= 5; dy++) {
            JsonObject layer = new JsonObject();
            for (int[] d : new int[][] {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 0}}) {
                Block block = end.getBlockAt(portal.getBlockX() + d[0], portal.getBlockY() + dy, portal.getBlockZ() + d[1]);
                layer.addProperty(d[0] + "," + d[1], block.getType().name());
            }
            o.add("dy" + dy, layer);
        }
        return o;
    }

    private JsonObject clearOrbs() {
        int removed = 0;
        for (ExperienceOrb orb : end().getEntitiesByClass(ExperienceOrb.class)) {
            orb.remove();
            removed++;
        }
        JsonObject o = new JsonObject();
        o.addProperty("removed", removed);
        return o;
    }

    /**
     * Places plain crystals where the plugin puts its own and asks vanilla to respawn the dragon as if a
     * player had placed them. Vanilla only accepts crystals in the spots players use, so success proves
     * the plugin's crystal positions are the vanilla ones.
     */
    private JsonObject vanillaRespawn() {
        World end = end();
        DragonBattle battle = end.getEnderDragonBattle();
        Location portal = battle.getEndPortalLocation();
        for (int[] offset : new int[][] {{3, 1, 0}, {-3, 1, 0}, {0, 1, 3}, {0, 1, -3}}) {
            end.spawn(portal.clone().add(offset[0] + 0.5, offset[1], offset[2] + 0.5), EnderCrystal.class, c -> c.setShowingBottom(false));
        }
        battle.initiateRespawn();
        JsonObject o = new JsonObject();
        o.addProperty("phase", battle.getRespawnPhase().name());
        return o;
    }

    private JsonObject vanish(String name, boolean on) {
        Player player = Bukkit.getPlayerExact(name);
        JsonObject o = new JsonObject();
        if (player == null) {
            o.addProperty("error", "not online");
            return o;
        }
        if (on) {
            player.setMetadata("vanished", new FixedMetadataValue(this, true));
        } else {
            player.removeMetadata("vanished", this);
        }
        o.addProperty("vanished", on);
        return o;
    }

    private static JsonObject xyz(Location l) {
        JsonObject o = new JsonObject();
        o.addProperty("x", l.getX());
        o.addProperty("y", l.getY());
        o.addProperty("z", l.getZ());
        return o;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
        return Commands.argument(name, StringArgumentType.word());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> integer(String name) {
        return Commands.argument(name, IntegerArgumentType.integer());
    }

    private static String str(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static int num(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String name) {
        return IntegerArgumentType.getInteger(c, name);
    }

    /** For replies too long for RCON (4,096 bytes): written to a file in the server directory instead. */
    private static int toFile(CommandSourceStack source, String name, JsonObject json) {
        try {
            Files.writeString(Path.of(name), json.toString(), StandardCharsets.UTF_8);
            source.getSender().sendPlainMessage("written " + name);
        } catch (IOException e) {
            source.getSender().sendPlainMessage("cannot write " + name + ": " + e.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int reply(CommandSourceStack source, JsonObject json) {
        CommandSender sender = source.getSender();
        sender.sendPlainMessage(json.toString());
        return Command.SINGLE_SUCCESS;
    }
}
