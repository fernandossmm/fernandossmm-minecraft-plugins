package me.fernandossm.bluemapgriefprevention.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import me.fernandossm.bluemapgriefprevention.core.Outline.Point;

/** Which rectangles form one connected region, and the exact outline of their union. */
public final class RectUnion {

    private static final Comparator<Point> BY_XZ = Comparator.comparingLong(Point::x).thenComparingLong(Point::z);

    private RectUnion() {}

    /**
     * Groups of rectangles connected through overlaps or shared edges (corners alone do not connect), as
     * lists of indexes into {@code rects}, each ascending, ordered by their first index.
     */
    public static List<List<Integer>> groups(List<Rect> rects) {
        int[] parent = new int[rects.size()];
        Arrays.setAll(parent, i -> i);
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                if (rects.get(i).connects(rects.get(j))) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        Map<Integer, List<Integer>> byRoot = new LinkedHashMap<>();
        for (int i = 0; i < rects.size(); i++) {
            byRoot.computeIfAbsent(find(parent, i), r -> new ArrayList<>()).add(i);
        }
        return List.copyOf(byRoot.values());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /**
     * The outline of the union of the rectangles, on block edges: a claim of blocks 0..9 spans 0 to 10.
     * Normally one outline per connected group; rectangles meeting only at a corner give separate ones.
     * Deterministic: the same rectangles always give equal outlines, in any order.
     */
    public static List<Outline> outlines(List<Rect> rects) {
        if (rects.isEmpty()) {
            return List.of();
        }
        // Compress the coordinates: the cells between consecutive distinct edges are each wholly in or out.
        long[] xs = distinct(rects.stream().flatMap(r -> Stream.of((long) r.minX(), r.maxX() + 1L)).toList());
        long[] zs = distinct(rects.stream().flatMap(r -> Stream.of((long) r.minZ(), r.maxZ() + 1L)).toList());
        boolean[][] covered = new boolean[xs.length - 1][zs.length - 1];
        for (Rect r : rects) {
            for (int i = Arrays.binarySearch(xs, r.minX()); xs[i] < r.maxX() + 1L; i++) {
                for (int j = Arrays.binarySearch(zs, r.minZ()); zs[j] < r.maxZ() + 1L; j++) {
                    covered[i][j] = true;
                }
            }
        }

        // Boundary edges, each with the covered side on its left (x right, z up), so outer rings run
        // counter-clockwise and holes clockwise.
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < xs.length - 1; i++) {
            for (int j = 0; j < zs.length - 1; j++) {
                if (!covered[i][j]) {
                    continue;
                }
                Point p00 = new Point(xs[i], zs[j]);
                Point p10 = new Point(xs[i + 1], zs[j]);
                Point p11 = new Point(xs[i + 1], zs[j + 1]);
                Point p01 = new Point(xs[i], zs[j + 1]);
                if (!isCovered(covered, i, j - 1)) edges.add(new Edge(p00, p10));
                if (!isCovered(covered, i + 1, j)) edges.add(new Edge(p10, p11));
                if (!isCovered(covered, i, j + 1)) edges.add(new Edge(p11, p01));
                if (!isCovered(covered, i - 1, j)) edges.add(new Edge(p01, p00));
            }
        }
        Map<Point, List<Edge>> leaving = new HashMap<>();
        edges.forEach(e -> leaving.computeIfAbsent(e.from(), p -> new ArrayList<>()).add(e));
        Map<Point, Boolean> turnLeftAt = pinches(covered, xs, zs);

        List<List<Point>> outers = new ArrayList<>();
        List<List<Point>> holes = new ArrayList<>();
        Set<Edge> used = new HashSet<>();
        for (Edge start : edges) {
            if (used.contains(start)) {
                continue;
            }
            List<Point> ring = new ArrayList<>();
            Edge e = start;
            do {
                used.add(e);
                ring.add(e.from());
                e = next(e, leaving.get(e.to()), used, start, turnLeftAt.getOrDefault(e.to(), true));
            } while (e != start);
            List<Point> simple = normalize(withoutStraightPoints(ring));
            (Outline.signedArea(simple) > 0 ? outers : holes).add(simple);
        }

        // Each hole belongs to the smallest outer ring around it.
        Map<List<Point>, List<List<Point>>> holesOf = new LinkedHashMap<>();
        outers.forEach(o -> holesOf.put(o, new ArrayList<>()));
        for (List<Point> hole : holes) {
            double[] inside = insideHole(hole);
            outers.stream()
                    .filter(o -> contains(o, inside[0], inside[1]))
                    .min(Comparator.comparingLong(o -> Outline.signedArea(o)))
                    .ifPresent(o -> holesOf.get(o).add(hole));
        }
        return holesOf.entrySet().stream()
                .map(e -> new Outline(e.getKey(), e.getValue().stream().sorted(Comparator.comparing(h -> h.getFirst(), BY_XZ)).toList()))
                .sorted(Comparator.comparing(o -> o.outer().getFirst(), BY_XZ))
                .toList();
    }

    private record Edge(Point from, Point to) {

        long dx() {
            return Long.signum(to.x() - from.x());
        }

        long dz() {
            return Long.signum(to.z() - from.z());
        }
    }

    /**
     * The edge to follow from {@code e}. Only at a pinch (two claimed cells meeting diagonally at one point)
     * do two edges leave the point, one turning left and one right; see {@link #pinches}.
     */
    private static Edge next(Edge e, List<Edge> candidates, Set<Edge> used, Edge start, boolean turnLeft) {
        List<Edge> open = candidates.stream().filter(c -> !used.contains(c) || c == start).toList();
        if (open.size() == 1) {
            return open.getFirst();
        }
        for (Edge c : open) {
            boolean left = c.dx() == -e.dz() && c.dz() == e.dx();
            if (left == turnLeft) {
                return c;
            }
        }
        throw new IllegalStateException("no way on from " + e.to());
    }

    /**
     * How to go on at each pinch, keyed by its point. If the two claimed cells belong to separate regions
     * (they touch only there), turning left hugs each cell and keeps their outlines apart. If they are one
     * region (connected elsewhere, so the region encloses unclaimed land that reaches the pinch), turning
     * right hugs the unclaimed side and keeps the outline apart from the hole's ring. Either way every ring
     * is a simple polygon.
     */
    private static Map<Point, Boolean> pinches(boolean[][] covered, long[] xs, long[] zs) {
        int[][] region = regions(covered);
        Map<Point, Boolean> turnLeftAt = new HashMap<>();
        for (int i = 1; i < covered.length; i++) {
            for (int j = 1; j < covered[i].length; j++) {
                boolean sw = covered[i - 1][j - 1];
                boolean se = covered[i][j - 1];
                boolean nw = covered[i - 1][j];
                boolean ne = covered[i][j];
                if (sw && ne && !se && !nw) {
                    turnLeftAt.put(new Point(xs[i], zs[j]), region[i - 1][j - 1] != region[i][j]);
                } else if (se && nw && !sw && !ne) {
                    turnLeftAt.put(new Point(xs[i], zs[j]), region[i][j - 1] != region[i - 1][j]);
                }
            }
        }
        return turnLeftAt;
    }

    /** Labels the claimed cells by edge-connected region (flood fill); unclaimed cells are -1. */
    private static int[][] regions(boolean[][] covered) {
        int[][] region = new int[covered.length][];
        for (int i = 0; i < covered.length; i++) {
            region[i] = new int[covered[i].length];
            Arrays.fill(region[i], -1);
        }
        int next = 0;
        for (int i = 0; i < covered.length; i++) {
            for (int j = 0; j < covered[i].length; j++) {
                if (!covered[i][j] || region[i][j] >= 0) {
                    continue;
                }
                java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                queue.add(new int[] {i, j});
                region[i][j] = next;
                while (!queue.isEmpty()) {
                    int[] c = queue.poll();
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int a = c[0] + d[0];
                        int b = c[1] + d[1];
                        if (isCovered(covered, a, b) && region[a][b] < 0) {
                            region[a][b] = next;
                            queue.add(new int[] {a, b});
                        }
                    }
                }
                next++;
            }
        }
        return region;
    }

    private static boolean isCovered(boolean[][] covered, int i, int j) {
        return i >= 0 && j >= 0 && i < covered.length && j < covered[i].length && covered[i][j];
    }

    private static long[] distinct(List<Long> values) {
        return new TreeSet<>(values).stream().mapToLong(Long::longValue).toArray();
    }

    /** Drops the points in the middle of a straight run (left by the compressed grid's lines). */
    private static List<Point> withoutStraightPoints(List<Point> ring) {
        List<Point> corners = new ArrayList<>();
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            Point prev = ring.get((i + n - 1) % n);
            Point here = ring.get(i);
            Point next = ring.get((i + 1) % n);
            long cross = (here.x() - prev.x()) * (next.z() - here.z()) - (here.z() - prev.z()) * (next.x() - here.x());
            if (cross != 0) {
                corners.add(here);
            }
        }
        return corners;
    }

    /** The same ring, starting at its smallest point, so equal shapes give equal lists. */
    private static List<Point> normalize(List<Point> ring) {
        int start = 0;
        for (int i = 1; i < ring.size(); i++) {
            if (BY_XZ.compare(ring.get(i), ring.get(start)) < 0) {
                start = i;
            }
        }
        List<Point> out = new ArrayList<>(ring.size());
        for (int i = 0; i < ring.size(); i++) {
            out.add(ring.get((start + i) % ring.size()));
        }
        return out;
    }

    /** A point half a block inside a hole: to the right of its first edge, where the unclaimed side is. */
    private static double[] insideHole(List<Point> hole) {
        Point a = hole.get(0);
        Point b = hole.get(1);
        double dx = Long.signum(b.x() - a.x());
        double dz = Long.signum(b.z() - a.z());
        return new double[] {(a.x() + b.x()) / 2.0 + dz * 0.5, (a.z() + b.z()) / 2.0 - dx * 0.5};
    }

    /** Even-odd ray casting. */
    private static boolean contains(List<Point> ring, double x, double z) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            Point a = ring.get(i);
            Point b = ring.get(j);
            if ((a.z() > z) != (b.z() > z) && x < (double) (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }
}
