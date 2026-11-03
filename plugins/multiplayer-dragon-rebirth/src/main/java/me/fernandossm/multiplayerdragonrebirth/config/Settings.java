package me.fernandossm.multiplayerdragonrebirth.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** config.yml, parsed and validated once per load. Invalid values fall back to the default with a warning. */
public record Settings(
        Set<String> worlds,
        int countdownSeconds,
        boolean triggerOnJoin,
        FirstRun firstRun,
        boolean freeTheEndCountsAsDone,
        boolean eggPerNewcomer,
        String countdownMessage,
        String firstTimeKillMessage) {

    public enum FirstRun { MARK_EXISTING, NONE }

    public static final Settings DEFAULTS = new Settings(
            Set.of(), 10, true, FirstRun.MARK_EXISTING, false, false,
            "<light_purple>A dragon rises for <newcomers> in <seconds> seconds.",
            "<gold>The dragon has fallen! Its egg and power await.");

    public static Settings from(ConfigurationSection config, Logger log) {
        List<String> worlds = config.getStringList("worlds");

        int countdown = config.getInt("countdown-seconds", DEFAULTS.countdownSeconds);
        if (countdown < 0) {
            log.warning("countdown-seconds must be 0 or more, not " + countdown + "; using " + DEFAULTS.countdownSeconds);
            countdown = DEFAULTS.countdownSeconds;
        }

        FirstRun firstRun = DEFAULTS.firstRun;
        String firstRunValue = config.getString("first-run", firstRun.name());
        try {
            firstRun = FirstRun.valueOf(firstRunValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("first-run must be MARK_EXISTING or NONE, not '" + firstRunValue + "'; using " + firstRun);
        }

        return new Settings(
                Set.copyOf(worlds),
                countdown,
                config.getBoolean("trigger-on-join", DEFAULTS.triggerOnJoin),
                firstRun,
                config.getBoolean("free-the-end-counts-as-done", DEFAULTS.freeTheEndCountsAsDone),
                config.getBoolean("egg-per-newcomer", DEFAULTS.eggPerNewcomer),
                config.getString("messages.countdown", DEFAULTS.countdownMessage),
                config.getString("messages.first-time-kill", DEFAULTS.firstTimeKillMessage));
    }

    public boolean manages(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
