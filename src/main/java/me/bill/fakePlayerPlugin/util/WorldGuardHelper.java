package me.bill.fakePlayerPlugin.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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

    /**
     * Forces WorldGuard to re-validate the player's session at their current location.
     * Call this after a bot teleports into or out of a WG-protected region so that
     * flags (PVP, build, game-mode, etc.) are re-evaluated immediately.
     *
     * <p>Implementation: because WorldGuard is {@code compileOnly}, all
     * SessionManager / Session interaction is done via reflection so the code
     * compiles without those classes on the classpath.
     */
    public static void refreshPlayerSession(Player player) {
        if (player == null || !player.isOnline()) return;
        Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wgPlugin == null || !wgPlugin.isEnabled()) return;
        try {
            // Use WorldGuard singleton (available at compile time) to reach the platform.
            Object platform = WorldGuard.getInstance().getPlatform();
            if (platform != null) {
                Object sm = platform.getClass().getMethod("getSessionManager").invoke(platform);
                if (sm != null) {
                    Object wrapped = platform.getClass()
                            .getMethod("wrapPlayer", Player.class)
                            .invoke(platform, player);
                    initializeSession(sm, wrapped, player);
                    return;
                }
            }

            reflectionInitializeFallback(wgPlugin, player);
        } catch (Throwable t) {
            FppLogger.debug("WorldGuardHelper: WG session refresh skipped: " + t.getMessage());
        }
    }

    /**
     * Forces a cold re-initialisation of the player's WorldGuard session via reflection.
     *
     * <p>Calling {@code sm.get(owner)} only returns a stale session whose handlers
     * retain old region/world state.  To force every handler (EntryFlag, GameModeFlag,
     * BuildFlag, PVP, etc.) to re-evaluate, we must drop the existing session and let
     * the manager recreate a fresh one - or if that's not possible, we force the
     * internal {@code initialize()} method to run again.
     */
    private static void initializeSession(Object sm, Object wrapped, Player bukkitPlayer) {
        try {
            // 1) Try to remove the stale session first so get() creates a fresh one.
            tryRemoveSession(sm, wrapped);
        } catch (Throwable ignored) {
        }

        try {
            // 2) Fetch the session object via reflection.
            Class<?> sessionOwnerClass = Class.forName(
                    "com.sk89q.worldguard.session.SessionOwner",
                    false,
                    sm.getClass().getClassLoader());
            Object session = sm.getClass().getMethod("get", sessionOwnerClass).invoke(sm, wrapped);
            if (session == null) return;

            // 3) Force internal handlers to re-run initialize() for the current location.
            java.lang.reflect.Method initMethod = session.getClass().getDeclaredMethod("initialize", sessionOwnerClass);
            initMethod.setAccessible(true);
            initMethod.invoke(session, wrapped);

            FppLogger.debug("WorldGuardHelper: re-initialised WG session for " + bukkitPlayer.getName());
        } catch (NoSuchMethodException ignored) {
            // In newer WG versions initialize() may be gone; fall back to get() alone.
            FppLogger.debug("WorldGuardHelper: refreshed WG session for " + bukkitPlayer.getName() + " (get only)");
        } catch (Throwable t) {
            FppLogger.debug("WorldGuardHelper: WG session init failed for " + bukkitPlayer.getName() + ": "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    /**
     * Attempts to remove the cached session from the internal session map so that
     * the next {@code get()} call creates a brand-new Session object with cold handler
     * state.
     */
    private static void tryRemoveSession(Object sm, Object wrapped) throws Exception {
        for (java.lang.reflect.Field f : sm.getClass().getDeclaredFields()) {
            if (!java.util.Map.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object val = f.get(sm);
            if (val instanceof java.util.Map map) {
                map.remove(wrapped);
                FppLogger.debug("WorldGuardHelper: removed stale session for " + wrapped);
                return;
            }
        }
    }

    /** Reflection fallback for older WG builds / edge cases. */
    private static void reflectionInitializeFallback(Plugin wgPlugin, Player player) {
        try {
            ClassLoader loader = wgPlugin.getClass().getClassLoader();
            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin", false, loader);
            Object instance = wgPluginClass.getMethod("inst").invoke(null);
            Object platform = wgPluginClass.getMethod("getPlatform").invoke(instance);
            Object sm = platform.getClass().getMethod("getSessionManager").invoke(platform);
            Object wrapped =
                    platform.getClass().getMethod("wrapPlayer", Player.class).invoke(platform, player);

            // Remove stale session if possible.
            for (java.lang.reflect.Field f : sm.getClass().getDeclaredFields()) {
                if (!java.util.Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object val = f.get(sm);
                if (val instanceof java.util.Map map) {
                    map.remove(wrapped);
                    break;
                }
            }

            Class<?> sessionOwnerClass = Class.forName("com.sk89q.worldguard.session.SessionOwner", false, loader);
            Object session = sm.getClass().getMethod("get", sessionOwnerClass).invoke(sm, wrapped);
            if (session != null) {
                try {
                    java.lang.reflect.Method initMethod =
                            session.getClass().getDeclaredMethod("initialize", sessionOwnerClass);
                    initMethod.setAccessible(true);
                    initMethod.invoke(session, wrapped);
                } catch (NoSuchMethodException ignored) {
                }
            }
            FppLogger.debug("WorldGuardHelper: refreshed WG session for " + player.getName() + " (reflect)");
        } catch (Throwable t) {
            FppLogger.debug("WorldGuardHelper: WG session refresh skipped (reflect): " + t.getMessage());
        }
    }
}
