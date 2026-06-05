package me.bill.fakePlayerPlugin.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;

/**
 * Shim for the WorldEdit soft-dependency.
 *
 * <p><b>ClassLoader guard:</b> never call any method in this class unless
 * {@code FakePlayerPlugin.isWorldEditAvailable()} returns {@code true}.  Paper's
 * PluginClassLoader will throw {@code NoClassDefFoundError} on the very first bytecode
 * verification pass if WorldEdit is absent and you reference a WE class anywhere in the
 * call stack.
 */
public final class WorldEditHelper {

    private WorldEditHelper() {}

    /**
     * Returns the two corners of the player's current WorldEdit selection, or {@code null} when
     * the player has no active selection or their selection is not a cuboid-like region.
     *
     * @return {@code Location[2]}{@code [0]} = minimum corner, {@code [1]} = maximum corner;
     * both in the player's current world. Returns {@code null} on any error.
     */
    @Nullable
    public static Location[] getSelection(Player player) {
        try {
            SessionManager sessions = WorldEdit.getInstance().getSessionManager();
            Actor actor = BukkitAdapter.adapt(player);
            LocalSession session = sessions.get(actor);

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());
            Region region = session.getSelection(weWorld);

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            World world = player.getWorld();
            return new Location[] {
                new Location(world, min.x(), min.y(), min.z()), new Location(world, max.x(), max.y(), max.z())
            };
        } catch (Exception e) {
            // com.sk89q.worldedit.IncompleteRegionException or anything else
            return null;
        }
    }
}
