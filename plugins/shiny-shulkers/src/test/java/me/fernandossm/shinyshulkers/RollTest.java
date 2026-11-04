package me.fernandossm.shinyshulkers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RollTest {

    private static final List<String> COLORS = List.of("a", "b", "c", "d");

    @Test
    void oneInOneIsAlwaysShiny() {
        SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < 1000; i++) {
            assertTrue(Roll.roll(1, COLORS, random).isPresent());
        }
    }

    @Test
    void oneInFiftyIsShinyAboutTwoPercentOfTheTime() {
        SplittableRandom random = new SplittableRandom(42);
        int rolls = 200_000;
        long shiny = 0;
        for (int i = 0; i < rolls; i++) {
            if (Roll.roll(50, COLORS, random).isPresent()) {
                shiny++;
            }
        }
        double rate = shiny / (double) rolls;
        assertTrue(Math.abs(rate - 0.02) < 0.002, "rate " + rate);
    }

    @Test
    void everyColourIsEquallyLikely() {
        SplittableRandom random = new SplittableRandom(7);
        Map<String, Integer> counts = new HashMap<>();
        int rolls = 80_000;
        for (int i = 0; i < rolls; i++) {
            Roll.roll(1, COLORS, random).ifPresent(c -> counts.merge(c, 1, Integer::sum));
        }
        assertEquals(COLORS.size(), counts.size());
        for (int count : counts.values()) {
            assertTrue(Math.abs(count - rolls / COLORS.size()) < rolls / 100, counts::toString);
        }
    }

    @Test
    void noColoursMeansNeverShiny() {
        assertEquals(Optional.empty(), Roll.roll(1, List.of(), new SplittableRandom(1)));
    }
}
