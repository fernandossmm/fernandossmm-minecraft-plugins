package me.fernandossm.multiplayerdragonrebirth.paper;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Server-wide messages from config.yml. */
final class Broadcasts {

    private Broadcasts() {}

    static void send(String template, Logger log, TagResolver... placeholders) {
        if (template == null || template.isBlank()) {
            return;
        }
        try {
            Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(template, placeholders));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Cannot send message '" + template + "'", e);
        }
    }

    static TagResolver newcomers(Collection<String> names) {
        return Placeholder.unparsed("newcomers", String.join(", ", names));
    }

    static TagResolver world(World world) {
        return Placeholder.unparsed("world", world.getName());
    }

    static TagResolver seconds(int seconds) {
        return Placeholder.unparsed("seconds", Integer.toString(seconds));
    }
}
