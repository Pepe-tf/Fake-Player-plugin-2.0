package me.bill.fakePlayerPlugin.util;

import org.bukkit.Location;
import org.bukkit.World;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;

public final class WorldGuardHelper {

    private WorldGuardHelper() {}

    public static boolean isPvpAllowed(Location location) {
        if (location == null || location.getWorld() == null) return true;
        try {
            RegionQuery query =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(location);

            StateFlag.State state = query.queryState(wgLoc, null, Flags.PVP);
            return state != StateFlag.State.DENY;
        } catch (Exception e) {

            return true;
        }
    }

    public static Location findSafeLocation(World world) {
        if (world == null) return null;
        Location spawn = world.getSpawnLocation();

        for (int yOffset = 0; yOffset <= 10; yOffset++) {
            Location check = spawn.clone().add(0.5, yOffset, 0.5);
            if (isPvpAllowed(check)) return check;
        }

        for (int radius = 5; radius <= 50; radius += 5) {
            for (int x = -radius; x <= radius; x += 5) {
                for (int z = -radius; z <= radius; z += 5) {
                    Location check = spawn.clone().add(x + 0.5, 0, z + 0.5);
                    if (!isPvpAllowed(check)) continue;
                    int y = world.getHighestBlockYAt(check) + 1;
                    check.setY(y);
                    if (isPvpAllowed(check)) return check;
                }
            }
        }

        return null;
    }
}
