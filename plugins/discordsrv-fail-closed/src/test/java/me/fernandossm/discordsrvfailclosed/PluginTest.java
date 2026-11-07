package me.fernandossm.discordsrvfailclosed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.server.ServerLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

/**
 * The plugin on a mock server, with a stand-in named DiscordSRV that can be enabled and disabled, and whose
 * linked-account setting the test sets ({@link #requireLink}). Reading that setting from the real DiscordSRV is
 * covered by {@link GateTest} and the end-to-end suite.
 */
class PluginTest {

    private ServerMock server;
    private DiscordSrvFailClosed plugin;
    private final List<LogRecord> logged = new ArrayList<>();
    /** The stand-in DiscordSRV's "Require linked account to play"; null when it cannot be read. */
    private volatile Boolean requireLink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DiscordSrvFailClosed.class);
        plugin.useForTests(new Gate(server.getPluginManager()::getPlugin, discordSrv -> {
            if (requireLink == null) {
                throw new NoSuchMethodException("config");
            }
            return requireLink;
        }, plugin.getLogger()));
        plugin.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A plugin named DiscordSRV, enabled, with the requirement as given (null: it cannot be read). */
    private PluginMock discordSrv(Boolean requireLink) {
        this.requireLink = requireLink;
        return MockBukkit.createMockPlugin("DiscordSRV");
    }

    /**
     * A login, as the server raises it: on another thread. Every constructor but the server's own (which needs a
     * live connection) is deprecated for removal; the event is the same.
     */
    @SuppressWarnings("removal")
    private AsyncPlayerPreLoginEvent login(String name) {
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(name, InetAddress.getLoopbackAddress(), UUID.randomUUID());
        server.getPluginManager().callEventAsynchronously(event);
        server.getScheduler().waitAsyncEventsFinished();
        return event;
    }

    private void assertRefused(AsyncPlayerPreLoginEvent event) {
        assertEquals(Result.KICK_OTHER, event.getLoginResult());
        assertEquals(plugin.settings().message(), event.kickMessage());
    }

    private List<String> messages(Level level) {
        return logged.stream().filter(r -> r.getLevel() == level).map(LogRecord::getMessage).toList();
    }

    @Test
    void withoutDiscordSrvEveryLoginIsRefused() {
        assertRefused(login("Alex"));
        assertRefused(login("Steve"));
        assertEquals(List.of("Refused the login of Alex: DiscordSRV is not installed",
                "Refused the login of Steve: DiscordSRV is not installed"), messages(Level.WARNING));
    }

    @Test
    void aDisabledDiscordSrvRefusesEveryLogin() {
        PluginMock dsrv = discordSrv(true);
        server.getPluginManager().disablePlugin(dsrv);
        assertRefused(login("Alex"));
        assertTrue(messages(Level.WARNING).getLast().endsWith("DiscordSRV is not enabled"));
    }

    @Test
    void anEnabledDiscordSrvRequiringLinksLetsLoginsThrough() {
        discordSrv(true);
        AsyncPlayerPreLoginEvent event = login("Alex");
        assertEquals(Result.ALLOWED, event.getLoginResult());
        assertTrue(messages(Level.WARNING).isEmpty());
    }

    @Test
    void anEnabledDiscordSrvNotRequiringLinksRefuses() {
        discordSrv(false);
        assertRefused(login("Alex"));
        assertEquals("Refused the login of Alex: DiscordSRV's \"Require linked account to play\" is not enabled",
                messages(Level.WARNING).getLast());
    }

    @Test
    void anUnreadableSettingRefuses() {
        discordSrv(null);
        assertRefused(login("Alex"));
        assertTrue(messages(Level.WARNING).getLast().endsWith("setting cannot be read"));
        assertTrue(messages(Level.SEVERE).getLast().startsWith("Reading DiscordSRV's"));
    }

    @Test
    void aDiscordSrvWithoutTheExpectedApiRefuses() {
        // The plugin's own reader, against a plugin named DiscordSRV that is not DiscordSRV (no config()).
        plugin.useForTests(new Gate(server.getPluginManager()::getPlugin, plugin.getLogger()));
        discordSrv(true);
        assertRefused(login("Alex"));
        assertTrue(messages(Level.WARNING).getLast().endsWith("setting cannot be read"));
    }

    @Test
    void theGateFollowsDiscordSrvWhileTheServerRuns() {
        PluginMock dsrv = discordSrv(true);
        assertEquals(Result.ALLOWED, login("Alex").getLoginResult());

        server.getPluginManager().disablePlugin(dsrv);
        assertRefused(login("Steve"));
        assertTrue(messages(Level.SEVERE).contains("DiscordSRV was disabled: every login is refused until it is enabled again"));

        server.getPluginManager().enablePlugin(dsrv);
        assertEquals(Result.ALLOWED, login("Steve").getLoginResult());
        assertEquals("DiscordSRV requires a linked account; logins are left to it", messages(Level.INFO).getLast());
    }

    @Test
    void theSettingIsReadOnEveryLogin() {
        discordSrv(true);
        assertEquals(Result.ALLOWED, login("Alex").getLoginResult());
        requireLink = false;
        assertRefused(login("Alex"));
        requireLink = true;
        assertEquals(Result.ALLOWED, login("Alex").getLoginResult());
    }

    @Test
    void aPluginAllowingLoginsEarlierCannotReopenTheServer() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void allow(AsyncPlayerPreLoginEvent event) {
                event.allow();
            }
        }, MockBukkit.createMockPlugin("Opener"));
        assertRefused(login("Alex"));
    }

    @Test
    void aLoginAlreadyRefusedKeepsItsReason() {
        Component banned = Component.text("You are banned");
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void ban(AsyncPlayerPreLoginEvent event) {
                event.disallow(Result.KICK_BANNED, banned);
            }
        }, MockBukkit.createMockPlugin("Bans"));
        AsyncPlayerPreLoginEvent event = login("Alex");
        assertEquals(Result.KICK_BANNED, event.getLoginResult());
        assertEquals(banned, event.kickMessage());
        assertTrue(messages(Level.WARNING).isEmpty());
    }

    @Test
    void aFailingCheckRefuses() {
        plugin.useForTests(new Gate(name -> {
            throw new IllegalStateException("boom");
        }, plugin.getLogger()));
        assertRefused(login("Alex"));
        assertTrue(messages(Level.WARNING).getLast().endsWith("the check itself failed"));
    }

    @Test
    void theStartupLogSaysWhetherTheServerIsOpen() {
        server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        assertEquals("Every login is refused: DiscordSRV is not installed", messages(Level.SEVERE).getLast());

        discordSrv(true);
        server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        assertEquals("DiscordSRV requires a linked account; logins are left to it", messages(Level.INFO).getLast());
    }

    @Test
    void aConfiguredMessageIsShown() {
        plugin.getConfig().set("message", "<red>Closed");
        plugin.saveConfig();
        plugin.reloadSettings();
        AsyncPlayerPreLoginEvent event = login("Alex");
        assertEquals(Result.KICK_OTHER, event.getLoginResult());
        assertEquals(Component.text("Closed", NamedTextColor.RED), event.kickMessage());
        assertNotEquals(Settings.DEFAULTS.message(), event.kickMessage());
    }
}
