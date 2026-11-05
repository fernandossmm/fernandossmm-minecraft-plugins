package me.fernandossm.bluemapbannermarkers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NamesAndSettingsTest {

    @Test
    void namesAreTrimmedCollapsedAndCapped() {
        assertEquals("Main base", Names.clean("  Main \t  base "));
        assertEquals("Home", Names.clean("Ho\u0000me"));
        assertNull(Names.clean("   "));
        assertNull(Names.clean(null));
        assertEquals(50, Names.clean("x".repeat(80)).length());
        assertEquals(50, Names.clean("😀".repeat(60)).codePointCount(0, 100), "capped by characters, not halves of them");
    }

    @Test
    void namesAreEscapedForThePopup() {
        assertEquals("&lt;img src=x onerror=&quot;a()&quot;&gt; &amp; &#39;", Names.escape("<img src=x onerror=\"a()\"> & '"));
    }

    @Test
    void settingsReadTheConfigAndRefuseNonsense() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("worlds", List.of("world"));
        c.set("max-per-player", -3);
        c.set("max-name-length", 51);
        c.set("layer.label", "Banners");
        c.set("layer.hidden-by-default", true);
        Settings s = Settings.from(c, Logger.getAnonymousLogger());
        assertEquals(Set.of("world"), s.worlds());
        assertEquals(5, s.maxPerPlayer());
        assertEquals(24, s.maxNameLength(), "longer than an anvil allows");
        c.set("max-name-length", 12);
        assertEquals(12, Settings.from(c, Logger.getAnonymousLogger()).maxNameLength());
        assertEquals(2, Names.length("😀😀"));
        assertEquals("Banners", s.layerLabel());
        assertTrue(s.hiddenByDefault());
        assertTrue(s.allows("world"));
        assertFalse(s.allows("world_nether"));
        assertTrue(Settings.DEFAULTS.allows("anything"));
    }
}
