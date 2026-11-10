package me.fernandossm.bluemapbannermarkers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.fernandossm.bluemapbannermarkers.core.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class WaypointsMessagesTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void theDefaultsReadAsBefore() {
        Component added = Waypoints.render(Messages.DEFAULTS.added(), Placeholder.unparsed("name", "Home"));
        assertEquals("Waypoint \"Home\" added to the map.", plain(added));
        Component notYours = Waypoints.render(Messages.DEFAULTS.notYours(), Placeholder.unparsed("owner", "Alice"),
                Placeholder.unparsed("name", "Base"));
        assertEquals("This banner is Alice's waypoint, \"Base\". Only Alice can rename it.", plain(notYours));
    }

    @Test
    void namesAreTextNotFormatting() {
        Component c = Waypoints.render("<green>Punto \"<name>\" añadido.", Placeholder.unparsed("name", "<red>Base</red>"));
        assertEquals("Punto \"<red>Base</red>\" añadido.", plain(c));
    }

    @Test
    void anEmptyMessageIsNotSent() {
        assertNull(Waypoints.render("  "));
    }
}
