package me.fernandossm.discordsrvfailclosed;

import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Whether DiscordSRV's linked-account requirement is in force: DiscordSRV is enabled and the setting its own check
 * reads, {@code Require linked account to play.Enabled}, is on. Anything else, including an error, is a refusal.
 * Asked afresh on every check, from whatever thread; nothing is cached.
 */
public final class Gate {

    static final String DISCORDSRV = "DiscordSRV";
    static final String LINKING_ENABLED = "Require linked account to play.Enabled";

    /** Reads the linked-account requirement from an enabled DiscordSRV; throws when it cannot. */
    @FunctionalInterface
    interface LinkSetting {
        boolean required(Plugin discordSrv) throws Exception;
    }

    private final Function<String, Plugin> plugins;
    private final LinkSetting linkSetting;
    private final Logger log;

    /** {@code plugins} looks a plugin up by name, returning null when it is not installed. */
    public Gate(Function<String, Plugin> plugins, Logger log) {
        this(plugins, discordSrv -> requiresLinkedAccount(discordSrv.getClass()), log);
    }

    Gate(Function<String, Plugin> plugins, LinkSetting linkSetting, Logger log) {
        this.plugins = plugins;
        this.linkSetting = linkSetting;
        this.log = log;
    }

    /** Empty when logins may go on to DiscordSRV's own check; otherwise why they are refused. */
    public Optional<Refusal> check() {
        try {
            Plugin discordSrv = plugins.apply(DISCORDSRV);
            if (discordSrv == null) {
                return Optional.of(Refusal.NOT_INSTALLED);
            }
            if (!discordSrv.isEnabled()) {
                return Optional.of(Refusal.DISABLED);
            }
            boolean required;
            try {
                required = linkSetting.required(discordSrv);
            } catch (Exception | LinkageError e) {
                log.log(Level.SEVERE, "Reading DiscordSRV's " + LINKING_ENABLED + " failed; refusing the login", e);
                return Optional.of(Refusal.SETTING_UNREADABLE);
            }
            return required ? Optional.empty() : Optional.of(Refusal.LINKING_OFF);
        } catch (RuntimeException | LinkageError e) {
            log.log(Level.SEVERE, "Checking DiscordSRV failed; refusing the login", e);
            return Optional.of(Refusal.ERROR);
        }
    }

    /**
     * The setting as DiscordSRV holds it now: {@code DiscordSRV.config().getBoolean(LINKING_ENABLED)}, the call its
     * own check makes. Not linking.yml on disk, which DiscordSRV reads only when it starts or reloads. By reflection,
     * so this plugin needs no DiscordSRV jar to build and fails closed if a DiscordSRV update changes the API.
     */
    static boolean requiresLinkedAccount(Class<?> discordSrvClass) throws ReflectiveOperationException {
        Object config = discordSrvClass.getMethod("config").invoke(null);
        Object value = config.getClass().getMethod("getBoolean", String.class).invoke(config, LINKING_ENABLED);
        return Boolean.TRUE.equals(value);
    }
}
