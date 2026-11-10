package me.fernandossm.bluemapgriefprevention.core;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The texts on the map, from config.yml's {@code messages}: plain text with {@code <placeholder>}s. A missing
 * key keeps its English default.
 */
public record Messages(
        String claim,
        String claimOf,
        String adminClaim,
        String unknownOwner,
        String size,
        String sizeSeveral,
        String thousandsSeparator) {

    public static final Messages DEFAULTS = new Messages(
            "Claim", "Claim of <owner>", "Admin claim", "unknown", "<blocks> blocks", "<claims> claims · <blocks> blocks", ",");

    private static final Pattern PLACEHOLDER = Pattern.compile("<([a-z]+)>");

    public static Messages from(ConfigurationSection c) {
        Messages d = DEFAULTS;
        return new Messages(
                c.getString("messages.claim", d.claim),
                c.getString("messages.claim-of", d.claimOf),
                c.getString("messages.admin-claim", d.adminClaim),
                c.getString("messages.unknown-owner", d.unknownOwner),
                c.getString("messages.size", d.size),
                c.getString("messages.size-several", d.sizeSeveral),
                c.getString("messages.thousands-separator", d.thousandsSeparator));
    }

    /** A whole number with this language's thousands separator, e.g. 12,345 or 12.345. */
    public String number(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(",", thousandsSeparator);
    }

    /**
     * The template with its placeholders filled in one pass, so a value (a player's name) is never read as a
     * placeholder. Unknown placeholders stay as they are.
     */
    public static String fill(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
