package me.fernandossm.shinyshulkers;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** One shulker's chance: one in {@code oneIn} to be shiny, then any of the colours with equal probability. */
public final class Roll {

    private Roll() {}

    public static <C> Optional<C> roll(int oneIn, List<C> colors, RandomGenerator random) {
        if (colors.isEmpty() || random.nextInt(oneIn) != 0) {
            return Optional.empty();
        }
        return Optional.of(colors.get(random.nextInt(colors.size())));
    }
}
