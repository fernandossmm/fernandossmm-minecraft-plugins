package me.fernandossm.bluemapskinsrestorerheads;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import java.util.function.Consumer;
import me.fernandossm.bluemapskinsrestorerheads.paper.ClientTextures;
import me.fernandossm.bluemapskinsrestorerheads.paper.Heads;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/** BlueMap's player heads drawn from the skin each player has in game. */
public class BlueMapSkinsRestorerHeads extends JavaPlugin implements Listener {

    /** How often online players' skins are compared with their heads, in ticks. */
    private static final long CHECK_TICKS = 100;

    private @Nullable ClientTextures textures;
    private @Nullable Heads heads;
    private @Nullable SkinProvider previous;
    private final Consumer<BlueMapAPI> onBlueMapEnable = this::blueMapEnabled;

    @Override
    public void onEnable() {
        textures = new ClientTextures(getServer().getWorldContainer().toPath(), getServer().getMinecraftVersion(), getLogger());
        ClientTextures jar = textures;
        heads = new Heads(this, path -> jar.available() ? jar.apply(path) : null);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::checkAll, CHECK_TICKS, CHECK_TICKS);
        BlueMapAPI.onEnable(onBlueMapEnable);
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(onBlueMapEnable);
        BlueMapAPI.getInstance().ifPresent(api -> {
            if (previous != null && api.getPlugin().getSkinProvider() == heads) {
                api.getPlugin().setSkinProvider(previous);
            }
        });
        if (textures != null) {
            textures.close();
        }
    }

    /** On BlueMap's start and on every BlueMap reload: take over its skins, and draw the online players' heads. */
    private void blueMapEnabled(BlueMapAPI api) {
        SkinProvider current = api.getPlugin().getSkinProvider();
        if (current != heads) {
            previous = current;
            api.getPlugin().setSkinProvider(heads);
        }
        getServer().getScheduler().runTask(this, () -> {
            if (heads != null) {
                heads.forgetAll();
                checkAll();
            }
        });
    }

    private void checkAll() {
        if (heads == null) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            heads.check(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (heads != null) {
            heads.check(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (heads != null) {
            heads.forget(event.getPlayer().getUniqueId());
        }
    }
}
