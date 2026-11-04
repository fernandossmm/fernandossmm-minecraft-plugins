package me.fernandossm.bluemapgriefprevention.core;

import java.util.List;

/**
 * A polygon on block edges, with holes. Points are corners only (no point in the middle of a straight
 * edge). The outer ring runs counter-clockwise and holes clockwise, with x east and z south.
 */
public record Outline(List<Point> outer, List<List<Point>> holes) {

    public record Point(long x, long z) {}

    public Outline {
        outer = List.copyOf(outer);
        holes = holes.stream().map(List::copyOf).toList();
    }

    /** Area of the outer ring minus the holes. */
    public long area() {
        long area = Math.abs(signedArea(outer));
        for (List<Point> hole : holes) {
            area -= Math.abs(signedArea(hole));
        }
        return area;
    }

    /** Shoelace formula: positive for a counter-clockwise ring. */
    static long signedArea(List<Point> ring) {
        long twice = 0;
        for (int i = 0; i < ring.size(); i++) {
            Point a = ring.get(i);
            Point b = ring.get((i + 1) % ring.size());
            twice += a.x() * b.z() - b.x() * a.z();
        }
        return twice / 2;
    }
}
