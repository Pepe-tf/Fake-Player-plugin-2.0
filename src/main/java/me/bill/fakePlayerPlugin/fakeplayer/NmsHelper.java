package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.util.FppLogger;

public final class NmsHelper {

    private NmsHelper() {}

    public static ClassLoader findNmsClassLoader() {

        try {
            Class<?> craftServerClass = getCraftClass("CraftServer");
            Method getServer = craftServerClass.getMethod("getServer");
            Object mcServer = getServer.invoke(craftServerClass.cast(Bukkit.getServer()));
            Class<?> c = mcServer.getClass();
            while (c != null && c != Object.class) {
                ClassLoader cl = c.getClassLoader();
                if (cl != null && canLoadNms(cl)) return cl;
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {
        }

        try {
            Class<?> craftPlayerClass = getCraftClass("entity.CraftPlayer");
            Method getHandle = craftPlayerClass.getMethod("getHandle");
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    Object nmsPlayer = getHandle.invoke(craftPlayerClass.cast(p));
                    Class<?> c = nmsPlayer.getClass();
                    while (c != null && c != Object.class) {
                        ClassLoader cl = c.getClassLoader();
                        if (cl != null && canLoadNms(cl)) return cl;
                        c = c.getSuperclass();
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        while (cl != null) {
            if (canLoadNms(cl)) return cl;
            cl = cl.getParent();
        }

        return null;
    }

    private static boolean canLoadNms(ClassLoader cl) {
        for (String probe : new String[] {
            "net.minecraft.server.players.PlayerList",
            "net.minecraft.server.MinecraftServer",
            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket"
        }) {
            try {
                cl.loadClass(probe);
                return true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return false;
    }

    public static Object getServerLevel(World world) {
        try {
            Class<?> craftWorldClass = getCraftClass("CraftWorld");
            Method getHandle = craftWorldClass.getMethod("getHandle");
            return getHandle.invoke(craftWorldClass.cast(world));
        } catch (Exception e) {
            FppLogger.warn("NmsHelper.getServerLevel failed: " + e.getMessage());
            return null;
        }
    }

    static Class<?> getCraftClass(String suffix) throws ClassNotFoundException {
        try {
            return Class.forName("org.bukkit.craftbukkit." + suffix);
        } catch (ClassNotFoundException ignored) {
        }
        String[] parts = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
        if (parts.length >= 4) return Class.forName("org.bukkit.craftbukkit." + parts[3] + "." + suffix);
        throw new ClassNotFoundException("Cannot resolve CraftBukkit class: " + suffix);
    }
}
