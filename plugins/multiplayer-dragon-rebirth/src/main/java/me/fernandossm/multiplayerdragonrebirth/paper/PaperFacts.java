package me.fernandossm.multiplayerdragonrebirth.paper;

import me.fernandossm.multiplayerdragonrebirth.core.PlayerFacts;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/** Reads {@link PlayerFacts} from an online player. */
public final class PaperFacts {

    public static final String EXEMPT_PERMISSION = "multiplayerdragonrebirth.exempt";
    public static final String ADMIN_PERMISSION = "multiplayerdragonrebirth.admin";
    private static final NamespacedKey FREE_THE_END = NamespacedKey.minecraft("end/kill_dragon");

    private PaperFacts() {}

    /** @param readAdvancement whether "Free the End" matters; reading it is skipped otherwise */
    public static PlayerFacts of(Player player, boolean readAdvancement) {
        GameMode mode = player.getGameMode();
        return new PlayerFacts(
                player.getUniqueId(),
                player.getName(),
                mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                isVanished(player),
                player.hasPermission(EXEMPT_PERMISSION),
                readAdvancement && hasFreeTheEnd(player));
    }

    /** The convention shared by vanish plugins (SuperVanish, PremiumVanish, EssentialsX and others). */
    private static boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFreeTheEnd(Player player) {
        Advancement advancement = Bukkit.getAdvancement(FREE_THE_END);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }
}
