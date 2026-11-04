package me.fernandossm.endcityshulkerrespawn.core;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Durations as people write them in config files: 90s, 30m, 16h, 7d, or combined, 1d12h. */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhd])");

    private Durations() {}

    /** @throws IllegalArgumentException if the text is not a positive duration in this format */
    public static Duration parse(String text) {
        String compact = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher m = PART.matcher(compact);
        Duration total = Duration.ZERO;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) {
                break;
            }
            long n = Long.parseLong(m.group(1));
            total = total.plus(switch (m.group(2)) {
                case "s" -> Duration.ofSeconds(n);
                case "m" -> Duration.ofMinutes(n);
                case "h" -> Duration.ofHours(n);
                default -> Duration.ofDays(n);
            });
            end = m.end();
        }
        if (compact.isEmpty() || end != compact.length() || total.isZero()) {
            throw new IllegalArgumentException("'" + text + "' is not a duration like 30m, 16h or 1d12h");
        }
        return total;
    }

    /** The shortest form back: 16h, 1d12h, 45s. */
    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        StringBuilder out = new StringBuilder();
        long[] sizes = {86_400, 3_600, 60, 1};
        String[] units = {"d", "h", "m", "s"};
        for (int i = 0; i < sizes.length; i++) {
            if (seconds >= sizes[i]) {
                out.append(seconds / sizes[i]).append(units[i]);
                seconds %= sizes[i];
            }
        }
        return out.isEmpty() ? "0s" : out.toString();
    }
}
