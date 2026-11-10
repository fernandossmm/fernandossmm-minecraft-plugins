package me.fernandossm.bluemapbannermarkers.core;

import org.bukkit.configuration.ConfigurationSection;

/**
 * What players read, from config.yml's {@code messages}. Chat texts are MiniMessage templates; {@code placedBy}
 * is plain text for the map's popup. A missing key keeps its English default; an empty chat text is not sent.
 */
public record Messages(
        String worldNotAllowed,
        String notYours,
        String noBuild,
        String nameTooLong,
        String limitReached,
        String added,
        String renamed,
        String placedBy) {

    public static final Messages DEFAULTS = new Messages(
            "<red>Banners in this world cannot be waypoints.",
            "<red>This banner is <owner>'s waypoint, \"<name>\". Only <owner> can rename it.",
            "<red>You need permission to build here to make this banner a waypoint.",
            "<red>That name has <length> characters; waypoint names can have at most <max>. Rename the tag in an anvil.",
            "<red>You have <count> waypoints, the most allowed. Break one of your waypoint banners to make a new one.",
            "<green>Waypoint \"<name>\" added to the map.",
            "<green>Waypoint renamed to \"<name>\".",
            "Placed by <owner>");

    public static Messages from(ConfigurationSection c) {
        Messages d = DEFAULTS;
        return new Messages(
                c.getString("messages.world-not-allowed", d.worldNotAllowed),
                c.getString("messages.not-yours", d.notYours),
                c.getString("messages.no-build", d.noBuild),
                c.getString("messages.name-too-long", d.nameTooLong),
                c.getString("messages.limit-reached", d.limitReached),
                c.getString("messages.added", d.added),
                c.getString("messages.renamed", d.renamed),
                c.getString("messages.placed-by", d.placedBy));
    }

    /** The popup's "placed by" line as HTML, from a {@code placed-by} template: template and name both escaped. */
    public static String placedByHtml(String template, String owner) {
        String[] parts = template.split("<owner>", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append(Names.escape(owner));
            }
            out.append(Names.escape(parts[i]));
        }
        return out.toString();
    }
}
