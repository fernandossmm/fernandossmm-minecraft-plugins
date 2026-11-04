package me.fernandossm.bluemapgriefprevention.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import me.fernandossm.bluemapgriefprevention.core.Outline.Point;
import org.junit.jupiter.api.Test;

class RectUnionTest {

    private static Point p(long x, long z) {
        return new Point(x, z);
    }

    @Test
    void oneClaimIsItsRectangleOnBlockEdges() {
        List<Outline> outlines = RectUnion.outlines(List.of(new Rect(0, 0, 9, 9)));
        assertEquals(List.of(new Outline(List.of(p(0, 0), p(10, 0), p(10, 10), p(0, 10)), List.of())), outlines);
    }

    @Test
    void claimsSideBySideBecomeOneRectangle() {
        List<Outline> outlines = RectUnion.outlines(List.of(new Rect(0, 0, 9, 9), new Rect(10, 0, 19, 9)));
        assertEquals(List.of(p(0, 0), p(20, 0), p(20, 10), p(0, 10)), outlines.getFirst().outer());
        assertEquals(1, outlines.size());
    }

    @Test
    void anLShapeHasSixCorners() {
        List<Outline> outlines = RectUnion.outlines(List.of(
                new Rect(0, 0, 9, 9), new Rect(10, 0, 19, 9), new Rect(0, 10, 9, 19)));
        assertEquals(1, outlines.size());
        assertEquals(6, outlines.getFirst().outer().size());
        assertEquals(300, outlines.getFirst().area());
    }

    @Test
    void aRingOfClaimsHasAHole() {
        List<Rect> ring = List.of(
                new Rect(0, 0, 29, 9),    // north
                new Rect(0, 20, 29, 29),  // south
                new Rect(0, 10, 9, 19),   // west
                new Rect(20, 10, 29, 19)); // east
        List<Outline> outlines = RectUnion.outlines(ring);
        assertEquals(1, outlines.size());
        Outline o = outlines.getFirst();
        assertEquals(List.of(p(0, 0), p(30, 0), p(30, 30), p(0, 30)), o.outer());
        assertEquals(1, o.holes().size());
        assertEquals(Set.of(p(10, 10), p(20, 10), p(20, 20), p(10, 20)), Set.copyOf(o.holes().getFirst()));
        assertEquals(900 - 100, o.area());
    }

    @Test
    void cornersAloneDoNotConnect() {
        List<Rect> rects = List.of(new Rect(0, 0, 9, 9), new Rect(10, 10, 19, 19));
        assertEquals(List.of(List.of(0), List.of(1)), RectUnion.groups(rects));
        assertEquals(2, RectUnion.outlines(rects).size(), "two shapes, touching at a point");
    }

    /**
     * Claims around an unclaimed square that reaches the outline at one corner, (10, 10): the outline and the
     * hole touch there, but each is its own simple ring (found by randomUnionsAreExact).
     */
    @Test
    void aHoleTouchingTheOutlineAtACornerStaysAHole() {
        List<Rect> rects = List.of(
                new Rect(0, -10, 9, 9),    // west of the hole, and below
                new Rect(10, -10, 29, -1), // south of the hole
                new Rect(20, 0, 29, 19),   // east of the hole
                new Rect(10, 10, 19, 19)); // north of the hole, meeting the west claim only at (10, 10)
        List<Outline> outlines = RectUnion.outlines(rects);
        assertEquals(1, outlines.size());
        Outline o = outlines.getFirst();
        assertEquals(1, o.holes().size());
        assertEquals(Set.of(p(10, 0), p(20, 0), p(20, 10), p(10, 10)), Set.copyOf(o.holes().getFirst()));
        assertEquals(o.outer().size(), new HashSet<>(o.outer()).size());
        assertEquals(200 + 200 + 200 + 100, o.area());
    }

    @Test
    void groupsFollowEdgesAndOverlaps() {
        List<Rect> rects = List.of(
                new Rect(0, 0, 9, 9),
                new Rect(100, 100, 109, 109),
                new Rect(10, 0, 19, 9),    // edge with 0
                new Rect(105, 105, 120, 120), // overlaps 1
                new Rect(20, 10, 29, 19)); // only a corner with 2
        assertEquals(List.of(List.of(0, 2), List.of(1, 3), List.of(4)), RectUnion.groups(rects));
    }

    @Test
    void touchingForLessThanABlockIsAnEdgeOnlyIfTheRangesOverlap() {
        assertTrue(new Rect(0, 0, 9, 9).connects(new Rect(10, 9, 19, 19)), "share the edge at z = 9");
        assertFalse(new Rect(0, 0, 9, 9).connects(new Rect(10, 10, 19, 19)), "corner only");
        assertFalse(new Rect(0, 0, 9, 9).connects(new Rect(11, 0, 19, 9)), "a one-block gap");
    }

    @Test
    void farAwayCoordinatesWork() {
        Outline o = RectUnion.outlines(List.of(new Rect(29_999_000, -29_999_990, 29_999_990, -29_999_000))).getFirst();
        assertEquals(991L * 991L, o.area());
    }

    /**
     * Thousands of random sets of small, crowded rectangles (overlaps, shared edges, corners, holes, rings
     * pinched at a corner): the outline's area always equals the union's, every point is a real corner,
     * and the result does not depend on the rectangles' order.
     */
    @Test
    void randomUnionsAreExact() {
        Random random = new Random(26_2);
        for (int round = 0; round < 5000; round++) {
            List<Rect> rects = new ArrayList<>();
            int n = 1 + random.nextInt(8);
            for (int i = 0; i < n; i++) {
                int x = random.nextInt(12);
                int z = random.nextInt(12);
                rects.add(new Rect(x, z, x + random.nextInt(5), z + random.nextInt(5)));
            }
            Set<Long> blocks = new HashSet<>();
            for (Rect r : rects) {
                for (int x = r.minX(); x <= r.maxX(); x++) {
                    for (int z = r.minZ(); z <= r.maxZ(); z++) {
                        blocks.add(((long) x << 32) | z);
                    }
                }
            }
            List<Outline> outlines = RectUnion.outlines(rects);
            long area = outlines.stream().mapToLong(Outline::area).sum();
            assertEquals(blocks.size(), area, "round " + round + ": " + rects);
            for (Outline o : outlines) {
                assertTrue(Outline.signedArea(o.outer()) > 0, "outer rings run counter-clockwise");
                assertCornersOnly(o.outer(), rects);
                for (List<Point> hole : o.holes()) {
                    assertTrue(Outline.signedArea(hole) < 0, "holes run clockwise");
                    assertCornersOnly(hole, rects);
                }
                assertEquals(o.outer().size(), new HashSet<>(o.outer()).size(), "no point twice in a ring: " + rects);
            }
            List<Rect> shuffled = new ArrayList<>(rects);
            Collections.shuffle(shuffled, random);
            assertEquals(outlines, RectUnion.outlines(shuffled), "order does not matter: " + rects);
        }
    }

    private static void assertCornersOnly(List<Point> ring, List<Rect> rects) {
        for (int i = 0; i < ring.size(); i++) {
            Point prev = ring.get((i + ring.size() - 1) % ring.size());
            Point here = ring.get(i);
            Point next = ring.get((i + 1) % ring.size());
            assertTrue(prev.x() == here.x() || prev.z() == here.z(), "axis-aligned: " + rects);
            assertNotEquals(prev.x() == here.x(), here.x() == next.x(), "a corner, not a straight point: " + ring + " " + rects);
        }
    }
}
