package me.fernandossm.bluemapbannermarkers.core;

import org.jspecify.annotations.Nullable;

/** Waypoint names and the HTML they appear in. */
public final class Names {

    /** As long as an anvil allows. */
    public static final int MAX_LENGTH = 50;

    private Names() {}

    /** A name tag's text as a waypoint name: no control characters, spaces collapsed; null if nothing is left. */
    public static @Nullable String clean(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String name = text.replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ").strip();
        if (name.isEmpty()) {
            return null;
        }
        return name.codePointCount(0, name.length()) <= MAX_LENGTH ? name
                : name.substring(0, name.offsetByCodePoints(0, MAX_LENGTH));
    }

    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
