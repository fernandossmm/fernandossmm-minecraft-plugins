package me.fernandossm.bluemapbannermarkers.core;

import java.util.UUID;

/**
 * A banner someone named with a name tag.
 *
 * @param scale the map icon's size: 1 for everyone's waypoints, 2 for those an admin enlarged
 */
public record Waypoint(int id, String name, UUID owner, String ownerName, String world, int x, int y, int z,
                       BannerDesign design, int scale) {

    public boolean at(String world, int x, int y, int z) {
        return this.world.equals(world) && this.x == x && this.y == y && this.z == z;
    }

    public boolean inChunk(String world, int chunkX, int chunkZ) {
        return this.world.equals(world) && (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }

    public Waypoint withName(String name) {
        return new Waypoint(id, name, owner, ownerName, world, x, y, z, design, scale);
    }

    public Waypoint withOwnerName(String ownerName) {
        return new Waypoint(id, name, owner, ownerName, world, x, y, z, design, scale);
    }

    public Waypoint withScale(int scale) {
        return new Waypoint(id, name, owner, ownerName, world, x, y, z, design, scale);
    }
}
