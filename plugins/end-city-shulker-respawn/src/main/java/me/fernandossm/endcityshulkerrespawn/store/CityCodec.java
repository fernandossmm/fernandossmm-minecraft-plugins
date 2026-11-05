package me.fernandossm.endcityshulkerrespawn.store;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.fernandossm.endcityshulkerrespawn.core.CityData;
import me.fernandossm.endcityshulkerrespawn.core.Spot;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Reads and writes a city's {@link CityData} in a persistent data container: the one Paper keeps on the
 * generated structure itself, so the data is saved with the world.
 */
public final class CityCodec {

    private static final int FORMAT = 1;

    private final NamespacedKey format;
    private final NamespacedKey generation;
    private final NamespacedKey spots;
    private final NamespacedKey learned;
    private final NamespacedKey x;
    private final NamespacedKey y;
    private final NamespacedKey z;
    private final NamespacedKey face;
    private final NamespacedKey due;

    public CityCodec(Plugin plugin) {
        format = new NamespacedKey(plugin, "format");
        generation = new NamespacedKey(plugin, "generation");
        spots = new NamespacedKey(plugin, "spots");
        learned = new NamespacedKey(plugin, "learned_chunks");
        x = new NamespacedKey(plugin, "x");
        y = new NamespacedKey(plugin, "y");
        z = new NamespacedKey(plugin, "z");
        face = new NamespacedKey(plugin, "face");
        due = new NamespacedKey(plugin, "due");
    }

    /** Empty if nothing has been stored yet, or if it was written by a newer, unknown format. */
    public CityData read(PersistentDataContainer pdc) {
        Integer version = pdc.get(format, PersistentDataType.INTEGER);
        if (version == null || version > FORMAT) {
            return CityData.empty();
        }
        List<Spot> list = new ArrayList<>();
        for (PersistentDataContainer s : pdc.getOrDefault(spots, PersistentDataType.LIST.dataContainers(), List.of())) {
            Spot.Face[] faces = Spot.Face.values();
            byte f = s.getOrDefault(face, PersistentDataType.BYTE, (byte) 0);
            list.add(new Spot(
                    s.getOrDefault(x, PersistentDataType.INTEGER, 0),
                    s.getOrDefault(y, PersistentDataType.INTEGER, 0),
                    s.getOrDefault(z, PersistentDataType.INTEGER, 0),
                    faces[Math.floorMod(f, faces.length)],
                    s.getOrDefault(due, PersistentDataType.LONG, Spot.ALIVE)));
        }
        Set<Long> chunks = new LinkedHashSet<>();
        for (long key : pdc.getOrDefault(learned, PersistentDataType.LONG_ARRAY, new long[0])) {
            chunks.add(key);
        }
        return new CityData(list, chunks, pdc.getOrDefault(generation, PersistentDataType.INTEGER, 0));
    }

    public void write(PersistentDataContainer pdc, CityData data) {
        PersistentDataAdapterContext context = pdc.getAdapterContext();
        List<PersistentDataContainer> list = new ArrayList<>();
        for (Spot spot : data.spots()) {
            PersistentDataContainer s = context.newPersistentDataContainer();
            s.set(x, PersistentDataType.INTEGER, spot.x());
            s.set(y, PersistentDataType.INTEGER, spot.y());
            s.set(z, PersistentDataType.INTEGER, spot.z());
            s.set(face, PersistentDataType.BYTE, (byte) spot.face().ordinal());
            s.set(due, PersistentDataType.LONG, spot.dueAt());
            list.add(s);
        }
        pdc.set(format, PersistentDataType.INTEGER, FORMAT);
        pdc.set(generation, PersistentDataType.INTEGER, data.generation());
        pdc.set(spots, PersistentDataType.LIST.dataContainers(), list);
        pdc.set(learned, PersistentDataType.LONG_ARRAY, data.learnedChunks().stream().mapToLong(Long::longValue).toArray());
    }
}
