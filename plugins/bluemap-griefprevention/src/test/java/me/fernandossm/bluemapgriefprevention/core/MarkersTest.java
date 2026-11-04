package me.fernandossm.bluemapgriefprevention.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarkersTest {

    private static final UUID ALICE = UUID.fromString("10920508-d5d8-3eed-93d2-92f193afe7d7");
    private static final UUID BOB = UUID.fromString("faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b");
    private static final Settings CLICK = Settings.DEFAULTS;

    private static ClaimView claim(long id, UUID owner, String name, int x, int z, int size) {
        return new ClaimView(id, "world", new Rect(x, z, x + size - 1, z + size - 1), 64, owner, name, false);
    }

    private static ClaimView admin(long id, int x, int z) {
        return new ClaimView(id, "world", new Rect(x, z, x + 9, z + 9), 70, null, "", true);
    }

    private static Settings with(Settings.OwnerShown owner) {
        Settings d = Settings.DEFAULTS;
        return new Settings(d.worlds(), d.layerLabel(), d.hiddenByDefault(), owner, d.playerClaims(), d.adminClaims(),
                d.adminColor(), d.fillOpacity(), d.lineOpacity(), d.lineWidth(), d.resyncInterval());
    }

    @Test
    void aPlayersTouchingClaimsAreOneMarker() {
        Map<String, MarkerSpec> world = Markers.of(List.of(
                claim(5, ALICE, "Alice", 0, 0, 10),
                claim(7, ALICE, "Alice", 10, 0, 10),
                claim(9, ALICE, "Alice", 0, 10, 10)), CLICK).get("world");

        assertEquals(Set.of("claim-" + ALICE + "-5"), world.keySet());
        MarkerSpec m = world.values().iterator().next();
        assertEquals(6, m.outline().outer().size(), "one L-shaped outline");
        assertEquals("<b>Claim of Alice</b><br>3 claims · 300 blocks", m.detail());
    }

    @Test
    void differentOwnersAreNeverMerged() {
        Map<String, MarkerSpec> world = Markers.of(List.of(
                claim(1, ALICE, "Alice", 0, 0, 10),
                claim(2, BOB, "Bob", 10, 0, 10)), CLICK).get("world");
        assertEquals(2, world.size());
        assertNotEquals(world.get("claim-" + ALICE + "-1").color(), world.get("claim-" + BOB + "-2").color());
    }

    @Test
    void separatePiecesOfLandAreSeparateMarkers() {
        Map<String, MarkerSpec> world = Markers.of(List.of(
                claim(1, ALICE, "Alice", 0, 0, 10),
                claim(2, ALICE, "Alice", 500, 500, 10),
                claim(3, ALICE, "Alice", 10, 10, 10)), CLICK).get("world"); // corner of claim 1 only
        assertEquals(Set.of("claim-" + ALICE + "-1", "claim-" + ALICE + "-2", "claim-" + ALICE + "-3"), world.keySet());
        assertEquals("<b>Claim of Alice</b><br>100 blocks", world.get("claim-" + ALICE + "-1").detail());
    }

    @Test
    void theOwnerIsNamedWhereTheConfigSays() {
        List<ClaimView> claims = List.of(claim(1, ALICE, "Alice", 0, 0, 10));
        MarkerSpec click = Markers.of(claims, with(Settings.OwnerShown.CLICK)).get("world").values().iterator().next();
        MarkerSpec list = Markers.of(claims, with(Settings.OwnerShown.LIST)).get("world").values().iterator().next();
        MarkerSpec none = Markers.of(claims, with(Settings.OwnerShown.NONE)).get("world").values().iterator().next();

        assertEquals("Claim", click.label());
        assertTrue(click.detail().contains("Alice"));
        assertEquals("Claim of Alice", list.label());
        assertTrue(list.detail().contains("Alice"));
        assertEquals("Claim", none.label());
        assertFalse(none.detail().contains("Alice"));
    }

    @Test
    void playerColoursAreTheirLocatorBarColours() {
        MarkerSpec m = Markers.of(List.of(claim(1, ALICE, "Alice", 0, 0, 10)), CLICK).get("world").values().iterator().next();
        assertEquals(LocatorColor.of(ALICE), m.color());
        assertEquals(0.8f, m.lineOpacity());
        assertEquals(0.2f, m.fillOpacity());
        assertEquals(2, m.lineWidth());
    }

    @Test
    void adminClaimsAreBrightPurpleAndMergeTogether() {
        Map<String, MarkerSpec> world = Markers.of(List.of(admin(3, 0, 0), admin(4, 10, 0)), CLICK).get("world");
        assertEquals(Set.of("claim-admin-3"), world.keySet());
        MarkerSpec m = world.get("claim-admin-3");
        assertEquals("Admin claim", m.label());
        assertEquals("<b>Admin claim</b><br>2 claims · 200 blocks", m.detail());
        assertEquals(0xBF00FF, m.color());
        assertEquals(70f, m.y());
    }

    @Test
    void namesAreEscaped() {
        MarkerSpec m = Markers.of(List.of(claim(1, ALICE, "<b>x</b>&'\"", 0, 0, 10)), with(Settings.OwnerShown.LIST))
                .get("world").values().iterator().next();
        assertEquals("<b>Claim of &lt;b&gt;x&lt;/b&gt;&amp;&#39;&quot;</b><br>100 blocks", m.detail());
        assertEquals("Claim of <b>x</b>&'\"", m.label(), "labels are plain text in BlueMap");
    }

    @Test
    void aMergedOverlayIsDrawnAtItsHighestSurface() {
        ClaimView high = new ClaimView(1, "world", new Rect(0, 0, 9, 9), 80, ALICE, "Alice", false);
        ClaimView low = new ClaimView(2, "world", new Rect(10, 0, 19, 9), 50, ALICE, "Alice", false);
        assertEquals(80f,Markers.of(List.of(high, low), CLICK).get("world").values().iterator().next().y());
    }

    @Test
    void filtersByKindAndWorld() {
        Settings d = Settings.DEFAULTS;
        Settings onlyAdmin = new Settings(Set.of("world"), d.layerLabel(), false, d.owner(), false, true,
                d.adminColor(), d.fillOpacity(), d.lineOpacity(), d.lineWidth(), Duration.ofMinutes(5));
        List<ClaimView> claims = List.of(
                claim(1, ALICE, "Alice", 0, 0, 10),
                admin(2, 100, 100),
                new ClaimView(3, "world_nether", new Rect(0, 0, 9, 9), 64, null, "", true));
        Map<String, Map<String, MarkerSpec>> markers = Markers.of(claims, onlyAdmin);
        assertEquals(Set.of("world"), markers.keySet());
        assertEquals(Set.of("claim-admin-2"), markers.get("world").keySet());
    }

    @Test
    void theSameClaimsGiveEqualMarkersInAnyOrder() {
        List<ClaimView> claims = new ArrayList<>(List.of(
                claim(1, ALICE, "Alice", 0, 0, 10), claim(2, ALICE, "Alice", 10, 0, 10),
                claim(3, BOB, "Bob", 50, 50, 10), admin(4, 200, 200)));
        Map<String, Map<String, MarkerSpec>> first = Markers.of(claims, CLICK);
        Collections.shuffle(claims);
        assertEquals(first, Markers.of(claims, CLICK));
    }
}
