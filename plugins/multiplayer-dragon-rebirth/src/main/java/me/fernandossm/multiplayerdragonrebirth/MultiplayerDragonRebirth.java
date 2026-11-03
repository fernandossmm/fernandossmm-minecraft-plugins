package me.fernandossm.multiplayerdragonrebirth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import me.fernandossm.multiplayerdragonrebirth.config.Settings;
import me.fernandossm.multiplayerdragonrebirth.core.FightTracker;
import me.fernandossm.multiplayerdragonrebirth.core.NewcomerPolicy;
import me.fernandossm.multiplayerdragonrebirth.paper.AdminCommands;
import me.fernandossm.multiplayerdragonrebirth.paper.Fights;
import me.fernandossm.multiplayerdragonrebirth.paper.PaperFights;
import me.fernandossm.multiplayerdragonrebirth.paper.Summoner;
import me.fernandossm.multiplayerdragonrebirth.paper.Wiring;
import me.fernandossm.multiplayerdragonrebirth.store.PlayerStore;
import me.fernandossm.multiplayerdragonrebirth.store.StoreException;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

// Not final: MockBukkit loads plugins in tests by subclassing them.
public class MultiplayerDragonRebirth extends JavaPlugin {

    private volatile Settings settings = Settings.DEFAULTS;
    private final FightTracker tracker = new FightTracker();
    private PlayerStore store;
    private Fights fights;
    private Summoner summoner;
    private boolean commandsRegistered;

    @Override
    public void onEnable() {
        store = null;
        summoner = null;
        saveDefaultConfig();
        settings = Settings.from(getConfig(), getLogger());
        try {
            store = PlayerStore.open(getDataFolder().toPath().resolve("players.json"), getLogger());
        } catch (StoreException e) {
            getLogger().severe(e.getMessage());
            getLogger().severe("Disabled so that no player becomes a newcomer by mistake. Fix or restore players.json, then restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (store.created()) {
            firstRun();
        }
        PaperFights paperFights = new PaperFights(this::settings, new NamespacedKey(this, "summoning_crystal"));
        fights = paperFights;
        summoner = Wiring.wire(this, paperFights);
        // Paper accepts lifecycle handlers once per plugin instance; the command reads this plugin's
        // current state, so the first registration serves later re-enables too.
        if (!commandsRegistered) {
            new AdminCommands(this).register();
            commandsRegistered = true;
        }
    }

    @Override
    public void onDisable() {
        if (summoner != null) {
            summoner.cancelAll();
        }
        if (store != null) {
            store.close();
        }
    }

    private void firstRun() {
        switch (settings.firstRun()) {
            case MARK_EXISTING -> getLogger().info("First run: " + markExistingPlayers()
                    + " players who have joined this server before count as having fought the dragon (first-run: MARK_EXISTING)");
            case NONE -> getLogger().info("First run: every player is a newcomer (first-run: NONE)");
        }
        // Written before players can join, so a crash now cannot make the next start a first run again.
        store.flush();
    }

    /** Records every player who has ever joined the server; returns how many were not recorded yet. */
    public int markExistingPlayers() {
        Instant now = Instant.now();
        Map<UUID, PlayerStore.Entry> entries = new LinkedHashMap<>();
        for (OfflinePlayer player : getServer().getOfflinePlayers()) {
            String name = player.getName();
            entries.put(player.getUniqueId(), new PlayerStore.Entry(name != null ? name : "", now, PlayerStore.Reason.EXISTING_PLAYER, null));
        }
        for (Player player : getServer().getOnlinePlayers()) {
            entries.putIfAbsent(player.getUniqueId(), new PlayerStore.Entry(player.getName(), now, PlayerStore.Reason.EXISTING_PLAYER, null));
        }
        return store.markAllDone(entries);
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
    }

    public Settings settings() {
        return settings;
    }

    public NewcomerPolicy policy() {
        return new NewcomerPolicy(store::isDone, settings.freeTheEndCountsAsDone());
    }

    public PlayerStore store() {
        return store;
    }

    public Fights fights() {
        return fights;
    }

    public FightTracker tracker() {
        return tracker;
    }

    public Summoner summoner() {
        return summoner;
    }

    /** Replaces the real dragon fights; every component looks fights up through {@link #fights()}. */
    void useFightsForTests(Fights fights) {
        this.fights = fights;
    }
}
