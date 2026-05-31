package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;

public final class NmsReflection {

    private static boolean initialized = false;
    private static boolean failed = false;

    private static Class<?> craftServerClass;
    private static Class<?> craftWorldClass;
    private static Class<?> minecraftServerClass;
    private static Class<?> serverLevelClass;
    private static Class<?> serverPlayerClass;
    private static Class<?> gameProfileClass;
    private static Class<?> clientInformationClass;
    private static Class<?> commonListenerCookieClass;

    private static Method craftServerGetHandle;
    private static Method craftWorldGetHandle;

    private static Constructor<?> gameProfileConstructor;
    private static Constructor<?> serverPlayerConstructor;
    private static Constructor<?> clientInformationConstructor;
    private static Constructor<?> commonListenerCookieConstructor;

    private NmsReflection() {}

    public static synchronized void init() {
        if (initialized || failed) return;

        try {

            String version =
                    Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

            craftServerClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftServer");
            craftWorldClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftWorld");

            ClassLoader nmsLoader = craftServerClass.getClassLoader();
            minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
            serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
            serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer");

            gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");

            craftServerGetHandle = craftServerClass.getMethod("getServer");
            craftWorldGetHandle = craftWorldClass.getMethod("getHandle");

            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);

            initialized = true;
        } catch (Exception e) {
            failed = true;
            throw new RuntimeException("Failed to initialize NMS reflection", e);
        }
    }

    public static boolean isAvailable() {
        if (!initialized && !failed) init();
        return initialized;
    }

    public static Class<?> getServerPlayerClass() {
        if (!initialized) init();
        return serverPlayerClass;
    }

    public static Class<?> getGameProfileClass() {
        if (!initialized) init();
        return gameProfileClass;
    }

    public static Object createGameProfile(UUID uuid, String name) {
        try {
            if (!initialized) init();
            return gameProfileConstructor.newInstance(uuid, name);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GameProfile", e);
        }
    }

    public static Object getMinecraftServer() {
        try {
            if (!initialized) init();
            return craftServerGetHandle.invoke(Bukkit.getServer());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get MinecraftServer", e);
        }
    }

    public static Object getServerLevel(World world) {
        try {
            if (!initialized) init();
            Object craftWorld = craftWorldClass.cast(world);
            return craftWorldGetHandle.invoke(craftWorld);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ServerLevel", e);
        }
    }
}
