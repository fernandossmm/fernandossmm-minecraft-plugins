package me.fernandossm.endcityshulkerrespawn.paper;

import me.fernandossm.endcityshulkerrespawn.core.CityData;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** One generated End city, ship included. An interface so the listeners can be tested without real structures. */
public interface City {

    World world();

    /** Identifies the city within its world: the lowest corner of its bounding box, "x,y,z". */
    String key();

    BoundingBox box();

    CityData load();

    void save(CityData data);

    default boolean contains(double x, double y, double z) {
        BoundingBox b = box();
        return x >= b.getMinX() && x <= b.getMaxX() && y >= b.getMinY() && y <= b.getMaxY() && z >= b.getMinZ() && z <= b.getMaxZ();
    }
}
