package me.fernandossm.bluemapgriefprevention.core;

/** A claim's area in blocks, corners included: from (minX, minZ) to (maxX, maxZ). */
public record Rect(int minX, int minZ, int maxX, int maxZ) {

    public Rect {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("empty rectangle " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        }
    }

    public long area() {
        return ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
    }

    /** Overlapping, or touching along an edge of at least one block. Touching only at a corner is not. */
    public boolean connects(Rect o) {
        boolean xOverlap = minX <= o.maxX && o.minX <= maxX;
        boolean zOverlap = minZ <= o.maxZ && o.minZ <= maxZ;
        boolean xTouch = maxX + 1 == o.minX || o.maxX + 1 == minX;
        boolean zTouch = maxZ + 1 == o.minZ || o.maxZ + 1 == minZ;
        return (xOverlap && zOverlap) || (xTouch && zOverlap) || (zTouch && xOverlap);
    }
}
