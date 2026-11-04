package me.fernandossm.bluemapgriefprevention.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.fernandossm.bluemapgriefprevention.core.ClaimView;
import me.fernandossm.bluemapgriefprevention.core.MarkerSpec;
import me.fernandossm.bluemapgriefprevention.core.Rect;
import me.fernandossm.bluemapgriefprevention.core.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class SyncTest {

    private ServerMock server;
    private PluginMock plugin;
    private final List<ClaimView> claims = new ArrayList<>();
    private final List<Map<String, Map<String, MarkerSpec>>> shown = new ArrayList<>();
    private boolean ready = true;
    private Sync sync;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        MapSink sink = new MapSink() {
            @Override
            public boolean ready() {
                return ready;
            }

            @Override
            public void show(Map<String, Map<String, MarkerSpec>> markersByWorld, Settings settings) {
                shown.add(markersByWorld);
            }
        };
        sync = new Sync(plugin, () -> Settings.DEFAULTS, () -> () -> List.copyOf(claims), sink);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void changesAreDrawnOnTheNextTickOnceForAll() {
        claims.add(new ClaimView(1, "world", new Rect(0, 0, 9, 9), 64, UUID.randomUUID(), "Alice", false));
        sync.requestSync();
        sync.requestSync();
        sync.requestSync();
        assertEquals(0, shown.size(), "not during the event: GriefPrevention has not applied the change yet");
        server.getScheduler().performTicks(1);
        assertEquals(1, shown.size());
        assertEquals(1, shown.getFirst().get("world").size());

        sync.requestSync();
        server.getScheduler().performTicks(1);
        assertEquals(2, shown.size(), "a later change is drawn again");
    }

    @Test
    void nothingIsDrawnBeforeTheMapIsUp() {
        ready = false;
        sync.requestSync();
        server.getScheduler().performTicks(1);
        sync.syncNow();
        assertEquals(0, shown.size());
    }

    @Test
    void theSafetyNetRedrawsAtTheInterval() {
        sync.startPeriodic();
        server.getScheduler().performTicks(5 * 60 * 20);
        assertEquals(1, shown.size());
        server.getScheduler().performTicks(5 * 60 * 20);
        assertEquals(2, shown.size());
        sync.stopPeriodic();
        server.getScheduler().performTicks(5 * 60 * 20);
        assertEquals(2, shown.size());
    }
}
