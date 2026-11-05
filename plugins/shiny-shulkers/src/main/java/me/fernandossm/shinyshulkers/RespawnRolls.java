package me.fernandossm.shinyshulkers;

import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import me.fernandossm.endcityshulkerrespawn.api.CityShulkerRespawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * With End City Shulker Respawn: each shulker it brings back to a city gets its own roll, as the city's
 * generated ones did, so a city's colours change as its shulkers come back. Only registered when that
 * plugin is installed, since this class needs its event.
 */
final class RespawnRolls implements Listener {

    private final Supplier<Settings> settings;
    private final Supplier<RandomGenerator> random;

    RespawnRolls(Supplier<Settings> settings, Supplier<RandomGenerator> random) {
        this.settings = settings;
        this.random = random;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRespawn(CityShulkerRespawnEvent event) {
        Settings s = settings.get();
        if (event.getColor() == null && s.manages(event.getLocation().getWorld().getName())) {
            Roll.roll(s.oneIn(), s.colors(), random.get()).ifPresent(event::setColor);
        }
    }
}
