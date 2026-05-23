package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeConnection;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeServerGamePacketListenerImpl;
import me.bill.fakePlayerPlugin.util.FppLogger;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class NmsPlayerSpawner {

  private static volatile boolean initialized = false;
  private static volatile boolean failed = false;

  private static Method craftPlayerGetHandleMethod;
  private static Method craftPlayerRefreshPlayerMethod;
  private static Method craftServerGetServerMethod;
  private static Method craftWorldGetHandleMethod;

  private static Class<?> minecraftServerClass;
  private static Class<?> serverLevelClass;
  private static Class<?> serverPlayerClass;
  private static Class<?> clientInformationClass;
  private static Class<?> connectionClass;
  private static Class<?> commonListenerCookieClass;
  private static Class<?> serverGamePacketListenerClass;
  private static Class<?> packetFlowClass;

  private static Constructor<?> gameProfileConstructor;
  private static Class<?> gameProfileClass;
  private static Method setPosMethod;
  private static Method doTickMethod;
  private static Method getPlayerListMethod;

  private static Field xoField;
  private static Field yoField;
  private static Field zoField;

  private static Field jumpingField;

  private static Field listedField;

  private static Field yHeadRotField;

  private static Field zzaField;

  private static Field xxaField;

  private static Field connectionFieldInPlayer;

  private static Method attackMethod;

  private static Method playerListRemoveMethod;

  private static Field playerDataStorageField;

  private static Method playerDataSaveMethod;

  private static Method getPlayerDirMethod;

  private static Object clientInfoDefault;

  private static final Set<UUID> firstTickSet = Collections.synchronizedSet(new HashSet<>());

  private static Object skinPartsDataAccessor = null;

  private static Method synchedEntityDataSetMethod = null;

  private static Field entityDataFieldForSkinParts = null;

  private NmsPlayerSpawner() {
  }

  public static synchronized void init() {
    if (initialized || failed) return;
    try {

      String packageName = Bukkit.getServer().getClass().getPackage().getName();
      String ver = packageName.substring(packageName.lastIndexOf('.') + 1);
      String cbPkg =
          ver.equals("craftbukkit") ? "org.bukkit.craftbukkit" : "org.bukkit.craftbukkit." + ver;
      FppLogger.debug("NmsPlayerSpawner: CraftBukkit package = " + cbPkg);

      Class<?> craftServerClass = Class.forName(cbPkg + ".CraftServer");
      Class<?> craftWorldClass = Class.forName(cbPkg + ".CraftWorld");
      Class<?> craftPlayerClass = Class.forName(cbPkg + ".entity.CraftPlayer");
      ClassLoader nmsLoader = craftServerClass.getClassLoader();

      craftServerGetServerMethod = craftServerClass.getMethod("getServer");
      craftWorldGetHandleMethod = craftWorldClass.getMethod("getHandle");
      craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");
      try {
        craftPlayerRefreshPlayerMethod = craftPlayerClass.getDeclaredMethod("refreshPlayer");
        craftPlayerRefreshPlayerMethod.setAccessible(true);
      } catch (NoSuchMethodException ignored) {
        craftPlayerRefreshPlayerMethod = null;
      }

      minecraftServerClass = nmsLoader.loadClass("net.minecraft.server.MinecraftServer");
      try {
        serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.ServerLevel");
        serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.ServerPlayer");
        FppLogger.debug("NmsPlayerSpawner: using Mojang-mapped NMS names");
      } catch (ClassNotFoundException e) {
        serverLevelClass = nmsLoader.loadClass("net.minecraft.server.level.WorldServer");
        serverPlayerClass = nmsLoader.loadClass("net.minecraft.server.level.EntityPlayer");
        FppLogger.debug("NmsPlayerSpawner: using Spigot-mapped NMS names");
      }

      try {
        connectionClass = nmsLoader.loadClass("net.minecraft.network.Connection");
      } catch (ClassNotFoundException e) {
        connectionClass = nmsLoader.loadClass("net.minecraft.network.NetworkManager");
      }
      try {
        commonListenerCookieClass =
            nmsLoader.loadClass("net.minecraft.server.network.CommonListenerCookie");
      } catch (ClassNotFoundException ignored) {
      }
      try {
        serverGamePacketListenerClass =
            nmsLoader.loadClass("net.minecraft.server.network.ServerGamePacketListenerImpl");
      } catch (ClassNotFoundException e) {
        try {
          serverGamePacketListenerClass =
              nmsLoader.loadClass("net.minecraft.server.network.PlayerConnection");
        } catch (ClassNotFoundException ignored) {
        }
      }
      try {
        packetFlowClass = nmsLoader.loadClass("net.minecraft.network.protocol.PacketFlow");
      } catch (ClassNotFoundException ignored) {
      }

      try {
        clientInformationClass =
            nmsLoader.loadClass("net.minecraft.server.level.ClientInformation");
        try {
          clientInfoDefault = clientInformationClass.getMethod("createDefault").invoke(null);
          FppLogger.debug("NmsPlayerSpawner: ClientInformation.createDefault() cached");
        } catch (Exception ignored) {
        }
      } catch (ClassNotFoundException ignored) {
      }

      gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
      gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);

      getPlayerListMethod = minecraftServerClass.getMethod("getPlayerList");

      for (Method m : serverPlayerClass.getMethods()) {
        if ("setPos".equals(m.getName()) && m.getParameterCount() == 3) {
          Class<?>[] p = m.getParameterTypes();
          if (p[0] == double.class && p[1] == double.class && p[2] == double.class) {
            setPosMethod = m;
            break;
          }
        }
      }
      if (setPosMethod == null)
        setPosMethod =
            findMethodBySignature(serverPlayerClass, 3, double.class, double.class, double.class);

      doTickMethod = findMethod(serverPlayerClass, "doTick", 0);
      if (doTickMethod == null) doTickMethod = findMethod(serverPlayerClass, "tick", 0);
      if (doTickMethod != null) {
        FppLogger.debug("NmsPlayerSpawner: doTick cached as " + doTickMethod.getName() + "()");
      } else {
        FppLogger.warn("NmsPlayerSpawner: doTick() not found - bots will have no physics");
      }

      Class<?> entityClass;
      try {
        entityClass = nmsLoader.loadClass("net.minecraft.world.entity.Entity");
      } catch (ClassNotFoundException e) {
        entityClass = serverPlayerClass;
      }
      xoField = findFieldByName(entityClass, "xo");
      yoField = findFieldByName(entityClass, "yo");
      zoField = findFieldByName(entityClass, "zo");
      FppLogger.debug(
          "NmsPlayerSpawner: xo/yo/zo fields " + (xoField != null ? "cached" : "not found"));

      try {
        Class<?> livingEntityClass = nmsLoader.loadClass("net.minecraft.world.entity.LivingEntity");
        jumpingField = findFieldByName(livingEntityClass, "jumping");
      } catch (ClassNotFoundException ignored) {

        jumpingField = findFieldByName(serverPlayerClass, "jumping");
      }
      FppLogger.debug(
          "NmsPlayerSpawner: jumping field "
              + (jumpingField != null ? "cached" : "not found - swim AI inactive"));

      listedField = findFieldByName(serverPlayerClass, "listed");
      FppLogger.debug(
          "NmsPlayerSpawner: listed field "
              + (listedField != null
              ? "cached"
              : "not found - tab unlist will use packet fallback"));

      yHeadRotField = findFieldByName(serverPlayerClass, "yHeadRot");
      FppLogger.debug(
          "NmsPlayerSpawner: yHeadRot field "
              + (yHeadRotField != null
              ? "cached"
              : "not found - head AI will rely on setRotation only"));

      zzaField = findFieldByName(serverPlayerClass, "zza");

      xxaField = findFieldByName(serverPlayerClass, "xxa");
      FppLogger.debug(
          "NmsPlayerSpawner: movement input fields "
              + (zzaField != null && xxaField != null
              ? "cached"
              : "not found - move command inactive"));

      if (serverGamePacketListenerClass != null) {
        connectionFieldInPlayer = findFieldByName(serverPlayerClass, "connection");
        if (connectionFieldInPlayer == null)
          connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerConnection");
        if (connectionFieldInPlayer == null)
          connectionFieldInPlayer = findFieldByName(serverPlayerClass, "playerGameConnection");
        if (connectionFieldInPlayer == null) {
          for (Field f : getAllDeclaredFields(serverPlayerClass)) {
            if (serverGamePacketListenerClass.isAssignableFrom(f.getType())
                || f.getType().isAssignableFrom(serverGamePacketListenerClass)) {
              f.setAccessible(true);
              connectionFieldInPlayer = f;
              break;
            }
          }
        }
        if (connectionFieldInPlayer != null) {
          FppLogger.debug(
              "NmsPlayerSpawner: connection field = " + connectionFieldInPlayer.getName());
        } else {
          FppLogger.warn(
              "NmsPlayerSpawner: ServerPlayer.connection field not found"
                  + " - fake listener injection will be skipped");
        }
      }

      try {
        Class<?> entityClassForAttack = nmsLoader.loadClass("net.minecraft.world.entity.Entity");
        attackMethod = findMethod(serverPlayerClass, "attack", 1, entityClassForAttack);
        if (attackMethod != null) {
          FppLogger.debug("NmsPlayerSpawner: attack(Entity) method cached");
        } else {
          FppLogger.warn(
              "NmsPlayerSpawner: attack(Entity) method not found - bots will use"
                  + " fallback damage");
        }
      } catch (Exception e) {
        FppLogger.warn("NmsPlayerSpawner: Failed to cache attack method: " + e.getMessage());
      }

      try {
        Class<?> playerListClass = getPlayerListMethod.getReturnType();
        playerListRemoveMethod = findMethod(playerListClass, "remove", 1);

        for (Field f : playerListClass.getDeclaredFields()) {
          String typeName = f.getType().getSimpleName();
          if (typeName.contains("WorldNBTStorage") || typeName.contains("PlayerDataStorage")) {
            f.setAccessible(true);
            playerDataStorageField = f;
            break;
          }
        }
        if (playerDataStorageField != null) {
          Class<?> storageClass = playerDataStorageField.getType();

          try {
            getPlayerDirMethod = storageClass.getMethod("getPlayerDir");
          } catch (Exception ignored) {
          }

          for (Method m : storageClass.getDeclaredMethods()) {
            if ("a".equals(m.getName())
                && m.getParameterCount() == 1
                && m.getReturnType() == void.class) {
              m.setAccessible(true);
              playerDataSaveMethod = m;
              break;
            }
          }
        }
        FppLogger.debug(
            "NmsPlayerSpawner: PlayerList lifecycle - remove="
                + (playerListRemoveMethod != null ? "ok" : "missing")
                + " storage="
                + (playerDataStorageField != null ? "ok" : "missing")
                + " save="
                + (playerDataSaveMethod != null ? "ok" : "missing")
                + " getPlayerDir="
                + (getPlayerDirMethod != null ? "ok" : "missing"));
      } catch (Exception e) {
        FppLogger.debug("NmsPlayerSpawner: PlayerList lifecycle init failed: " + e.getMessage());
      }

      initialized = true;
      FppLogger.info(
          "NmsPlayerSpawner initialised (doTick="
              + (doTickMethod != null)
              + ", connectionField="
              + (connectionFieldInPlayer != null)
              + ", attack="
              + (attackMethod != null)
              + ", playerDataDir="
              + (getPlayerDirMethod != null)
              + ")");

      try {
        Class<?> playerNmsClass;
        try {
          playerNmsClass = nmsLoader.loadClass("net.minecraft.world.entity.player.Player");
        } catch (ClassNotFoundException ignored) {
          playerNmsClass = serverPlayerClass;
        }

        Field spField = findFieldByName(playerNmsClass, "DATA_PLAYER_MODE_CUSTOMISATION");
        if (spField != null && Modifier.isStatic(spField.getModifiers())) {
          spField.setAccessible(true);
          skinPartsDataAccessor = spField.get(null);
        }

        Class<?> syncDataClass =
            nmsLoader.loadClass("net.minecraft.network.syncher.SynchedEntityData");
        for (Method m : syncDataClass.getDeclaredMethods()) {
          if ("set".equals(m.getName()) && m.getParameterCount() == 2) {
            m.setAccessible(true);
            synchedEntityDataSetMethod = m;
            break;
          }
        }

        entityDataFieldForSkinParts = findFieldByName(serverPlayerClass, "entityData");

        FppLogger.debug(
            "NmsPlayerSpawner: skin-parts init - accessor="
                + (skinPartsDataAccessor != null)
                + " entityData="
                + (entityDataFieldForSkinParts != null)
                + " setMethod="
                + (synchedEntityDataSetMethod != null));
      } catch (Exception e) {
        FppLogger.debug("NmsPlayerSpawner: skin-parts init failed (non-fatal): " + e.getMessage());
      }

    } catch (Exception e) {
      failed = true;
      FppLogger.error("NmsPlayerSpawner.init() failed: " + e.getMessage());
    }
  }

  public static boolean isAvailable() {
    if (!initialized && !failed) init();
    return initialized;
  }

  public static Player spawnFakePlayer(
      UUID uuid, String name, World world, double x, double y, double z) {
    return spawnFakePlayer(uuid, name, null, world, x, y, z, -1);
  }

  public static Player spawnFakePlayer(
      UUID uuid, String name, SkinProfile skin, World world, double x, double y, double z) {
    return spawnFakePlayer(uuid, name, skin, world, x, y, z, -1);
  }

  public static Player spawnFakePlayer(
      UUID uuid, String name, SkinProfile skin, World world, double x, double y, double z, int initialPing) {
    return spawnFakePlayer(uuid, name, skin, world, x, y, z, 0.0f, 0.0f, initialPing);
  }

  public static Player spawnFakePlayer(
      UUID uuid,
      String name,
      SkinProfile skin,
      World world,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      int initialPing) {
    if (!isAvailable()) {
      FppLogger.warn("NmsPlayerSpawner not available - cannot spawn " + name);
      return null;
    }
    try {

      Object gameProfile = gameProfileConstructor.newInstance(uuid, name);
      if (skin != null && skin.isValid()) {
        try {
          gameProfile = SkinProfileInjector.createGameProfile(gameProfileClass, uuid, name, skin);
          FppLogger.debug("NmsPlayerSpawner: injected skin for '" + name + "'");
        } catch (Exception e) {
          FppLogger.warn("NmsPlayerSpawner: skin injection failed: " + e.getMessage());
        }
      }

      Object minecraftServer = craftServerGetServerMethod.invoke(Bukkit.getServer());
      Object serverLevel = craftWorldGetHandleMethod.invoke(world);
      Object clientInfo = getClientInformation();

      Object serverPlayer =
          createServerPlayer(minecraftServer, serverLevel, gameProfile, clientInfo);
      if (serverPlayer == null) {
        FppLogger.warn("NmsPlayerSpawner: failed to create ServerPlayer for " + name);
        return null;
      }

      if (setPosMethod != null) setPosMethod.invoke(serverPlayer, x, y, z);
      initPreviousPosition(serverPlayer, x, y, z);

      if (initialPing >= 0) {
        setPingNms(serverPlayer, initialPing);
      }

      Object conn = createFakeConnection();
      if (conn == null) {
        FppLogger.warn("NmsPlayerSpawner: failed to create fake connection for " + name);
        return null;
      }
      prepareJoinCompatibility(conn, serverPlayer, uuid, name);

      FppLogger.debug("NmsPlayerSpawner: spawning '" + name + "' uuid=" + uuid);
      ensurePlayerDataExists(minecraftServer, serverPlayer, name, uuid);

      boolean placed = placePlayer(minecraftServer, conn, serverPlayer, gameProfile, clientInfo);
      if (!placed) {
        cleanupFailedSpawn(minecraftServer, serverPlayer, name);
        FppLogger.warn("NmsPlayerSpawner: placeNewPlayer failed for " + name);
        return null;
      }

      if (setPosMethod != null) setPosMethod.invoke(serverPlayer, x, y, z);
      initPreviousPosition(serverPlayer, x, y, z);

      injectFakeListener(minecraftServer, conn, serverPlayer, gameProfile, clientInfo);

      Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
      Object entity = getBukkitEntity.invoke(serverPlayer);
      if (entity instanceof Player result) {
        applyRotation(result, yaw, pitch);
        result.setGameMode(GameMode.SURVIVAL);
        setListed(result, true);

        forceAllSkinParts(result);
        firstTickSet.add(uuid);
        FppLogger.debug("NmsPlayerSpawner: spawned " + name + " (" + uuid + ")");
        return result;
      }

      FppLogger.warn("NmsPlayerSpawner: getBukkitEntity did not return a Player for " + name);
      return null;

    } catch (Exception e) {
      FppLogger.error(
          "NmsPlayerSpawner.spawnFakePlayer failed for " + name + ": " + e.getMessage());
      FppLogger.debug(Arrays.toString(e.getStackTrace()));
      return null;
    }
  }

  public static void tickPhysics(Player bot) {
    if (!initialized || doTickMethod == null || craftPlayerGetHandleMethod == null) return;
    if (!bot.isOnline() || !bot.isValid() || bot.isDead()) return;

    if (dispatchTickPhysicsToRegionThread(bot)) {
      return;
    }

    tickPhysicsInternal(bot);
  }

  private static void tickPhysicsInternal(Player bot) {
    if (!initialized || doTickMethod == null || craftPlayerGetHandleMethod == null) return;
    if (!bot.isOnline() || !bot.isValid() || bot.isDead()) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);

      if (firstTickSet.remove(bot.getUniqueId())) {

        Location loc = bot.getLocation();
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();

        initPreviousPosition(nmsPlayer, x, y, z);
        doTickMethod.invoke(nmsPlayer);

        if (setPosMethod != null) setPosMethod.invoke(nmsPlayer, x, y, z);
        initPreviousPosition(nmsPlayer, x, y, z);

      } else {

        doTickMethod.invoke(nmsPlayer);
      }

    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.tickPhysics failed for " + bot.getName() + ": " + e.getMessage());
    }
  }

  private static boolean dispatchTickPhysicsToRegionThread(Player bot) {
    return false;
  }

  public static void setPosition(Player bot, double x, double y, double z) {
    if (!initialized || setPosMethod == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      setPosMethod.invoke(nmsPlayer, x, y, z);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setPosition failed: " + e.getMessage());
    }
  }

  public static void setJumping(Player bot, boolean jumping) {
    if (!initialized || jumpingField == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      jumpingField.setBoolean(nmsPlayer, jumping);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setJumping failed: " + e.getMessage());
    }
  }

  public static boolean setListed(Player bot, boolean listed) {
    if (!initialized || listedField == null || craftPlayerGetHandleMethod == null) return false;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      listedField.setBoolean(nmsPlayer, listed);
      return true;
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setListed failed: " + e.getMessage());
      return false;
    }
  }

  public static void setHeadYaw(Player bot, float yaw) {
    if (!initialized || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      if (yHeadRotField != null) {
        yHeadRotField.setFloat(nmsPlayer, yaw);
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setHeadYaw failed: " + e.getMessage());
    }
  }

  private static void applyRotation(Player bot, float yaw, float pitch) {
    try {
      bot.setRotation(yaw, pitch);
      setHeadYaw(bot, yaw);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.applyRotation failed: " + e.getMessage());
    }
  }

  public static void performAttack(Player bot, org.bukkit.entity.Entity target, double damage) {
    if (!initialized || craftPlayerGetHandleMethod == null) {

      if (target instanceof Damageable damageable) {
        damageable.damage(damage, bot);
      }
      return;
    }

    try {
      Object nmsBot = craftPlayerGetHandleMethod.invoke(bot);

      Object nmsTarget = target.getClass().getMethod("getHandle").invoke(target);

      if (attackMethod != null && nmsTarget != null) {

        attackMethod.invoke(nmsBot, nmsTarget);
      } else {

        if (target instanceof Damageable damageable) {
          damageable.damage(damage, bot);
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.performAttack failed: " + e.getMessage());

      if (target instanceof Damageable damageable) {
        damageable.damage(damage, bot);
      }
    }
  }

  public static void setMovementForward(Player bot, float forward) {
    if (!initialized || zzaField == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      zzaField.setFloat(nmsPlayer, forward);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setMovementForward failed: " + e.getMessage());
    }
  }

  public static void setMovementStrafe(Player bot, float strafe) {
    if (!initialized || xxaField == null || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      xxaField.setFloat(nmsPlayer, strafe);
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.setMovementStrafe failed: " + e.getMessage());
    }
  }

  public static void applyServerVelocity(Player bot, Vector velocity) {
    if (!initialized || craftPlayerGetHandleMethod == null || bot == null || velocity == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
      Object vec3 =
          vec3Class
              .getConstructor(double.class, double.class, double.class)
              .newInstance(velocity.getX(), velocity.getY(), velocity.getZ());
      Method setDelta = findMethod(nmsPlayer.getClass(), "setDeltaMovement", 1, vec3Class);
      if (setDelta != null) {
        setDelta.invoke(nmsPlayer, vec3);
      } else {
        bot.setVelocity(velocity);
      }

      Field hurtMarked = findFieldInHierarchy(nmsPlayer.getClass(), "hurtMarked");
      if (hurtMarked != null && hurtMarked.getType() == boolean.class) {
        hurtMarked.setBoolean(nmsPlayer, true);
      }
      Field hasImpulse = findFieldInHierarchy(nmsPlayer.getClass(), "hasImpulse");
      if (hasImpulse != null && hasImpulse.getType() == boolean.class) {
        hasImpulse.setBoolean(nmsPlayer, true);
      }
    } catch (Exception e) {
      try {
        bot.setVelocity(velocity);
      } catch (Exception ignored) {
      }
      FppLogger.debug("NmsPlayerSpawner.applyServerVelocity failed: " + e.getMessage());
    }
  }

  public static void removeFakePlayer(Player player) {
    removeFakePlayer(player, true);
  }

  public static void removeFakePlayerFast(Player player) {
    removeFakePlayer(player, false);
  }

  private static void removeFakePlayer(Player player, boolean saveData) {
    if (player == null) return;
    try {
      firstTickSet.remove(player.getUniqueId());
      if (player.isOnline()) {
        final String name = player.getName();
        final UUID uuid = player.getUniqueId();

        FppLogger.debug("NmsPlayerSpawner: removing '" + name + "' uuid=" + uuid);

        if (saveData) {
          try {
            player.saveData();
            FppLogger.debug("NmsPlayerSpawner: saved playerdata for '" + name + "' uuid=" + uuid);
          } catch (Exception e) {
            FppLogger.warn(
                "NmsPlayerSpawner: saveData failed for '"
                    + name
                    + "' uuid="
                    + uuid
                    + ": "
                    + e.getMessage());
          }

        }

        boolean removedViaPlayerList = false;
        if (initialized
            && craftPlayerGetHandleMethod != null
            && craftServerGetServerMethod != null
            && getPlayerListMethod != null
            && playerListRemoveMethod != null) {
          try {
            Object nmsPlayer = craftPlayerGetHandleMethod.invoke(player);
            Object minecraftServer =
                craftServerGetServerMethod.invoke(Bukkit.getServer());
            Object playerList = getPlayerListMethod.invoke(minecraftServer);
            playerListRemoveMethod.invoke(playerList, nmsPlayer);
            removedViaPlayerList = true;
            FppLogger.debug(
                "NmsPlayerSpawner: removed '" + name + "' via PlayerList.remove() uuid=" + uuid);
          } catch (Exception e) {
            FppLogger.debug(
                "NmsPlayerSpawner: PlayerList.remove failed for '"
                    + name
                    + "' uuid="
                    + uuid
                    + ": "
                    + e.getMessage()
                    + " - falling back to kick");
          }
        }

        if (!removedViaPlayerList && player.isOnline()) {
          player.kick(Component.empty());
        }
      }
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.removeFakePlayer failed for "
              + player.getName()
              + ": "
              + e.getMessage());
    }
  }

  private static void ensurePlayerDataExists(
      Object minecraftServer, Object serverPlayer, String name, UUID uuid) {
    if (playerDataStorageField == null) {
      FppLogger.debug(
          "NmsPlayerSpawner: ensurePlayerDataExists skipped"
              + " - WorldNBTStorage field not cached (name="
              + name
              + " uuid="
              + uuid
              + ")");
      return;
    }
    try {
      Object playerList = getPlayerListMethod.invoke(minecraftServer);
      Object playerDataStorage = playerDataStorageField.get(playerList);

      if (getPlayerDirMethod != null) {
        File playerDir = (File) getPlayerDirMethod.invoke(playerDataStorage);
        File playerFile = new File(playerDir, uuid + ".dat");
        if (playerFile.exists()) {
          FppLogger.debug(
              "NmsPlayerSpawner: playerdata found for '"
                  + name
                  + "' uuid="
                  + uuid
                  + " - returning player");
          return;
        }
      }

      if (playerDataSaveMethod != null) {
        playerDataSaveMethod.invoke(playerDataStorage, serverPlayer);
        FppLogger.debug(
            "NmsPlayerSpawner: created initial playerdata for '"
                + name
                + "' uuid="
                + uuid
                + " - will be treated as returning player on next spawn");
      } else {
        FppLogger.debug(
            "NmsPlayerSpawner: playerdata file missing but save method"
                + " not cached - first-join message may appear (name="
                + name
                + ")");
      }
    } catch (Exception e) {

      FppLogger.warn(
          "NmsPlayerSpawner: ensurePlayerDataExists failed for '"
              + name
              + "' uuid="
              + uuid
              + ": "
              + e.getMessage());
    }
  }

  public static void reInjectFakeListener(Player bot) {
    if (!initialized || craftPlayerGetHandleMethod == null || connectionFieldInPlayer == null) {
      FppLogger.debug("NmsPlayerSpawner.reInjectFakeListener: not available");
      return;
    }
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      if (nmsPlayer == null) return;

      Object currentConn = connectionFieldInPlayer.get(nmsPlayer);
      if (currentConn instanceof FakeServerGamePacketListenerImpl) {

        FppLogger.debug(
            "NmsPlayerSpawner.reInjectFakeListener: "
                + bot.getName()
                + " already has FakeServerGamePacketListenerImpl");
        return;
      }

      Object networkConn = null;
      if (currentConn != null && connectionClass != null) {
        for (Field f : getAllDeclaredFields(currentConn.getClass())) {
          if (Modifier.isStatic(f.getModifiers())) continue;
          if (connectionClass.isAssignableFrom(f.getType())) {
            f.setAccessible(true);
            networkConn = f.get(currentConn);
            break;
          }
        }
      }
      if (networkConn == null) {

        networkConn = createFakeConnection();
      }
      if (networkConn == null) {
        FppLogger.warn(
            "NmsPlayerSpawner.reInjectFakeListener: cannot get connection for " + bot.getName());
        return;
      }

      Object gameProfile = null;
      try {
        Method gpMethod = findMethodByName(nmsPlayer.getClass(), "getGameProfile", 0);
        if (gpMethod != null) {
          gpMethod.setAccessible(true);
          gameProfile = gpMethod.invoke(nmsPlayer);
        }
      } catch (Exception ignored) {
      }
      if (gameProfile == null) {

        for (Field f : getAllDeclaredFields(nmsPlayer.getClass())) {
          if (f.getType().getSimpleName().equals("GameProfile")) {
            f.setAccessible(true);
            gameProfile = f.get(nmsPlayer);
            break;
          }
        }
      }

      Object clientInfo = getClientInformation();
      injectFakeListener(
          craftServerGetServerMethod.invoke(Bukkit.getServer()),
          networkConn,
          nmsPlayer,
          gameProfile,
          clientInfo);

      clearAwaitingPosition(nmsPlayer);

      firstTickSet.add(bot.getUniqueId());

      FppLogger.debug("NmsPlayerSpawner.reInjectFakeListener: success for " + bot.getName());
    } catch (Exception e) {
      FppLogger.warn(
          "NmsPlayerSpawner.reInjectFakeListener failed for "
              + bot.getName()
              + ": "
              + e.getMessage());
    }
  }

  private static void clearAwaitingPosition(Object nmsPlayer) {
    try {

      Object sgpl = connectionFieldInPlayer.get(nmsPlayer);
      if (sgpl == null) return;
      for (Field f : getAllDeclaredFields(sgpl.getClass())) {
        if (Modifier.isStatic(f.getModifiers())) continue;

        if (f.getName().equals("awaitingPositionFromClient") || f.getName().contains("awaiting")) {
          f.setAccessible(true);
          if (!f.getType().isPrimitive()) {
            f.set(sgpl, null);
            FppLogger.debug("NmsPlayerSpawner: cleared " + f.getName() + " on SGPL");
            return;
          }
        }
      }

      for (Field f : getAllDeclaredFields(sgpl.getClass())) {
        if (Modifier.isStatic(f.getModifiers())) continue;
        if (f.getType().getSimpleName().equals("Vec3")) {
          f.setAccessible(true);
          Object val = f.get(sgpl);
          if (val != null) {
            f.set(sgpl, null);
            FppLogger.debug(
                "NmsPlayerSpawner: cleared Vec3 field '"
                    + f.getName()
                    + "' on SGPL (likely awaitingPositionFromClient)");
            return;
          }
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.clearAwaitingPosition failed: " + e.getMessage());
    }
  }

  private static Method findMethodByName(Class<?> clazz, String name, int paramCount) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  public static void setPing(Player bot, int pingMs) {
    if (bot == null || !initialized) return;
    try {
      Object nmsPlayer = craftBukkitGetHandle(bot);
      if (nmsPlayer == null) return;
      setPingNms(nmsPlayer, pingMs);
    } catch (Exception e) {
      Config.debugNms("setPing failed: " + e.getMessage());
    }
  }

  private static void setPingNms(Object nmsPlayer, int pingMs) {
    int safePing = Math.max(0, pingMs);
    try {
      setLatencyField(nmsPlayer, safePing);
      if (connectionFieldInPlayer != null) {
        connectionFieldInPlayer.setAccessible(true);
        Object listener = connectionFieldInPlayer.get(nmsPlayer);
        setLatencyField(listener, safePing);
      }
    } catch (Exception e) {
      Config.debugNms("setPing failed: " + e.getMessage());
    }
  }

  private static void setLatencyField(Object target, int pingMs) {
    if (target == null) return;
    Field latencyField = findLatencyField(target.getClass());
    if (latencyField == null) return;
    try {
      latencyField.setAccessible(true);
      latencyField.setInt(target, Math.max(0, pingMs));
    } catch (Exception e) {
      Config.debugNms(
          "setLatencyField failed on " + target.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static Object craftBukkitGetHandle(Player player) {
    try {
      return player.getClass().getMethod("getHandle").invoke(player);
    } catch (Exception e) {
      return null;
    }
  }

  private static Field findFieldByType(Class<?> clazz, Class<?> type, String preferredName) {
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (f.getType() == type && f.getName().equals(preferredName)) {
          return f;
        }
      }
    }
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (f.getType() == type
            && (f.getName().contains("latency")
            || f.getName().contains("ping")
            || f.getName().contains("ping")
            || f.getName().contains("Latency")
            || f.getName().contains("Ping"))) {
          return f;
        }
      }
    }
    return null;
  }

  private static Field findLatencyField(Class<?> clazz) {
    Field direct = findFieldByType(clazz, int.class, "latency");
    if (direct != null) return direct;
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) continue;
        String name = f.getName().toLowerCase(Locale.ROOT);
        if (name.equals("ping") || name.equals("latency")) return f;
      }
    }
    return null;
  }

  public static void startUsingMainHandItem(Player bot) {
    if (!initialized || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      ClassLoader cl = nmsPlayer.getClass().getClassLoader();

      Class<?> interactionHandClass = cl.loadClass("net.minecraft.world.InteractionHand");
      Object[] hands = interactionHandClass.getEnumConstants();
      if (hands == null || hands.length == 0) return;
      Object mainHand = hands[0];

      for (Method m : nmsPlayer.getClass().getMethods()) {
        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == interactionHandClass) {
          String name = m.getName();
          if (name.equals("startUsingItem") || name.equals("c")) {
            m.setAccessible(true);
            m.invoke(nmsPlayer, mainHand);
            return;
          }
        }
      }

      for (Method m : nmsPlayer.getClass().getMethods()) {
        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == interactionHandClass) {
          m.setAccessible(true);
          m.invoke(nmsPlayer, mainHand);
          return;
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.startUsingMainHandItem failed: " + e.getMessage());
    }
  }

  public static void interactBlock(Player bot, Block block) {
    if (!initialized || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      ClassLoader cl = nmsPlayer.getClass().getClassLoader();

      Class<?> interactionHandClass = cl.loadClass("net.minecraft.world.InteractionHand");
      Object[] hands = interactionHandClass.getEnumConstants();
      if (hands == null || hands.length == 0) return;
      Object mainHand = hands[0];

      Class<?> blockPosClass = cl.loadClass("net.minecraft.core.BlockPos");
      Class<?> directionClass = cl.loadClass("net.minecraft.core.Direction");
      Class<?> blockHitResultClass = cl.loadClass("net.minecraft.world.phys.BlockHitResult");

      Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
          .newInstance(block.getX(), block.getY(), block.getZ());

      Object direction = directionClass.getMethod("getNearest", float.class, float.class, float.class)
          .invoke(null, 0f, -1f, 0f);

      Object blockHit = blockHitResultClass.getConstructor(
              Vector.class, directionClass, blockPosClass, boolean.class)
          .newInstance(new Vector(0.5, 0.5, 0.5), direction, blockPos, false);

      Object gameMode = nmsPlayer.getClass().getMethod("gameMode").invoke(nmsPlayer);
      Object level = nmsPlayer.getClass().getMethod("level").invoke(nmsPlayer);
      Object itemStack = nmsPlayer.getClass().getMethod("getItemInHand", interactionHandClass)
          .invoke(nmsPlayer, mainHand);

      Object result = gameMode.getClass().getMethod("useItemOn",
              nmsPlayer.getClass(), level.getClass(),
              cl.loadClass("net.minecraft.world.item.ItemStack"), interactionHandClass, blockHitResultClass)
          .invoke(gameMode, nmsPlayer, level, itemStack, mainHand, blockHit);

      if (result != null) {
        Method consumesAction = result.getClass().getMethod("consumesAction");
        if ((boolean) consumesAction.invoke(result)) {
          Method swing = nmsPlayer.getClass().getMethod("swing", interactionHandClass);
          swing.invoke(nmsPlayer, mainHand);
        }
      }
    } catch (Exception e) {
      FppLogger.debug("NmsPlayerSpawner.interactBlock failed: " + e.getMessage());
    }
  }

  public static void forceAllSkinParts(Player bot) {
    if (!initialized
        || skinPartsDataAccessor == null
        || entityDataFieldForSkinParts == null
        || synchedEntityDataSetMethod == null
        || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(bot);
      Object entityData = entityDataFieldForSkinParts.get(nmsPlayer);

      synchedEntityDataSetMethod.invoke(entityData, skinPartsDataAccessor, (byte) 0x7F);
      FppLogger.debug("NmsPlayerSpawner: skin-parts forced to 0x7F for " + bot.getName());
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.forceAllSkinParts failed for " + bot.getName() + ": " + e.getMessage());
    }
  }

  public static void applySkinToGameProfile(Player player, SkinProfile skin) {
    if (player == null || !isAvailable() || craftPlayerGetHandleMethod == null) return;
    try {
      Object nmsPlayer = craftPlayerGetHandleMethod.invoke(player);
      Object gameProfile = resolveGameProfile(nmsPlayer);
      if (gameProfile == null) return;
      if (skin != null && skin.isValid()) SkinProfileInjector.apply(gameProfile, skin);
      else SkinProfileInjector.clear(gameProfile);
      forceAllSkinParts(player);
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.applySkinToGameProfile failed for "
              + player.getName()
              + ": "
              + e.getMessage());
    }
  }

  public static boolean refreshPaperPlayer(Player player) {
    if (player == null || craftPlayerRefreshPlayerMethod == null) return false;
    try {
      craftPlayerRefreshPlayerMethod.invoke(player);
      try {
        Object nmsPlayer = craftPlayerGetHandleMethod.invoke(player);
        Method triggerHealthUpdate = findMethodByName(nmsPlayer.getClass(), "triggerHealthUpdate", 0);
        if (triggerHealthUpdate != null) {
          triggerHealthUpdate.setAccessible(true);
          triggerHealthUpdate.invoke(nmsPlayer);
        }
      } catch (Exception ignored) {
      }
      return true;
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner.refreshPaperPlayer failed for "
              + player.getName()
              + ": "
              + e.getMessage());
      return false;
    }
  }

  private static Object resolveGameProfile(Object nmsPlayer) {
    if (nmsPlayer == null) return null;
    for (Method method : nmsPlayer.getClass().getMethods()) {
      if (method.getParameterCount() != 0) continue;
      String name = method.getName();
      if (!name.equals("getGameProfile") && !name.equals("getProfile") && !name.equals("gameProfile")) {
        continue;
      }
      try {
        method.setAccessible(true);
        Object profile = method.invoke(nmsPlayer);
        if (profile != null && profile.getClass().getName().endsWith("GameProfile")) return profile;
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  private static void injectFakeListener(
      Object minecraftServer,
      Object conn,
      Object serverPlayer,
      Object gameProfile,
      Object clientInfo) {
    if (connectionFieldInPlayer == null) {
      FppLogger.warn("NmsPlayerSpawner: cannot inject fake listener - connection field not found");
      return;
    }
    try {
      Object cookie = createCookieDynamic(gameProfile, clientInfo);
      if (cookie == null) {
        FppLogger.warn("NmsPlayerSpawner: cannot inject fake listener - cookie creation failed");
        return;
      }

      FakeServerGamePacketListenerImpl fakeListener =
          FakeServerGamePacketListenerImpl.create(minecraftServer, conn, serverPlayer, cookie);

      connectionFieldInPlayer.set(serverPlayer, fakeListener);
      FppLogger.debug(
          "NmsPlayerSpawner: FakeServerGamePacketListenerImpl injected into"
              + " serverPlayer.connection");

      injectPacketListenerIntoConnection(conn, fakeListener);

    } catch (Exception e) {
      FppLogger.warn("NmsPlayerSpawner: fake listener injection failed: " + e.getMessage());
      FppLogger.debug(Arrays.toString(e.getStackTrace()));
    }
  }

  private static void injectPacketListenerIntoConnection(
      Object conn, FakeServerGamePacketListenerImpl fakeListener) {
    if (conn == null || serverGamePacketListenerClass == null) return;
    try {
      for (Field f : getAllDeclaredFields(conn.getClass())) {
        if (Modifier.isStatic(f.getModifiers())) continue;
        try {
          f.setAccessible(true);
          Object val = f.get(conn);
          if (val != null && serverGamePacketListenerClass.isInstance(val)) {
            f.set(conn, fakeListener);
            FppLogger.debug(
                "NmsPlayerSpawner: Connection."
                    + f.getName()
                    + " updated to FakeServerGamePacketListenerImpl"
                    + " (was "
                    + val.getClass().getSimpleName()
                    + ")");
            return;
          }
        } catch (Exception ignored) {
        }
      }
      FppLogger.debug(
          "NmsPlayerSpawner: Connection packetListener field not found"
              + " - onDisconnect override may not fire on double-disconnect");
    } catch (Exception e) {
      FppLogger.debug(
          "NmsPlayerSpawner: injectPacketListenerIntoConnection failed: " + e.getMessage());
    }
  }

  private static Object createFakeConnection() {
    try {
      FakeConnection conn = new FakeConnection(InetAddress.getLoopbackAddress());
      FppLogger.debug("NmsPlayerSpawner: FakeConnection created (direct Connection subclass)");
      return conn;

    } catch (Exception e) {
      FppLogger.warn("NmsPlayerSpawner.createFakeConnection failed: " + e.getMessage());
      return null;
    }
  }

  private static Object getClientInformation() {
    if (clientInfoDefault != null) return clientInfoDefault;
    if (clientInformationClass == null) return null;
    try {
      return clientInformationClass.getMethod("createDefault").invoke(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static Object createServerPlayer(
      Object minecraftServer, Object serverLevel, Object gameProfile, Object clientInfo) {

    if (clientInfo != null && clientInformationClass != null) {
      try {
        Constructor<?> ctor =
            serverPlayerClass.getConstructor(
                minecraftServerClass,
                serverLevelClass,
                gameProfile.getClass(),
                clientInformationClass);
        return ctor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);
      } catch (NoSuchMethodException ignored) {
      } catch (Exception e) {
        FppLogger.debug("4-arg ServerPlayer ctor failed: " + e.getMessage());
      }
    }

    try {
      Constructor<?> ctor =
          serverPlayerClass.getConstructor(
              minecraftServerClass, serverLevelClass, gameProfile.getClass());
      return ctor.newInstance(minecraftServer, serverLevel, gameProfile);
    } catch (Exception e) {
      FppLogger.error("NmsPlayerSpawner: no ServerPlayer constructor matched: " + e.getMessage());
      return null;
    }
  }

  private static boolean placePlayer(
      Object minecraftServer,
      Object conn,
      Object serverPlayer,
      Object gameProfile,
      Object clientInfo) {
    try {
      Object playerList = getPlayerListMethod.invoke(minecraftServer);
      if (conn == null || commonListenerCookieClass == null) {
        FppLogger.debug("placeNewPlayer skipped (conn=" + conn + ")");
        return false;
      }
      Object cookie = createCookieDynamic(gameProfile, clientInfo);
      if (cookie == null) return false;

      Method placeMethod = findMethod(playerList.getClass(), "placeNewPlayer", 3);
      if (placeMethod != null) {
        placeMethod.setAccessible(true);
        placeMethod.invoke(playerList, conn, serverPlayer, cookie);
        return true;
      }
      FppLogger.warn("NmsPlayerSpawner: placeNewPlayer(3-arg) not found on PlayerList");
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (isWorldDataNotReadyFailure(cause)) {
        FppLogger.warn(
            "NmsPlayerSpawner.placePlayer deferred: world data not ready on this thread yet");
      } else {
        FppLogger.warn(
            "NmsPlayerSpawner.placePlayer failed: "
                + cause.getClass().getSimpleName()
                + ": "
                + cause.getMessage());
      }
    }
    return false;
  }

  private static boolean isWorldDataNotReadyFailure(Throwable cause) {
    if (cause == null) return false;
    String msg = cause.getMessage();
    return cause instanceof NullPointerException
        && msg != null
        && msg.contains("getCurrentWorldData()")
        && msg.contains("connections");
  }

  private static void cleanupFailedSpawn(Object minecraftServer, Object serverPlayer, String name) {
    try {
      Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
      Object entity = getBukkitEntity.invoke(serverPlayer);
      if (entity instanceof Player player) {
        FppLogger.warn("NmsPlayerSpawner: cleaning up partial failed spawn for " + name);
        removeFakePlayer(player);
        return;
      }
    } catch (Exception ignored) {
    }

    try {
      if (minecraftServer != null && getPlayerListMethod != null && playerListRemoveMethod != null) {
        Object playerList = getPlayerListMethod.invoke(minecraftServer);
        playerListRemoveMethod.invoke(playerList, serverPlayer);
      }
    } catch (Exception ignored) {
    }
  }

  /**
   * Compatibility entry point for callers that expect a callback-based spawn API.
   */
  public static void spawnFakePlayerAsync(
      UUID uuid,
      String name,
      SkinProfile skin,
      World world,
      double x,
      double y,
      double z,
      Consumer<Player> callback) {
    spawnFakePlayerAsync(uuid, name, skin, world, x, y, z, -1, callback);
  }

  public static void spawnFakePlayerAsync(
      UUID uuid,
      String name,
      SkinProfile skin,
      World world,
      double x,
      double y,
      double z,
      int initialPing,
      Consumer<Player> callback) {
    spawnFakePlayerAsync(uuid, name, skin, world, x, y, z, 0.0f, 0.0f, initialPing, callback);
  }

  public static void spawnFakePlayerAsync(
      UUID uuid,
      String name,
      SkinProfile skin,
      World world,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      int initialPing,
      Consumer<Player> callback) {
    if (!isAvailable()) {
      FppLogger.warn("NmsPlayerSpawner not available - cannot spawn " + name);
      callback.accept(null);
      return;
    }
    callback.accept(spawnFakePlayer(uuid, name, skin, world, x, y, z, yaw, pitch, initialPing));
  }

  private static Object createCookieDynamic(Object gameProfile, Object clientInfo) {
    if (commonListenerCookieClass == null) return null;

    try {
      Method factory =
          commonListenerCookieClass.getMethod(
              "createInitial", gameProfile.getClass(), boolean.class);
      return factory.invoke(null, gameProfile, false);
    } catch (Exception ignored) {
    }

    for (Constructor<?> c : commonListenerCookieClass.getDeclaredConstructors()) {
      c.setAccessible(true);
      Class<?>[] p = c.getParameterTypes();
      if (p.length > 0 && p[p.length - 1].getSimpleName().contains("DefaultConstructorMarker")) {
        continue;
      }
      try {
        Object result =
            switch (p.length) {
              case 1 -> c.newInstance(gameProfile);
              case 2 -> c.newInstance(gameProfile, 0);
              case 3 -> c.newInstance(gameProfile, 0, clientInfo);
              case 4 -> c.newInstance(gameProfile, 0, clientInfo, false);
              case 5 -> c.newInstance(gameProfile, 0, clientInfo, false, false);
              case 7 -> c.newInstance(
                  gameProfile, 0, clientInfo, false, null, Collections.emptySet(), null);
              default -> null;
            };
        if (result != null) return result;
      } catch (Exception ignored) {
      }
    }
    FppLogger.debug("NmsPlayerSpawner: no CommonListenerCookie constructor succeeded");
    return null;
  }

  private static void prepareJoinCompatibility(Object conn, Object serverPlayer, UUID uuid, String name) {
    Player bukkitPlayer = resolveBukkitPlayer(serverPlayer);
    markFakePlayerMetadata(bukkitPlayer);
    initialiseCmiUser(bukkitPlayer, uuid, name);
    registerPacketEventsUser(conn, bukkitPlayer, uuid, name);
  }

  private static Player resolveBukkitPlayer(Object serverPlayer) {
    if (serverPlayer == null || serverPlayerClass == null) return null;
    try {
      Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
      Object entity = getBukkitEntity.invoke(serverPlayer);
      return entity instanceof Player player ? player : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void markFakePlayerMetadata(Player player) {
    Plugin plugin = FakePlayerPlugin.getInstance();
    if (plugin == null || player == null) return;
    try {
      player.getPersistentDataContainer().set(new NamespacedKey(plugin, "npc"), PersistentDataType.BYTE, (byte) 1);
      player.getPersistentDataContainer().set(new NamespacedKey(plugin, "fpp"), PersistentDataType.BYTE, (byte) 1);
      player.getPersistentDataContainer().set(new NamespacedKey(plugin, "fakeplayerplugin"), PersistentDataType.BYTE, (byte) 1);
    } catch (Throwable t) {
      FppLogger.debug("NmsPlayerSpawner: fake-player metadata failed: " + t.getMessage());
    }
  }

  private static void initialiseCmiUser(Player player, UUID uuid, String name) {
    Plugin cmiPlugin = Bukkit.getPluginManager().getPlugin("CMI");
    if (cmiPlugin == null || player == null || uuid == null || name == null) return;
    try {
      ClassLoader loader = cmiPlugin.getClass().getClassLoader();
      Class<?> cmiUserClass = Class.forName("com.Zrips.CMI.Containers.CMIUser", false, loader);
      Object existing = tryStaticCmiGetUser(cmiUserClass, player);
      if (existing != null) return;

      Object cmiInstance = resolveCmiInstance(cmiPlugin, loader);
      if (cmiInstance == null) return;

      List<Object> managers = new ArrayList<>();
      managers.add(cmiInstance);
      for (String methodName :
          new String[]{"getPlayerManager", "getUserManager", "getCmiUserManager", "getPlayerDataManager"}) {
        Object manager = tryInvokeNoArg(cmiInstance, methodName);
        if (manager != null && !managers.contains(manager)) managers.add(manager);
      }

      Object user = null;
      for (Object manager : managers) {
        user = tryCreateOrGetCmiUser(manager, player, uuid, name);
        if (user != null) {
          addUserToCmiMaps(manager, user, player, uuid, name);
          break;
        }
      }

      if (user != null) {
        addUserToCmiMaps(cmiUserClass, user, player, uuid, name);
        FppLogger.debug("NmsPlayerSpawner: CMI fake user initialised for " + name);
      }
    } catch (Throwable t) {
      FppLogger.debug("NmsPlayerSpawner: CMI user init skipped: " + t.getMessage());
    }
  }

  private static Object resolveCmiInstance(Plugin cmiPlugin, ClassLoader loader) {
    try {
      Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI", false, loader);
      Object instance = tryInvokeStaticNoArg(cmiClass, "getInstance");
      return instance != null ? instance : cmiPlugin;
    } catch (Throwable ignored) {
      return cmiPlugin;
    }
  }

  private static Object tryStaticCmiGetUser(Class<?> cmiUserClass, Player player) {
    try {
      Method method = cmiUserClass.getMethod("getUser", Player.class);
      method.setAccessible(true);
      return method.invoke(null, player);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Object tryCreateOrGetCmiUser(Object target, Player player, UUID uuid, String name) {
    for (String methodName :
        new String[]{
            "getUser", "getCMIUser", "getByName", "getByUUID", "loadUser", "addUser", "createUser"
        }) {
      Object user = tryInvokeUserMethod(target, methodName, player, uuid, name);
      if (user != null) return user;
    }
    return null;
  }

  private static Object tryInvokeUserMethod(Object target, String methodName, Player player, UUID uuid, String name) {
    for (Method method : getAllDeclaredMethods(target.getClass())) {
      if (!method.getName().equals(methodName)) continue;
      Object[] args = buildCmiUserArgs(method.getParameterTypes(), player, uuid, name);
      if (args == null) continue;
      try {
        method.setAccessible(true);
        return method.invoke(target, args);
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  private static Object[] buildCmiUserArgs(Class<?>[] types, Player player, UUID uuid, String name) {
    Object[] args = new Object[types.length];
    for (int i = 0; i < types.length; i++) {
      Class<?> type = types[i];
      if (Player.class.isAssignableFrom(type)) args[i] = player;
      else if (OfflinePlayer.class.isAssignableFrom(type)) args[i] = player;
      else if (UUID.class.isAssignableFrom(type)) args[i] = uuid;
      else if (String.class.isAssignableFrom(type)) args[i] = name;
      else if (type == boolean.class || type == Boolean.class) args[i] = Boolean.TRUE;
      else return null;
    }
    return args;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void addUserToCmiMaps(Object owner, Object user, Player player, UUID uuid, String name) {
    Class<?> type = owner instanceof Class<?> clazz ? clazz : owner.getClass();
    Object target = owner instanceof Class<?> ? null : owner;
    for (Field field : getAllDeclaredFields(type)) {
      if (!Map.class.isAssignableFrom(field.getType())) continue;
      try {
        field.setAccessible(true);
        Object value = field.get(target);
        if (!(value instanceof Map map)) continue;
        putIfKeyWorks(map, uuid, user);
        putIfKeyWorks(map, player.getUniqueId(), user);
        putIfKeyWorks(map, name, user);
        putIfKeyWorks(map, name.toLowerCase(Locale.ROOT), user);
        putIfKeyWorks(map, player, user);
      } catch (Throwable ignored) {
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void putIfKeyWorks(Map map, Object key, Object user) {
    try {
      map.putIfAbsent(key, user);
    } catch (Throwable ignored) {
    }
  }

  private static Object tryInvokeNoArg(Object target, String methodName) {
    try {
      return invokeNoArg(target.getClass(), target, methodName);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Object tryInvokeStaticNoArg(Class<?> owner, String methodName) {
    try {
      return invokeNoArg(owner, null, methodName);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static void registerPacketEventsUser(
      Object conn, Player bukkitPlayer, UUID uuid, String name) {
    Object channel = resolveConnectionChannel(conn);
    if (channel == null || bukkitPlayer == null || uuid == null || name == null) return;

    Set<ClassLoader> tried = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
      String pluginName = plugin.getName().toLowerCase(Locale.ROOT);
      if (!pluginName.contains("grim") && !pluginName.contains("packetevents")) continue;
      ClassLoader loader = plugin.getClass().getClassLoader();
      if (!tried.add(loader)) continue;
      try {
        if (tryRegisterPacketEventsUser(loader, channel, bukkitPlayer, uuid, name)) return;
      } catch (Throwable t) {
        FppLogger.debug(
            "NmsPlayerSpawner: PacketEvents registration failed for "
                + name
                + ": "
                + t.getClass().getSimpleName()
                + " - "
                + t.getMessage());
      }
    }
  }

  private static Object resolveConnectionChannel(Object conn) {
    if (conn == null) return null;
    try {
      Field channelField = findFieldByName(conn.getClass(), "channel");
      return channelField != null ? channelField.get(conn) : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static boolean tryRegisterPacketEventsUser(
      ClassLoader loader, Object channel, Player bukkitPlayer, UUID uuid, String name) {
    for (String prefix :
        new String[]{
            "com.github.retrooper.packetevents",
            "ac.grim.grimac.shaded.com.github.retrooper.packetevents"
        }) {
      try {
        Class<?> packetEventsClass = Class.forName(prefix + ".PacketEvents", false, loader);
        Object api = invokeNoArg(packetEventsClass, null, "getAPI");
        if (api == null) continue;

        try {
          if (!Boolean.TRUE.equals(invokeNoArg(api.getClass(), api, "isInitialized"))) continue;
        } catch (NoSuchMethodException ignored) {
        }

        Object injector = invokeNoArg(api.getClass(), api, "getInjector");
        if (injector == null) continue;

        Class<?> connectionStateClass = Class.forName(prefix + ".protocol.ConnectionState", false, loader);
        Class<?> clientVersionClass = Class.forName(prefix + ".protocol.player.ClientVersion", false, loader);
        Class<?> userProfileClass = Class.forName(prefix + ".protocol.player.UserProfile", false, loader);
        Class<?> userClass = Class.forName(prefix + ".protocol.player.User", false, loader);

        Object playState = Enum.valueOf(connectionStateClass.asSubclass(Enum.class), "PLAY");
        Object clientVersion = resolvePacketEventsClientVersion(api, clientVersionClass);
        Object profile = userProfileClass.getConstructor(UUID.class, String.class).newInstance(uuid, name);

        tryInitialisePacketEventsChannel(loader, prefix, channel, playState, connectionStateClass);

        Object user =
            userClass
                .getConstructor(Object.class, connectionStateClass, clientVersionClass, userProfileClass)
                .newInstance(channel, playState, clientVersion, profile);

        try {
          userClass.getMethod("setEntityId", int.class).invoke(user, bukkitPlayer.getEntityId());
        } catch (NoSuchMethodException ignored) {
        }

        Object protocolManager = invokeNoArg(api.getClass(), api, "getProtocolManager");
        invokeMethod(protocolManager, "setChannel", new Class<?>[]{UUID.class, Object.class}, uuid, channel);
        invokeMethod(protocolManager, "setUser", new Class<?>[]{Object.class, userClass}, channel, user);
        putPacketEventsProtocolMaps(loader, prefix, channel, uuid, user);
        patchPacketEventsHandlerUser(loader, prefix, channel, user, bukkitPlayer);

        try {
          invokeMethod(injector, "updateUser", new Class<?>[]{Object.class, userClass}, channel, user);
        } catch (NoSuchMethodException ignored) {
        }

        try {
          invokeMethod(injector, "setPlayer", new Class<?>[]{Object.class, Object.class}, channel, bukkitPlayer);
        } catch (NoSuchMethodException ignored) {
        }

        FppLogger.debug("NmsPlayerSpawner: PacketEvents fake user registered for " + name);
        return true;
      } catch (ClassNotFoundException ignored) {
      } catch (Throwable t) {
        FppLogger.warn("NmsPlayerSpawner: PacketEvents fake user registration skipped: " + t.getMessage());
      }
    }
    return false;
  }

  private static Object invokeNoArg(Class<?> owner, Object target, String name) throws Exception {
    Method method = owner.getMethod(name);
    method.setAccessible(true);
    return method.invoke(target);
  }

  private static Object invokeMethod(Object target, String name, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = target.getClass().getMethod(name, parameterTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  @SuppressWarnings("unchecked")
  private static void putPacketEventsProtocolMaps(
      ClassLoader loader, String prefix, Object channel, UUID uuid, Object user) throws Exception {
    Class<?> protocolManagerClass = Class.forName(prefix + ".manager.protocol.ProtocolManager", false, loader);
    Field channelsField = protocolManagerClass.getField("CHANNELS");
    Field usersField = protocolManagerClass.getField("USERS");
    Object channels = channelsField.get(null);
    Object users = usersField.get(null);
    if (channels instanceof Map channelsMap) channelsMap.put(uuid, channel);
    if (users instanceof Map usersMap) usersMap.put(channel, user);
  }

  private static void patchPacketEventsHandlerUser(
      ClassLoader loader, String prefix, Object channel, Object user, Player bukkitPlayer) {
    String injectorPrefix =
        prefix.startsWith("ac.grim.grimac.shaded")
            ? "ac.grim.grimac.shaded.io.github.retrooper.packetevents"
            : "io.github.retrooper.packetevents";
    try {
      Class<?> encoderClass =
          Class.forName(injectorPrefix + ".injector.handlers.PacketEventsEncoder", false, loader);
      Class<?> decoderClass =
          Class.forName(injectorPrefix + ".injector.handlers.PacketEventsDecoder", false, loader);
      Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
      patchPacketEventsHandler(pipeline, encoderClass, user, bukkitPlayer);
      patchPacketEventsHandler(pipeline, decoderClass, user, bukkitPlayer);
    } catch (Throwable ignored) {
    }
  }

  private static void patchPacketEventsHandler(
      Object pipeline, Class<?> handlerClass, Object user, Player bukkitPlayer) {
    try {
      Object handler = pipeline.getClass().getMethod("get", Class.class).invoke(pipeline, handlerClass);
      if (handler == null) return;
      Field userField = findFieldByName(handler.getClass(), "user");
      if (userField != null) userField.set(handler, user);
      Field playerField = findFieldByName(handler.getClass(), "player");
      if (playerField != null) playerField.set(handler, bukkitPlayer);
    } catch (Throwable ignored) {
    }
  }

  private static void tryInitialisePacketEventsChannel(
      ClassLoader loader,
      String packetEventsPrefix,
      Object channel,
      Object playState,
      Class<?> connectionStateClass) {
    String injectorPrefix =
        packetEventsPrefix.startsWith("ac.grim.grimac.shaded")
            ? "ac.grim.grimac.shaded.io.github.retrooper.packetevents"
            : "io.github.retrooper.packetevents";
    try {
      Class<?> initializerClass =
          Class.forName(injectorPrefix + ".injector.connection.ServerConnectionInitializer", false, loader);
      initializerClass
          .getMethod("initChannel", Object.class, connectionStateClass)
          .invoke(null, channel, playState);
    } catch (Throwable t) {
      FppLogger.debug("NmsPlayerSpawner: PacketEvents channel init skipped: " + t.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static Object resolvePacketEventsClientVersion(Object api, Class<?> clientVersionClass) {
    try {
      Object serverManager = invokeNoArg(api.getClass(), api, "getServerManager");
      Object serverVersion = invokeNoArg(serverManager.getClass(), serverManager, "getVersion");
      Object clientVersion = invokeNoArg(serverVersion.getClass(), serverVersion, "toClientVersion");
      if (clientVersionClass.isInstance(clientVersion)) return clientVersion;
    } catch (Throwable ignored) {
    }

    try {
      return clientVersionClass.getMethod("getLatest").invoke(null);
    } catch (Throwable ignored) {
    }
    return Enum.valueOf(clientVersionClass.asSubclass(Enum.class), "UNKNOWN");
  }

  private static void initPreviousPosition(Object nmsPlayer, double x, double y, double z) {
    try {
      if (xoField != null) xoField.setDouble(nmsPlayer, x);
      if (yoField != null) yoField.setDouble(nmsPlayer, y);
      if (zoField != null) zoField.setDouble(nmsPlayer, z);
    } catch (Exception ignored) {
    }
  }

  private static Method findMethod(Class<?> clazz, String name, int paramCount) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
          m.setAccessible(true);
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static Method findMethod(
      Class<?> clazz, String name, int paramCount, Class<?>... paramTypes) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (!m.getName().equals(name) || m.getParameterCount() != paramCount) continue;
        if (paramTypes.length == 0) {
          m.setAccessible(true);
          return m;
        }

        Class<?>[] mParams = m.getParameterTypes();
        boolean match = true;
        for (int i = 0; i < paramTypes.length && i < mParams.length; i++) {
          if (!mParams[i].isAssignableFrom(paramTypes[i])) {
            match = false;
            break;
          }
        }
        if (match) {
          m.setAccessible(true);
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static Field findFieldInHierarchy(Class<?> clazz, String name) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      try {
        Field f = cur.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) {
        cur = cur.getSuperclass();
      }
    }
    return null;
  }

  private static Method findMethodBySignature(
      Class<?> clazz, int paramCount, Class<?>... paramTypes) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Method m : cur.getDeclaredMethods()) {
        if (m.getParameterCount() == paramCount
            && Arrays.equals(m.getParameterTypes(), paramTypes)) {
          m.setAccessible(true);
          return m;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static Field findFieldByName(Class<?> clazz, String name) {
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      for (Field f : cur.getDeclaredFields()) {
        if (f.getName().equals(name)) {
          f.setAccessible(true);
          return f;
        }
      }
      cur = cur.getSuperclass();
    }
    return null;
  }

  private static List<Field> getAllDeclaredFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      Collections.addAll(fields, cur.getDeclaredFields());
      cur = cur.getSuperclass();
    }
    return fields;
  }

  private static List<Method> getAllDeclaredMethods(Class<?> clazz) {
    List<Method> methods = new ArrayList<>();
    Class<?> cur = clazz;
    while (cur != null && cur != Object.class) {
      Collections.addAll(methods, cur.getDeclaredMethods());
      cur = cur.getSuperclass();
    }
    return methods;
  }

  // ── Version-safe NMS helpers (Paper 26.1.x / MC 1.21.2+ compatibility) ──
  // Each helper tries the direct NMS call first (fast path, zero overhead after
  // first success). On NoSuchMethodError it falls back to ClassLoader-based
  // reflection that works regardless of method renames or signature changes.

  // ── useItemOn ──

  private static volatile int useItemOnProbeState; // 0=untried, 1=direct-works, 2=need-reflection
  private static volatile Method useItemOnDirect5;
  private static volatile Method useItemOnDirect3;
  private static volatile Method useItemOnReflect;

  public static Object useItemOn(ServerPlayer nms, InteractionHand hand, BlockHitResult blockHit) {
    if (useItemOnProbeState == 0) {
      try {
        var r = nms.gameMode.useItemOn(nms, nms.level(), nms.getItemInHand(hand), hand, blockHit);
        useItemOnProbeState = 1;
        return r;
      } catch (NoSuchMethodError e) {
        useItemOnProbeState = 2;
      }
    } else if (useItemOnProbeState == 1) {
      return nms.gameMode.useItemOn(nms, nms.level(), nms.getItemInHand(hand), hand, blockHit);
    }
    // reflection path
    if (useItemOnReflect == null) {
      synchronized (NmsPlayerSpawner.class) {
        if (useItemOnReflect == null) {
          Class<?> gmClass = nms.gameMode.getClass();
          ClassLoader cl = gmClass.getClassLoader();
          try {
            Class<?> itemStackClass = cl.loadClass("net.minecraft.world.item.ItemStack");
            Class<?> handClass = cl.loadClass("net.minecraft.world.InteractionHand");
            Class<?> hitClass = cl.loadClass("net.minecraft.world.phys.BlockHitResult");
            Class<?> playerClass = cl.loadClass("net.minecraft.server.level.ServerPlayer");
            Class<?> levelClass = cl.loadClass("net.minecraft.server.level.ServerLevel");
            // try 5-arg (MC < 1.21.2)
            for (Method m : gmClass.getMethods()) {
              if (m.getParameterCount() == 5
                  && m.getParameterTypes()[0] == playerClass
                  && m.getParameterTypes()[2] == itemStackClass
                  && m.getParameterTypes()[3] == handClass
                  && m.getParameterTypes()[4] == hitClass) {
                m.setAccessible(true);
                useItemOnReflect = m;
                break;
              }
            }
            // try 3-arg (MC 1.21.2+)
            if (useItemOnReflect == null) {
              for (Method m : gmClass.getMethods()) {
                if (m.getParameterCount() == 3
                    && m.getParameterTypes()[0] == itemStackClass
                    && m.getParameterTypes()[1] == handClass
                    && m.getParameterTypes()[2] == hitClass) {
                  m.setAccessible(true);
                  useItemOnReflect = m;
                  break;
                }
              }
            }
          } catch (ClassNotFoundException ignored) {
          }
        }
      }
    }
    if (useItemOnReflect != null) {
      try {
        Object itemStack = nms.getItemInHand(hand);
        if (useItemOnReflect.getParameterCount() == 5) {
          return useItemOnReflect.invoke(nms.gameMode, nms, nms.level(), itemStack, hand, blockHit);
        } else {
          return useItemOnReflect.invoke(nms.gameMode, itemStack, hand, blockHit);
        }
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  // ── useItem ──

  private static volatile int useItemProbeState;
  private static volatile Method useItemReflect;

  public static Object useItem(ServerPlayer nms, InteractionHand hand) {
    if (useItemProbeState == 0) {
      try {
        var r = nms.gameMode.useItem(nms, nms.level(), nms.getItemInHand(hand), hand);
        useItemProbeState = 1;
        return r;
      } catch (NoSuchMethodError e) {
        useItemProbeState = 2;
      }
    } else if (useItemProbeState == 1) {
      return nms.gameMode.useItem(nms, nms.level(), nms.getItemInHand(hand), hand);
    }
    if (useItemReflect == null) {
      synchronized (NmsPlayerSpawner.class) {
        if (useItemReflect == null) {
          Class<?> gmClass = nms.gameMode.getClass();
          ClassLoader cl = gmClass.getClassLoader();
          try {
            Class<?> itemStackClass = cl.loadClass("net.minecraft.world.item.ItemStack");
            Class<?> handClass = cl.loadClass("net.minecraft.world.InteractionHand");
            Class<?> playerClass = cl.loadClass("net.minecraft.server.level.ServerPlayer");
            Class<?> levelClass = cl.loadClass("net.minecraft.server.level.ServerLevel");
            // try 4-arg (MC < 1.21.2)
            for (Method m : gmClass.getMethods()) {
              if (m.getParameterCount() == 4
                  && m.getParameterTypes()[0] == playerClass
                  && m.getParameterTypes()[1] == levelClass
                  && m.getParameterTypes()[2] == itemStackClass
                  && m.getParameterTypes()[3] == handClass) {
                m.setAccessible(true);
                useItemReflect = m;
                break;
              }
            }
            // try 2-arg (MC 1.21.2+)
            if (useItemReflect == null) {
              for (Method m : gmClass.getMethods()) {
                if (m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == itemStackClass
                    && m.getParameterTypes()[1] == handClass) {
                  m.setAccessible(true);
                  useItemReflect = m;
                  break;
                }
              }
            }
          } catch (ClassNotFoundException ignored) {
          }
        }
      }
    }
    if (useItemReflect != null) {
      try {
        Object itemStack = nms.getItemInHand(hand);
        if (useItemReflect.getParameterCount() == 4) {
          return useItemReflect.invoke(nms.gameMode, nms, nms.level(), itemStack, hand);
        } else {
          return useItemReflect.invoke(nms.gameMode, itemStack, hand);
        }
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  // ── handleBlockBreakAction ──

  private static volatile int breakActionProbeState;
  private static volatile Method breakActionReflect;

  public static void handleBlockBreakAction(ServerPlayer nms, BlockPos pos,
                                            ServerboundPlayerActionPacket.Action action,
                                            Direction direction, int maxY, int sequence) {
    if (breakActionProbeState == 0) {
      try {
        nms.gameMode.handleBlockBreakAction(pos, action, direction, maxY, sequence);
        breakActionProbeState = 1;
        return;
      } catch (NoSuchMethodError e) {
        breakActionProbeState = 2;
      }
    } else if (breakActionProbeState == 1) {
      nms.gameMode.handleBlockBreakAction(pos, action, direction, maxY, sequence);
      return;
    }
    if (breakActionReflect == null) {
      synchronized (NmsPlayerSpawner.class) {
        if (breakActionReflect == null) {
          Class<?> gmClass = nms.gameMode.getClass();
          ClassLoader cl = gmClass.getClassLoader();
          try {
            Class<?> bpClass = cl.loadClass("net.minecraft.core.BlockPos");
            Class<?> actionClass = cl.loadClass("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");
            // try 5-arg, then 3-arg, then scan by param count
            for (Method m : gmClass.getMethods()) {
              if (m.getParameterCount() == 5
                  && m.getParameterTypes()[0] == bpClass
                  && m.getParameterTypes()[1] == actionClass) {
                m.setAccessible(true);
                breakActionReflect = m;
                break;
              }
            }
            if (breakActionReflect == null) {
              for (Method m : gmClass.getMethods()) {
                if (m.getParameterCount() == 3
                    && m.getParameterTypes()[0] == bpClass
                    && m.getParameterTypes()[1] == actionClass) {
                  m.setAccessible(true);
                  breakActionReflect = m;
                  break;
                }
              }
            }
          } catch (ClassNotFoundException ignored) {
          }
        }
      }
    }
    if (breakActionReflect != null) {
      try {
        if (breakActionReflect.getParameterCount() == 5) {
          breakActionReflect.invoke(nms.gameMode, pos, action, direction, maxY, sequence);
        } else {
          breakActionReflect.invoke(nms.gameMode, pos, action, sequence);
        }
      } catch (Exception ignored) {
      }
    }
  }

  // ── destroyBlockProgress ──

  private static volatile int destroyProgressProbeState;
  private static volatile Method destroyProgressReflect;

  public static void destroyBlockProgress(ServerPlayer nms, int breakerId,
                                          BlockPos pos, int progress) {
    if (destroyProgressProbeState == 0) {
      try {
        nms.level().destroyBlockProgress(breakerId, pos, progress);
        destroyProgressProbeState = 1;
        return;
      } catch (NoSuchMethodError e) {
        destroyProgressProbeState = 2;
      }
    } else if (destroyProgressProbeState == 1) {
      nms.level().destroyBlockProgress(breakerId, pos, progress);
      return;
    }
    if (destroyProgressReflect == null) {
      synchronized (NmsPlayerSpawner.class) {
        if (destroyProgressReflect == null) {
          Class<?> levelClass = nms.level().getClass();
          ClassLoader cl = levelClass.getClassLoader();
          try {
            Class<?> bpClass = cl.loadClass("net.minecraft.core.BlockPos");
            Class<?> entityClass = cl.loadClass("net.minecraft.world.entity.Entity");
            // try 3-arg (int, BlockPos, int)
            for (Method m : levelClass.getMethods()) {
              if (m.getParameterCount() == 3
                  && m.getParameterTypes()[1] == bpClass
                  && m.getParameterTypes()[0] == int.class) {
                m.setAccessible(true);
                destroyProgressReflect = m;
                break;
              }
            }
            // try 3-arg (Entity, BlockPos, int) — MC 1.21.2+
            if (destroyProgressReflect == null) {
              for (Method m : levelClass.getMethods()) {
                if (m.getParameterCount() == 3
                    && m.getParameterTypes()[0] == entityClass
                    && m.getParameterTypes()[1] == bpClass) {
                  m.setAccessible(true);
                  destroyProgressReflect = m;
                  break;
                }
              }
            }
          } catch (ClassNotFoundException ignored) {
          }
        }
      }
    }
    if (destroyProgressReflect != null) {
      try {
        Object firstArg = destroyProgressReflect.getParameterTypes()[0] == int.class
            ? breakerId : nms;
        destroyProgressReflect.invoke(nms.level(), firstArg, pos, progress);
      } catch (Exception ignored) {
      }
    }
  }

  // ── startSleepInBed ──

  private static volatile int sleepProbeState;
  private static volatile Method sleepReflect;

  public static void startSleepInBed(
      ServerPlayer nms, BlockPos pos, boolean force) {
    if (sleepProbeState == 0) {
      try {
        nms.startSleepInBed(pos, force);
        sleepProbeState = 1;
        return;
      } catch (NoSuchMethodError e) {
        sleepProbeState = 2;
      }
    } else if (sleepProbeState == 1) {
      nms.startSleepInBed(pos, force);
      return;
    }
    if (sleepReflect == null) {
      synchronized (NmsPlayerSpawner.class) {
        if (sleepReflect == null) {
          ClassLoader cl = nms.getClass().getClassLoader();
          try {
            Class<?> bpClass = cl.loadClass("net.minecraft.core.BlockPos");
            // walk class hierarchy for startSleepInBed(BlockPos, ...) or startSleepInBed(BlockPos)
            Class<?> cur = nms.getClass();
            while (cur != null && cur != Object.class) {
              for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals("startSleepInBed") && m.getParameterCount() >= 1
                    && m.getParameterTypes()[0] == bpClass) {
                  m.setAccessible(true);
                  sleepReflect = m;
                  break;
                }
              }
              if (sleepReflect != null) break;
              cur = cur.getSuperclass();
            }
          } catch (ClassNotFoundException ignored) {
          }
        }
      }
    }
    if (sleepReflect != null) {
      try {
        if (sleepReflect.getParameterCount() >= 2) {
          sleepReflect.invoke(nms, pos, force);
        } else {
          sleepReflect.invoke(nms, pos);
        }
      } catch (Exception ignored) {
      }
    }
  }

  // ── interactOn ──

  private static volatile int interactOnProbeState;

  public static boolean interactOnEntity(ServerPlayer nms, net.minecraft.world.entity.Entity entity, InteractionHand hand) {
    if (interactOnProbeState == 0) {
      try {
        var result = nms.interactOn(entity, hand);
        interactOnProbeState = 1;
        return result != null && result.consumesAction();
      } catch (NoSuchMethodError e) {
        interactOnProbeState = 2;
      }
    } else if (interactOnProbeState == 1) {
      var result = nms.interactOn(entity, hand);
      return result != null && result.consumesAction();
    }
    return false;
  }

  // ── consumesAction helper ──

  public static boolean consumesAction(Object result) {
    if (result == null) return false;
    try {
      Method m = result.getClass().getMethod("consumesAction");
      return (boolean) m.invoke(result);
    } catch (Exception e) {
      return false;
    }
  }
}
