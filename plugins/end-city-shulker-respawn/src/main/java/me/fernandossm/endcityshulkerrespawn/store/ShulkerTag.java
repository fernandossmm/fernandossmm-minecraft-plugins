package me.fernandossm.endcityshulkerrespawn.store;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Marks a shulker as the owner of a city's spot. Stored on the entity, so it follows the shulker wherever it
 * teleports or is moved to, and is saved with it.
 *
 * @param city        the city's key (see {@code City#key})
 * @param spot        the spot's index in the city
 * @param generation  the city's generation when the tag was written; a tag from an older one is void
 */
public record ShulkerTag(String city, int spot, int generation) {

    public static final class Keys {

        private final NamespacedKey city;
        private final NamespacedKey spot;
        private final NamespacedKey generation;

        public Keys(Plugin plugin) {
            city = new NamespacedKey(plugin, "city");
            spot = new NamespacedKey(plugin, "spot");
            generation = new NamespacedKey(plugin, "generation");
        }

        public void write(Entity shulker, ShulkerTag tag) {
            PersistentDataContainer pdc = shulker.getPersistentDataContainer();
            pdc.set(city, PersistentDataType.STRING, tag.city());
            pdc.set(spot, PersistentDataType.INTEGER, tag.spot());
            pdc.set(generation, PersistentDataType.INTEGER, tag.generation());
        }

        public Optional<ShulkerTag> read(Entity shulker) {
            PersistentDataContainer pdc = shulker.getPersistentDataContainer();
            String c = pdc.get(city, PersistentDataType.STRING);
            Integer s = pdc.get(spot, PersistentDataType.INTEGER);
            Integer g = pdc.get(generation, PersistentDataType.INTEGER);
            return c == null || s == null || g == null ? Optional.empty() : Optional.of(new ShulkerTag(c, s, g));
        }
    }
}
