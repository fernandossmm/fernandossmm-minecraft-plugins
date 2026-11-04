package me.fernandossm.endcityshulkerrespawn;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import me.fernandossm.endcityshulkerrespawn.config.Settings;
import me.fernandossm.endcityshulkerrespawn.paper.AdminCommands;
import me.fernandossm.endcityshulkerrespawn.paper.Cities;
import me.fernandossm.endcityshulkerrespawn.paper.CityKeeper;
import me.fernandossm.endcityshulkerrespawn.paper.Claims;
import me.fernandossm.endcityshulkerrespawn.paper.GriefPreventionClaims;
import me.fernandossm.endcityshulkerrespawn.paper.PaperCities;
import me.fernandossm.endcityshulkerrespawn.paper.ShulkerListener;
import me.fernandossm.endcityshulkerrespawn.store.CityCodec;
import me.fernandossm.endcityshulkerrespawn.store.ShulkerTag;
import org.bukkit.entity.Shulker;
import org.bukkit.plugin.java.JavaPlugin;

// Not final: MockBukkit loads plugins in tests by subclassing them.
public class EndCityShulkerRespawn extends JavaPlugin {

    private volatile Settings settings = Settings.DEFAULTS;
    private Cities cities;
    private CityKeeper keeper;
    private Claims griefPrevention = Claims.NONE;
    private LongSupplier clock = System::currentTimeMillis;
    private Consumer<Shulker> keepForever = shulker -> shulker.setRemoveWhenFarAway(false);
    private boolean commandsRegistered;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig(), getLogger());
        if (getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
            griefPrevention = new GriefPreventionClaims();
            getLogger().info("GriefPrevention found: spots inside claims are not refilled"
                    + (settings.respectGriefPreventionClaims() ? "" : " (turned off in config.yml)"));
        }
        cities = new PaperCities(this::settings, new CityCodec(this));
        keeper = new CityKeeper(getLogger(), this::settings, this::cities, new ShulkerTag.Keys(this), this::claims,
                () -> clock.getAsLong(), shulker -> keepForever.accept(shulker));
        getServer().getPluginManager().registerEvents(new ShulkerListener(this, this::cities, keeper), this);
        // Paper accepts lifecycle handlers once per plugin instance.
        if (!commandsRegistered) {
            new AdminCommands(this).register();
            commandsRegistered = true;
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
    }

    public Settings settings() {
        return settings;
    }

    public Cities cities() {
        return cities;
    }

    public CityKeeper keeper() {
        return keeper;
    }

    /** GriefPrevention's claims while the config respects them; otherwise nothing is claimed. */
    public Claims claims() {
        return settings.respectGriefPreventionClaims() ? griefPrevention : Claims.NONE;
    }

    public LongSupplier clock() {
        return () -> clock.getAsLong();
    }

    /** Every component looks cities up through {@link #cities()}, so tests can replace them after enabling. */
    void useForTests(Cities cities, Claims claims, LongSupplier clock, Consumer<Shulker> keepForever) {
        this.cities = cities;
        this.griefPrevention = claims;
        this.clock = clock;
        this.keepForever = keepForever;
    }
}
