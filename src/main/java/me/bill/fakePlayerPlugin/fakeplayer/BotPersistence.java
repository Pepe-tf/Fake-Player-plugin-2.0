package me.bill.fakePlayerPlugin.fakeplayer;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBotSaveEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.command.AttackCommand;
import me.bill.fakePlayerPlugin.command.FollowCommand;
import me.bill.fakePlayerPlugin.command.MineCommand;
import me.bill.fakePlayerPlugin.command.MoveCommand;
import me.bill.fakePlayerPlugin.command.PlaceCommand;
import me.bill.fakePlayerPlugin.command.UseCommand;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.util.BotDataYaml;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.minecraft.core.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BotPersistence {

  private static final String FILE_NAME = "active-bots.yml";
  private static final String INV_FILE_NAME = "bot-inventories.yml";
  private static final String TASKS_FILE_NAME = "bot-tasks.yml";
  private static final String XP_FILE_NAME = "bot-xp.yml";
  private static final String ROOT_BOTS = "persistence.active-bots";
  private static final String ROOT_INVENTORIES = "persistence.inventories";
  private static final String ROOT_XP = "persistence.xp";
  private static final String ROOT_TASKS = "persistence.tasks";

  private final File dataFile;
  private final File inventoryFile;
  private final File tasksFile;
  private final File xpFile;
  private final File unifiedFile;
  private final FakePlayerPlugin plugin;

  private MoveCommand moveCommand;
  private MineCommand mineCommand;
  private PlaceCommand placeCommand;
  private UseCommand useCommand;
  private AttackCommand attackCommand;
  private FollowCommand followCommand;

  public void setMoveCommand(MoveCommand cmd) {
    this.moveCommand = cmd;
  }

  public void setMineCommand(MineCommand cmd) {
    this.mineCommand = cmd;
  }

  public void setPlaceCommand(PlaceCommand cmd) {
    this.placeCommand = cmd;
  }

  public void setUseCommand(UseCommand cmd) {
    this.useCommand = cmd;
  }

  public void setAttackCommand(AttackCommand cmd) {
    this.attackCommand = cmd;
  }

  public void setFollowCommand(FollowCommand cmd) {
    this.followCommand = cmd;
  }

  private Map<String, Map<String, String>> loadedInventories = null;

  private Map<String, XpEntry> loadedXp = null;

  private Map<String, TaskEntry> loadedTasks = null;

  public BotPersistence(FakePlayerPlugin plugin) {
    this.plugin = plugin;
    File dataDir = new File(plugin.getDataFolder(), "data");
    if (!dataDir.exists() && !dataDir.mkdirs()) {
      FppLogger.warn(
          "BotPersistence: could not create data directory: " + dataDir.getAbsolutePath());
    }
    this.dataFile = new File(dataDir, FILE_NAME);
    this.inventoryFile = new File(dataDir, INV_FILE_NAME);
    this.tasksFile = new File(dataDir, TASKS_FILE_NAME);
    this.xpFile = new File(dataDir, XP_FILE_NAME);
    this.unifiedFile = BotDataYaml.getFile(plugin);
  }

  public void save(Iterable<FakePlayer> players) {
    fireSaveEvents(players);
    saveInternal(players);
    saveInventoriesInternal(players);
    saveXpInternal(players);
    saveTasksInternal(players);
  }

  public void saveAsync(Iterable<FakePlayer> players) {

    fireSaveEvents(players);
    List<Object> list = buildList(players);
    Map<String, Map<String, String>> invSnap = snapshotInventories(players);
    Map<String, XpEntry> xpSnap = snapshotXp(players);
    Map<String, TaskEntry> taskSnap = snapshotTasks(players);
    FppScheduler.runAsync(
        plugin,
        () -> {
          try {
            BotDataYaml.replaceSection(
                plugin,
                ROOT_BOTS,
                section -> {
                  section.set("bots", list);
                });
          } catch (IOException e) {
            FppLogger.error("Failed to auto-save active bots: " + e.getMessage());
          }
          writeInventorySnapshot(invSnap);
          writeXpSnapshot(xpSnap);
          writeTaskSnapshot(taskSnap);
        });
  }

  private void saveInternal(Iterable<FakePlayer> players) {
    try {
      BotDataYaml.replaceSection(
          plugin,
          ROOT_BOTS,
          section -> {
            section.set("bots", buildList(players));
          });
      deleteFile(dataFile);
      FppLogger.info("Saved bot list to " + FILE_NAME + ".");
    } catch (IOException e) {
      FppLogger.error("Failed to save active bots: " + e.getMessage());
    }
  }

  private void saveInventoriesInternal(Iterable<FakePlayer> players) {
    writeInventorySnapshot(snapshotInventories(players));
  }

  private void saveXpInternal(Iterable<FakePlayer> players) {
    writeXpSnapshot(snapshotXp(players));
  }

  private void saveTasksInternal(Iterable<FakePlayer> players) {
    writeTaskSnapshot(snapshotTasks(players));
  }

  private void fireSaveEvents(Iterable<FakePlayer> players) {
    for (FakePlayer fp : players) {
      Bukkit.getPluginManager().callEvent(new FppBotSaveEvent(new FppBotImpl(fp)));
      persistSkinCheckpoint(fp);
    }
  }

  private void persistSkinCheckpoint(FakePlayer fp) {
    if (fp == null || plugin.getDatabaseManager() == null) return;
    SkinProfile skin = fp.getResolvedSkin();
    if (skin != null && skin.isValid()) {
      plugin.getDatabaseManager().updateBotSkin(fp.getUuid().toString(), skin.getValue(), skin.getSignature());
    }
  }

  private Map<String, TaskEntry> snapshotTasks(Iterable<FakePlayer> players) {
    Map<String, TaskEntry> snap = new LinkedHashMap<>();
    for (FakePlayer fp : players) {
      String uuidStr = fp.getUuid().toString();

      String rcc = fp.getRightClickCommand();

      String useWorld = null;
      double useX = 0, useY = 0, useZ = 0;
      float useYaw = 0, usePitch = 0;
      boolean useOnce = false;
      if (useCommand != null) {
        Location useLoc = useCommand.getActiveUseLocation(fp.getUuid());
        if (useLoc != null && useLoc.getWorld() != null) {
          useWorld = useLoc.getWorld().getName();
          useX = useLoc.getX();
          useY = useLoc.getY();
          useZ = useLoc.getZ();
          useYaw = useLoc.getYaw();
          usePitch = useLoc.getPitch();
          useOnce = useCommand.isActiveUseOnce(fp.getUuid());
        }
      }

      String mineWorld = null;
      double mineX = 0, mineY = 0, mineZ = 0;
      float mineYaw = 0, minePitch = 0;
      boolean mineOnce = false;
      if (mineCommand != null) {
        BlockPos minePos = mineCommand.getActiveMineTarget(fp.getUuid());
        if (minePos != null && fp.getPlayer() != null && fp.getPlayer().getWorld() != null) {
          mineWorld = fp.getPlayer().getWorld().getName();
          mineX = minePos.getX();
          mineY = minePos.getY();
          mineZ = minePos.getZ();
          mineOnce = mineCommand.isActiveMineOnce(fp.getUuid());
        }
      }

      String placeWorld = null;
      double placeX = 0, placeY = 0, placeZ = 0;
      float placeYaw = 0, placePitch = 0;
      boolean placeOnce = false;
      if (placeCommand != null) {
        Location placeLoc = placeCommand.getActivePlaceLocation(fp.getUuid());
        if (placeLoc != null && placeLoc.getWorld() != null) {
          placeWorld = placeLoc.getWorld().getName();
          placeX = placeLoc.getX();
          placeY = placeLoc.getY();
          placeZ = placeLoc.getZ();
          placeYaw = placeLoc.getYaw();
          placePitch = placeLoc.getPitch();
          placeOnce = placeCommand.isActivePlaceOnce(fp.getUuid());
        }
      }

      String areaPos1World = null, areaPos2World = null;
      double areaPos1X = 0, areaPos1Y = 0, areaPos1Z = 0;
      double areaPos2X = 0, areaPos2Y = 0, areaPos2Z = 0;
      boolean areaActive = false;
      if (mineCommand != null) {
        Location aPos1 = mineCommand.getSelectionPos1(fp.getUuid());
        Location aPos2 = mineCommand.getSelectionPos2(fp.getUuid());
        if (aPos1 != null
            && aPos1.getWorld() != null
            && aPos2 != null
            && aPos2.getWorld() != null) {
          areaPos1World = aPos1.getWorld().getName();
          areaPos1X = aPos1.getX();
          areaPos1Y = aPos1.getY();
          areaPos1Z = aPos1.getZ();
          areaPos2World = aPos2.getWorld().getName();
          areaPos2X = aPos2.getX();
          areaPos2Y = aPos2.getY();
          areaPos2Z = aPos2.getZ();
          areaActive = mineCommand.hasActiveAreaJob(fp.getUuid());
        }
      }

      String attackWorld = null;
      double attackX = 0, attackY = 0, attackZ = 0;
      float attackYaw = 0, attackPitch = 0;
      boolean attackOnce = false;
      String attackMode = null;
      double attackRange = Config.attackMobDefaultRange();
      String attackPriority = Config.attackMobDefaultPriority();
      String attackMobTypes = null;
      String attackSmartMode = null;
      if (attackCommand != null) {
        Location attackLoc = attackCommand.getActiveAttackLocation(fp.getUuid());
        if (attackLoc != null && attackLoc.getWorld() != null) {
          attackWorld = attackLoc.getWorld().getName();
          attackX = attackLoc.getX();
          attackY = attackLoc.getY();
          attackZ = attackLoc.getZ();
          attackYaw = attackLoc.getYaw();
          attackPitch = attackLoc.getPitch();
          attackOnce = attackCommand.isActiveAttackOnce(fp.getUuid());
          attackMode = attackCommand.getAttackMode(fp.getUuid());
          if (attackMode != null && !attackMode.equals("classic")) {
            attackRange = attackCommand.getAttackRange(fp.getUuid());
            attackPriority = attackCommand.getAttackPriority(fp.getUuid());
            Set<EntityType> types = attackCommand.getAttackFilterTypes(fp.getUuid());
            if (types != null && !types.isEmpty()) {
              StringBuilder sb = new StringBuilder();
              for (EntityType et : types) {
                if (sb.length() > 0) sb.append(',');
                sb.append(et.name());
              }
              attackMobTypes = sb.toString();
            }
            FakePlayer.PveSmartAttackMode sm = attackCommand.getAttackSmartMode(fp.getUuid());
            if (sm != null) attackSmartMode = sm.name();
          }
        }
      }

      String followTargetUuid = null;
      if (followCommand != null && followCommand.isFollowing(fp.getUuid())) {
        UUID targetUuid = followCommand.getFollowTarget(fp.getUuid());
        if (targetUuid != null) followTargetUuid = targetUuid.toString();
      }

      String roamWorld = null;
      double roamX = 0, roamY = 0, roamZ = 0;
      double roamRadius = 0;
      if (moveCommand != null && moveCommand.isRoaming(fp.getUuid())) {
        Location roamCenter = moveCommand.getRoamCenter(fp.getUuid());
        Double roamR = moveCommand.getRoamRadius(fp.getUuid());
        if (roamCenter != null && roamCenter.getWorld() != null && roamR != null) {
          roamWorld = roamCenter.getWorld().getName();
          roamX = roamCenter.getX();
          roamY = roamCenter.getY();
          roamZ = roamCenter.getZ();
          roamRadius = roamR;
        }
      }

      if (rcc != null
          || mineWorld != null
          || useWorld != null
          || areaPos1World != null
          || placeWorld != null
          || attackWorld != null
          || followTargetUuid != null
          || roamWorld != null) {
        snap.put(
            uuidStr,
            new TaskEntry(
                rcc,
                mineWorld,
                mineX,
                mineY,
                mineZ,
                mineYaw,
                minePitch,
                mineOnce,
                useWorld,
                useX,
                useY,
                useZ,
                useYaw,
                usePitch,
                useOnce,
                areaPos1World,
                areaPos1X,
                areaPos1Y,
                areaPos1Z,
                areaPos2World,
                areaPos2X,
                areaPos2Y,
                areaPos2Z,
                areaActive,
                placeWorld,
                placeX,
                placeY,
                placeZ,
                placeYaw,
                placePitch,
                placeOnce,
                attackWorld,
                attackX,
                attackY,
                attackZ,
                attackYaw,
                attackPitch,
                attackOnce,
                attackMode,
                attackRange,
                attackPriority,
                attackMobTypes,
                attackSmartMode,
                followTargetUuid,
                roamWorld,
                roamX,
                roamY,
                roamZ,
                roamRadius));
      }
    }
    return snap;
  }

  private void writeTaskSnapshot(Map<String, TaskEntry> snap) {
    try {
      BotDataYaml.replaceSection(
          plugin,
          ROOT_TASKS,
          section -> {
            for (Map.Entry<String, TaskEntry> e : snap.entrySet()) {
              String sec = e.getKey() + ".";
              TaskEntry t = e.getValue();
              if (t.rightClickCommand() != null)
                section.set(sec + "right-click-command", t.rightClickCommand());
              if (t.mineWorld() != null) {
                section.set(sec + "mine-world", t.mineWorld());
                section.set(sec + "mine-x", t.mineX());
                section.set(sec + "mine-y", t.mineY());
                section.set(sec + "mine-z", t.mineZ());
                section.set(sec + "mine-yaw", (double) t.mineYaw());
                section.set(sec + "mine-pitch", (double) t.minePitch());
                section.set(sec + "mine-once", t.mineOnce());
              }
              if (t.useWorld() != null) {
                section.set(sec + "use-world", t.useWorld());
                section.set(sec + "use-x", t.useX());
                section.set(sec + "use-y", t.useY());
                section.set(sec + "use-z", t.useZ());
                section.set(sec + "use-yaw", (double) t.useYaw());
                section.set(sec + "use-pitch", (double) t.usePitch());
                section.set(sec + "use-once", t.useOnce());
              }
              if (t.placeWorld() != null) {
                section.set(sec + "place-world", t.placeWorld());
                section.set(sec + "place-x", t.placeX());
                section.set(sec + "place-y", t.placeY());
                section.set(sec + "place-z", t.placeZ());
                section.set(sec + "place-yaw", (double) t.placeYaw());
                section.set(sec + "place-pitch", (double) t.placePitch());
                section.set(sec + "place-once", t.placeOnce());
              }
              if (t.attackWorld() != null) {
                section.set(sec + "attack-world", t.attackWorld());
                section.set(sec + "attack-x", t.attackX());
                section.set(sec + "attack-y", t.attackY());
                section.set(sec + "attack-z", t.attackZ());
                section.set(sec + "attack-yaw", (double) t.attackYaw());
                section.set(sec + "attack-pitch", (double) t.attackPitch());
                section.set(sec + "attack-once", t.attackOnce());
                if (t.attackMode() != null) {
                  section.set(sec + "attack-mode", t.attackMode());
                  section.set(sec + "attack-range", t.attackRange());
                  section.set(sec + "attack-priority", t.attackPriority());
                  if (t.attackMobTypes() != null)
                    section.set(sec + "attack-mob-types", t.attackMobTypes());
                  if (t.attackSmartMode() != null)
                    section.set(sec + "attack-smart-mode", t.attackSmartMode());
                }
              }
              if (t.followTargetUuid() != null) {
                section.set(sec + "follow-target", t.followTargetUuid());
              }
              if (t.roamWorld() != null) {
                section.set(sec + "roam-world", t.roamWorld());
                section.set(sec + "roam-x", t.roamX());
                section.set(sec + "roam-y", t.roamY());
                section.set(sec + "roam-z", t.roamZ());
                section.set(sec + "roam-radius", t.roamRadius());
              }
              if (t.areaPos1World() != null && t.areaPos2World() != null) {
                section.set(sec + "area-pos1-world", t.areaPos1World());
                section.set(sec + "area-pos1-x", t.areaPos1X());
                section.set(sec + "area-pos1-y", t.areaPos1Y());
                section.set(sec + "area-pos1-z", t.areaPos1Z());
                section.set(sec + "area-pos2-world", t.areaPos2World());
                section.set(sec + "area-pos2-x", t.areaPos2X());
                section.set(sec + "area-pos2-y", t.areaPos2Y());
                section.set(sec + "area-pos2-z", t.areaPos2Z());
                section.set(sec + "area-active", t.areaActive());
              }
            }
          });
      deleteFile(tasksFile);
      Config.debug("Saved task state for " + snap.size() + " bot(s) to YAML.");
    } catch (IOException ex) {
      FppLogger.error("Failed to save bot tasks: " + ex.getMessage());
    }

    DatabaseManager db = plugin.getDatabaseManager();
    if (db != null) {
      db.saveBotTasks(buildTaskRows(snap));
    }
  }

  private List<DatabaseManager.BotTaskRow> buildTaskRows(
      Map<String, TaskEntry> snap) {
    List<DatabaseManager.BotTaskRow> rows = new ArrayList<>();
    String serverId = Config.serverId();
    for (Map.Entry<String, TaskEntry> e : snap.entrySet()) {
      String uuid = e.getKey();
      TaskEntry t = e.getValue();
      if (t.mineWorld() != null) {
        rows.add(
            new DatabaseManager.BotTaskRow(
                uuid,
                serverId,
                "MINE",
                t.mineWorld(),
                t.mineX(),
                t.mineY(),
                t.mineZ(),
                t.mineYaw(),
                t.minePitch(),
                t.mineOnce(),
                null,
                false));
      }
      if (t.useWorld() != null) {
        rows.add(
            new DatabaseManager.BotTaskRow(
                uuid,
                serverId,
                "USE",
                t.useWorld(),
                t.useX(),
                t.useY(),
                t.useZ(),
                t.useYaw(),
                t.usePitch(),
                t.useOnce(),
                null,
                false));
      }
      if (t.placeWorld() != null) {
        rows.add(
            new DatabaseManager.BotTaskRow(
                uuid,
                serverId,
                "PLACE",
                t.placeWorld(),
                t.placeX(),
                t.placeY(),
                t.placeZ(),
                t.placeYaw(),
                t.placePitch(),
                t.placeOnce(),
                null,
                false));
      }
      if (t.attackWorld() != null) {
        String attackExtra = null;
        boolean attackExtraBool = false;
        if (t.attackMode() != null && !t.attackMode().equals("classic")) {
          attackExtra =
              t.attackMode()
                  + ":"
                  + t.attackRange()
                  + ":"
                  + (t.attackPriority() != null ? t.attackPriority() : "")
                  + ":"
                  + (t.attackSmartMode() != null ? t.attackSmartMode() : "")
                  + ":"
                  + (t.attackMobTypes() != null ? t.attackMobTypes() : "");
          attackExtraBool = t.attackMode().equals("hunt");
        }
        rows.add(
            new DatabaseManager.BotTaskRow(
                uuid,
                serverId,
                "ATTACK",
                t.attackWorld(),
                t.attackX(),
                t.attackY(),
                t.attackZ(),
                t.attackYaw(),
                t.attackPitch(),
                t.attackOnce(),
                attackExtra,
                attackExtraBool));
      }
      if (t.followTargetUuid() != null) {
        rows.add(
            new DatabaseManager.BotTaskRow(
                uuid,
                serverId,
                "FOLLOW",
                null,
                0,
                0,
                0,
                0f,
                0f,
                false,
                t.followTargetUuid(),
                false));
      }
      if (t.roamWorld() != null) {
        rows.add(
            new DatabaseManager.BotTaskRow(
                uuid,
                serverId,
                "ROAM",
                t.roamWorld(),
                t.roamX(),
                t.roamY(),
                t.roamZ(),
                (float) t.roamRadius(),
                0f,
                false,
                null,
                false));
      }
    }
    return rows;
  }

  private Map<String, Map<String, String>> snapshotInventories(Iterable<FakePlayer> players) {
    Map<String, Map<String, String>> snap = new LinkedHashMap<>();
    for (FakePlayer fp : players) {
      Player bot = fp.getPlayer();
      if (bot == null || !bot.isValid()) continue;
      Map<String, String> slots = serializeInventory(bot.getInventory());
      if (!slots.isEmpty()) {
        snap.put(fp.getUuid().toString(), slots);
      }
    }
    return snap;
  }

  private Map<String, XpEntry> snapshotXp(Iterable<FakePlayer> players) {
    Map<String, XpEntry> snap = new LinkedHashMap<>();
    for (FakePlayer fp : players) {
      Player bot = fp.getPlayer();
      if (bot == null || !bot.isValid()) continue;
      snap.put(
          fp.getUuid().toString(),
          new XpEntry(bot.getTotalExperience(), bot.getLevel(), bot.getExp()));
    }
    return snap;
  }

  private void writeInventorySnapshot(Map<String, Map<String, String>> snap) {
    try {
      BotDataYaml.replaceSection(
          plugin,
          ROOT_INVENTORIES,
          section -> {
            for (Map.Entry<String, Map<String, String>> entry : snap.entrySet()) {
              String uuidKey = entry.getKey();
              for (Map.Entry<String, String> slot : entry.getValue().entrySet()) {
                section.set(uuidKey + "." + slot.getKey(), slot.getValue());
              }
            }
          });
      deleteFile(inventoryFile);
      Config.debug("Saved inventories for " + snap.size() + " bot(s).");
    } catch (IOException e) {
      FppLogger.error("Failed to save bot inventories: " + e.getMessage());
    }
  }

  private void writeXpSnapshot(Map<String, XpEntry> snap) {
    try {
      BotDataYaml.replaceSection(
          plugin,
          ROOT_XP,
          section -> {
            for (Map.Entry<String, XpEntry> entry : snap.entrySet()) {
              String base = entry.getKey() + ".";
              XpEntry xp = entry.getValue();
              section.set(base + "total", xp.totalExperience());
              section.set(base + "level", xp.level());
              section.set(base + "progress", (double) xp.progress());
            }
          });
      deleteFile(xpFile);
      Config.debug("Saved XP for " + snap.size() + " bot(s).");
    } catch (IOException e) {
      FppLogger.error("Failed to save bot XP: " + e.getMessage());
    }
  }

  private static Map<String, String> serializeInventory(PlayerInventory inv) {
    Map<String, String> slots = new LinkedHashMap<>();

    ItemStack[] contents = inv.getContents();
    for (int i = 0; i < contents.length; i++) {
      if (contents[i] != null && contents[i].getType() != Material.AIR) {
        try {
          slots.put(
              String.valueOf(i),
              Base64.getEncoder().encodeToString(contents[i].serializeAsBytes()));
        } catch (Exception ignored) {
        }
      }
    }

    ItemStack[] armour = inv.getArmorContents();
    for (int i = 0; i < armour.length; i++) {
      if (armour[i] != null && armour[i].getType() != Material.AIR) {
        try {
          slots.put(
              String.valueOf(36 + i),
              Base64.getEncoder().encodeToString(armour[i].serializeAsBytes()));
        } catch (Exception ignored) {
        }
      }
    }

    ItemStack offhand = inv.getItemInOffHand();
    if (offhand != null && offhand.getType() != Material.AIR) {
      try {
        slots.put("40", Base64.getEncoder().encodeToString(offhand.serializeAsBytes()));
      } catch (Exception ignored) {
      }
    }
    return slots;
  }

  private List<Object> buildList(Iterable<FakePlayer> players) {
    List<Object> list = new ArrayList<>();
    for (FakePlayer fp : players) {
      Entity body = fp.getPhysicsEntity();
      Location loc = (body != null && body.isValid()) ? body.getLocation() : fp.getSpawnLocation();
      if (loc == null || loc.getWorld() == null) continue;

      var section = new LinkedHashMap<String, Object>();
      section.put("name", fp.getName());
      section.put("uuid", fp.getUuid().toString());
      section.put("display-name", fp.getDisplayName());
      section.put("spawned-by", fp.getSpawnedBy());
      section.put("spawned-by-uuid", fp.getSpawnedByUuid().toString());
      section.put("world", loc.getWorld().getName());
      section.put("x", loc.getX());
      section.put("y", loc.getY());
      section.put("z", loc.getZ());
      section.put("yaw", (double) loc.getYaw());
      section.put("pitch", (double) loc.getPitch());
      section.put("bot-type", fp.getBotType().name());
      section.put("chat-enabled", fp.isChatEnabled());
      section.put("respawn-on-death", fp.isRespawnOnDeath());
      section.put("head-ai-enabled", fp.isHeadAiEnabled());
      section.put("pickup-items", fp.isPickUpItemsEnabled());
      section.put("pickup-xp", fp.isPickUpXpEnabled());
      section.put("frozen", fp.isFrozen());
      section.put("nav-parkour", fp.isNavParkour());
      section.put("nav-break-blocks", fp.isNavBreakBlocks());
      section.put("nav-place-blocks", fp.isNavPlaceBlocks());
      section.put("nav-avoid-water", fp.isNavAvoidWater());
      section.put("nav-avoid-lava", fp.isNavAvoidLava());
      section.put("swim-ai-enabled", fp.isSwimAiEnabled());
      section.put("auto-eat-enabled", fp.isAutoEatEnabled());
      section.put("auto-place-bed-enabled", fp.isAutoPlaceBedEnabled());
      section.put("auto-milk-enabled", fp.isAutoMilkEnabled());
      section.put("prevent-bad-omen", fp.isPreventBadOmen());
      section.put("chunk-load-radius", fp.getChunkLoadRadius());
      if (!fp.getSharedControllers().isEmpty()) {
        section.put(
            "shared-controllers",
            fp.getSharedControllers().stream().map(UUID::toString).sorted().toList());
      }
      section.put("pve-enabled", fp.isPveEnabled());
      section.put("pve-smart-attack-mode", fp.getPveSmartAttackMode().name());
      section.put("pve-range", fp.getPveRange());
      if (fp.getPvePriority() != null) section.put("pve-priority", fp.getPvePriority());
      if (fp.getPveMobType() != null) section.put("pve-mob-type", fp.getPveMobType());
      Player bot = fp.getPlayer();
      if (bot != null) {
        section.put("xp-total", bot.getTotalExperience());
        section.put("xp-level", bot.getLevel());
        section.put("xp-progress", (double) bot.getExp());
      }
      if (fp.hasCustomPing() || fp.isPingSimulated()) {
        section.put("ping", fp.getPing());
        section.put("ping-user-set", fp.hasCustomPing());
      }
      if (fp.getChatTier() != null) {
        section.put("chat-tier", fp.getChatTier());
      }
      if (fp.getAiPersonality() != null) {
        section.put("ai-personality", fp.getAiPersonality());
      }
      if (fp.getLuckpermsGroup() != null && !fp.getLuckpermsGroup().isBlank()) {
        section.put("luckperms-group", fp.getLuckpermsGroup());
      }
      if (fp.getRightClickCommand() != null) {
        section.put("right-click-command", fp.getRightClickCommand());
      }
      SkinProfile skin = fp.getResolvedSkin();
      if (skin != null && skin.isValid()) {
        section.put("skin-texture", skin.getValue());
        if (skin.getSignature() != null) {
          section.put("skin-signature", skin.getSignature());
        }
      }
      list.add(section);
    }
    return list;
  }

  public void restore(FakePlayerManager manager) {
    if (!Config.persistOnRestart()) {
      clearUnifiedSection(ROOT_BOTS);
      deleteFile(dataFile);
      FppLogger.info("Bot persistence is disabled - skipping restore.");
      return;
    }

    manager.setRestorationInProgress(true);

    loadInventoryFile();
    loadXpFile();

    loadTasksFile();

    boolean skinExtensionLoaded = isExtensionLoaded("FPP-Skin");
    boolean luckPermsExtensionLoaded = isExtensionLoaded("FPP-LuckPerms");
    boolean chatExtensionLoaded = isExtensionLoaded("FPP-Chat");
    boolean aiChatExtensionLoaded = isExtensionLoaded("FPP-AIChat");
    boolean commandExtensionLoaded = isExtensionLoaded("FPP-Command");
    boolean pingExtensionLoaded = isExtensionLoaded("FPP-Ping");

    DatabaseManager db = plugin.getDatabaseManager();
    if (db != null) {

      List<DatabaseManager.ActiveBotRow> rows =
          db.getActiveBotsForThisServer();
      if (!rows.isEmpty()) {
        Map<String, SkinProfile> yamlSkinFallback = loadYamlSkinFallback();
        FppLogger.info(
            "Restoring "
                + rows.size()
                + " bot(s) from database (server='"
                + Config.serverId()
                + "')...");

        db.clearActiveBots();

        clearUnifiedSection(ROOT_BOTS);
        deleteFile(dataFile);

        List<SavedBot> saved = new ArrayList<>();
        for (var row : rows) {
          try {
            UUID storedUuid = parseUuidOrNull(row.botUuid());
            UUID effectiveUuid = resolveRestoredUuid(row.botName(), storedUuid);
            if (effectiveUuid == null) continue;
            SkinProfile fallbackSkin = yamlSkinFallback.get(row.botName().toLowerCase(Locale.ROOT));
            String skinTexture = skinExtensionLoaded ? row.skinTexture() : null;
            String skinSignature = skinExtensionLoaded ? row.skinSignature() : null;
            if (skinExtensionLoaded
                && (skinTexture == null || skinTexture.isBlank())
                && fallbackSkin != null
                && fallbackSkin.isValid()) {
              skinTexture = fallbackSkin.getValue();
              skinSignature = fallbackSkin.getSignature();
            }
            saved.add(
                new SavedBot(
                    row.botName(),
                    effectiveUuid,
                    row.botDisplay(),
                    row.spawnedBy(),
                    UUID.fromString(row.spawnedByUuid()),
                    row.world(),
                    row.x(),
                    row.y(),
                    row.z(),
                    row.yaw(),
                    row.pitch(),
                    luckPermsExtensionLoaded ? row.luckpermsGroup() : null,
                    BotType.AFK,
                    chatExtensionLoaded && row.chatEnabled(),
                    row.respawnOnDeath(),
                    chatExtensionLoaded ? row.chatTier() : null,
                    aiChatExtensionLoaded ? row.aiPersonality() : null,
                    row.headAiEnabled(),
                    row.pickUpItems(),
                    row.pickUpXp(),
                    0,
                    0,
                    0f,
                    row.frozen(),
                    row.navParkour(),
                    row.navBreakBlocks(),
                    row.navPlaceBlocks(),
                    row.navAvoidWater(),
                    row.navAvoidLava(),
                    row.swimAiEnabled(),
                    pingExtensionLoaded ? row.ping() : -1,
                    commandExtensionLoaded ? row.rightClickCmd() : null,
                    row.pveEnabled(),
                    row.pveSmartAttackMode(),
                    row.pveRange(),
                    row.pvePriority(),
                    row.pveMobType(),
                    skinTexture,
                    skinSignature,
                    Set.of(),
                    Config.autoEatEnabled(),
                    Config.autoPlaceBedEnabled(),
                    row.autoMilkEnabled(),
                    row.preventBadOmen(),
                    pingExtensionLoaded && row.pingUserSet()));
          } catch (Exception e) {
            FppLogger.warn("Skipping malformed DB active-bot row: " + e.getMessage());
          }
        }
        if (!saved.isEmpty()) {
          FppScheduler.runSyncLater(plugin, () -> restoreChain(manager, saved, 0), 40L);
        } else {

          manager.setRestorationInProgress(false);
        }
        return;
      }
    }

    YamlConfiguration unified = BotDataYaml.load(plugin);
    List<?> raw = unified.getList(ROOT_BOTS + ".bots");
    if ((raw == null || raw.isEmpty()) && dataFile.exists()) {
      YamlConfiguration legacy = YamlConfiguration.loadConfiguration(dataFile);
      raw = legacy.getList("bots");
      if (raw != null && !raw.isEmpty()) {
        final List<?> migrated = raw;
        try {
          BotDataYaml.replaceSection(
              plugin,
              ROOT_BOTS,
              section -> {
                section.set("bots", migrated);
              });
          deleteFile(dataFile);
        } catch (IOException e) {
          FppLogger.warn("Failed to migrate " + FILE_NAME + " to " + BotDataYaml.FILE_NAME + ": " + e.getMessage());
        }
      }
    }

    if (raw == null || raw.isEmpty()) {
      clearUnifiedSection(ROOT_BOTS);
      deleteFile(dataFile);
      manager.setRestorationInProgress(false);
      return;
    }

    List<SavedBot> saved = new ArrayList<>();
    for (Object obj : raw) {
      if (!(obj instanceof Map<?, ?> map)) continue;
      try {
        String name = (String) map.get("name");
        UUID storedUuid = parseUuidOrNull((String) map.get("uuid"));
        UUID uuid = resolveRestoredUuid(name, storedUuid);
        if (uuid == null) continue;
        String displayName = (String) map.get("display-name");
        Object sbRaw = map.get("spawned-by");
        String spawnedBy = sbRaw instanceof String s ? s : "SERVER";
        Object sbuRaw = map.get("spawned-by-uuid");
        UUID spawnedByUuid = sbuRaw instanceof String str ? UUID.fromString(str) : new UUID(0, 0);
        String worldName = (String) map.get("world");
        double x = toDouble(map.get("x"));
        double y = toDouble(map.get("y"));
        double z = toDouble(map.get("z"));
        float yaw = (float) toDouble(map.get("yaw"));
        float pitch = (float) toDouble(map.get("pitch"));
        Object btRaw = map.get("bot-type");
        BotType botType = btRaw instanceof String bts ? BotType.parse(bts) : BotType.AFK;
        Object ceRaw = map.get("chat-enabled");
        boolean chatEnabled = !(ceRaw instanceof Boolean b) || b;
        Object rodRaw = map.get("respawn-on-death");
        boolean respawnOnDeath = rodRaw instanceof Boolean rod ? rod : Config.respawnOnDeath();
        Object headAiRaw = map.get("head-ai-enabled");
        boolean headAiEnabled = !(headAiRaw instanceof Boolean hai) || hai;
        Object pickupItemsRaw = map.get("pickup-items");
        boolean pickUpItems = pickupItemsRaw instanceof Boolean pi ? pi : Config.bodyPickUpItems();
        Object pickupXpRaw = map.get("pickup-xp");
        boolean pickUpXp = pickupXpRaw instanceof Boolean px ? px : Config.bodyPickUpXp();
        Object frozenRaw = map.get("frozen");
        boolean frozen = frozenRaw instanceof Boolean fr && fr;
        Object navPkRaw = map.get("nav-parkour");
        boolean navParkour = navPkRaw instanceof Boolean npk ? npk : Config.pathfindingParkour();
        Object navBbRaw = map.get("nav-break-blocks");
        boolean navBreakBlocks =
            navBbRaw instanceof Boolean nbb ? nbb : Config.pathfindingBreakBlocks();
        Object navPbRaw = map.get("nav-place-blocks");
        boolean navPlaceBlocks =
            navPbRaw instanceof Boolean npb ? npb : Config.pathfindingPlaceBlocks();
        Object navAwRaw = map.get("nav-avoid-water");
        boolean navAvoidWater = navAwRaw instanceof Boolean naw && naw;
        Object navAlRaw = map.get("nav-avoid-lava");
        boolean navAvoidLava = navAlRaw instanceof Boolean nal && nal;
        Object swimAiRaw = map.get("swim-ai-enabled");
        boolean swimAiEnabled = !(swimAiRaw instanceof Boolean sae) || sae;
        Object autoEatRaw = map.get("auto-eat-enabled");
        boolean autoEatEnabled = autoEatRaw instanceof Boolean aee ? aee : Config.autoEatEnabled();
        Object autoBedRaw = map.get("auto-place-bed-enabled");
        boolean autoPlaceBedEnabled = autoBedRaw instanceof Boolean apb ? apb : Config.autoPlaceBedEnabled();
        Object autoMilkRaw = map.get("auto-milk-enabled");
        boolean autoMilkEnabled = autoMilkRaw instanceof Boolean amk ? amk : Config.autoMilkEnabled();
        Object preventBadOmenRaw = map.get("prevent-bad-omen");
        boolean preventBadOmen = preventBadOmenRaw instanceof Boolean pbo ? pbo : Config.preventBadOmen();
        Set<UUID> sharedControllers = new LinkedHashSet<>();
        Object sharedRaw = map.get("shared-controllers");
        if (sharedRaw instanceof Iterable<?> sharedList) {
          for (Object entry : sharedList) {
            if (entry instanceof String str) {
              try {
                sharedControllers.add(UUID.fromString(str));
              } catch (IllegalArgumentException ignored) {
              }
            }
          }
        }
        Object clrRaw = map.get("chunk-load-radius");
        int chunkLoadRadius = clrRaw instanceof Number clrn ? clrn.intValue() : -1;
        Object pingRaw = map.get("ping");
        int ping = pingRaw instanceof Number pr ? pr.intValue() : -1;
        Object pingUserSetRaw = map.get("ping-user-set");
        boolean pingUserSet = pingUserSetRaw instanceof Boolean pus ? pus : (ping >= 0);
        Object rccRaw = map.get("right-click-command");
        String rightClickCommand = commandExtensionLoaded && rccRaw instanceof String rcc ? rcc : null;
        Object xpTotalRaw = map.get("xp-total");
        int xpTotal = xpTotalRaw instanceof Number n1 ? n1.intValue() : 0;
        Object xpLevelRaw = map.get("xp-level");
        int xpLevel = xpLevelRaw instanceof Number n2 ? n2.intValue() : 0;
        Object xpProgressRaw = map.get("xp-progress");
        float xpProgress = xpProgressRaw instanceof Number n3 ? n3.floatValue() : 0f;
        Object ctRaw = map.get("chat-tier");
        String chatTier = chatExtensionLoaded && ctRaw instanceof String s2 ? s2 : null;
        Object apRaw = map.get("ai-personality");
        String aiPersonality = aiChatExtensionLoaded && apRaw instanceof String s3 ? s3 : null;
        Object lpRaw = map.get("luckperms-group");
        String luckpermsGroup = luckPermsExtensionLoaded && lpRaw instanceof String s4 ? s4 : null;
        Object pveEnRaw = map.get("pve-enabled");
        boolean pveEnabled = pveEnRaw instanceof Boolean pve && pve;
        Object pveModeRaw = map.get("pve-smart-attack-mode");
        String pveSmartAttackMode =
            pveModeRaw instanceof String psm
                ? psm
                : (map.get("pve-move") instanceof Boolean pveMove && pveMove)
                  ? "ON_MOVE"
                  : (pveEnabled ? "ON_NO_MOVE" : "OFF");
        Object pveRgRaw = map.get("pve-range");
        double pveRange =
            pveRgRaw instanceof Number prn ? prn.doubleValue() : Config.attackMobDefaultRange();
        Object pvePrRaw = map.get("pve-priority");
        String pvePriority = pvePrRaw instanceof String pps ? pps : null;
        Object pveMtRaw = map.get("pve-mob-type");
        String pveMobType = pveMtRaw instanceof String pmt ? pmt : null;
        Object skinTexRaw = map.get("skin-texture");
        String skinTexture = skinExtensionLoaded && skinTexRaw instanceof String st ? st : null;
        Object skinSigRaw = map.get("skin-signature");
        String skinSignature = skinExtensionLoaded && skinSigRaw instanceof String ss ? ss : null;
        if (name == null || worldName == null) continue;
        saved.add(
            new SavedBot(
                name,
                uuid,
                displayName,
                spawnedBy,
                spawnedByUuid,
                worldName,
                x,
                y,
                z,
                yaw,
                pitch,
                luckpermsGroup,
                botType,
                chatEnabled,
                respawnOnDeath,
                chatTier,
                aiPersonality,
                headAiEnabled,
                pickUpItems,
                pickUpXp,
                xpTotal,
                xpLevel,
                xpProgress,
                frozen,
                navParkour,
                navBreakBlocks,
                navPlaceBlocks,
                navAvoidWater,
                navAvoidLava,
                swimAiEnabled,
                pingExtensionLoaded ? ping : -1,
                rightClickCommand,
                pveEnabled,
                pveSmartAttackMode,
                pveRange,
                pvePriority,
                pveMobType,
                skinTexture,
                skinSignature,
                sharedControllers,
                autoEatEnabled,
                autoPlaceBedEnabled,
                autoMilkEnabled,
                preventBadOmen,
                pingExtensionLoaded && pingUserSet));
      } catch (Exception e) {
        FppLogger.warn("Skipping malformed bot entry in " + FILE_NAME + ": " + e.getMessage());
      }
    }
    clearUnifiedSection(ROOT_BOTS);
    deleteFile(dataFile);
    if (saved.isEmpty()) {
      manager.setRestorationInProgress(false);
      return;
    }

    FppLogger.info("Restoring " + saved.size() + " bot(s) from YAML fallback...");
    FppScheduler.runSyncLater(plugin, () -> restoreChain(manager, saved, 0), 40L);
  }

  private Map<String, SkinProfile> loadYamlSkinFallback() {
    Map<String, SkinProfile> fallback = new HashMap<>();
    try {
      YamlConfiguration unified = BotDataYaml.load(plugin);
      List<?> raw = unified.getList(ROOT_BOTS + ".bots");
      if ((raw == null || raw.isEmpty()) && dataFile.exists()) {
        raw = YamlConfiguration.loadConfiguration(dataFile).getList("bots");
      }
      if (raw == null) return fallback;
      for (Object obj : raw) {
        if (!(obj instanceof Map<?, ?> map)) continue;
        Object nameRaw = map.get("name");
        Object textureRaw = map.get("skin-texture");
        if (!(nameRaw instanceof String name) || !(textureRaw instanceof String texture) || texture.isBlank()) {
          continue;
        }
        Object signatureRaw = map.get("skin-signature");
        String signature = signatureRaw instanceof String sig ? sig : null;
        fallback.put(
            name.toLowerCase(Locale.ROOT),
            new SkinProfile(texture, signature, "yaml-fallback:" + name));
      }
    } catch (Exception e) {
      FppLogger.warn("BotPersistence: failed to read YAML skin fallback: " + e.getMessage());
    }
    return fallback;
  }

  private void restoreChain(FakePlayerManager manager, List<SavedBot> saved, int index) {
    if (index >= saved.size()) {

      manager.setRestorationInProgress(false);
      loadedInventories = null;
      loadedXp = null;
      loadedTasks = null;
      clearUnifiedSection(ROOT_TASKS);
      deleteFile(tasksFile);
      FppLogger.info("Bot restoration complete: " + saved.size() + " bot(s) restored.");
      return;
    }

    SavedBot sb = saved.get(index);

    World world = Bukkit.getWorld(sb.worldName);
    if (world == null) {
      FppLogger.warn(
          "Cannot restore bot '"
              + sb.name
              + "' - world '"
              + sb.worldName
              + "' not found. Skipping.");
      restoreChain(manager, saved, index + 1);
      return;
    }

    Location loc = new Location(world, sb.x, sb.y, sb.z, sb.yaw, sb.pitch);

    SkinProfile restoredSkin =
        sb.skinTexture != null && !sb.skinTexture.isBlank()
            ? new SkinProfile(sb.skinTexture, sb.skinSignature, "persisted:" + sb.name)
            : null;
    manager.spawnRestored(
        sb.name,
        sb.uuid,
        sb.displayName,
        sb.spawnedBy,
        sb.spawnedByUuid,
        loc,
        sb.botType,
        restoredSkin);

    FakePlayer fp = manager.getByName(sb.name);
    if (fp != null) {
      restoreExtensionMetadata(fp);

      if (restoredSkin != null && restoredSkin.isValid() && plugin.getDatabaseManager() != null) {
        plugin.getDatabaseManager().updateBotSkin(fp.getUuid().toString(), sb.skinTexture, sb.skinSignature);
      }
      if (sb.luckpermsGroup != null && !sb.luckpermsGroup.isBlank()) {
        fp.setLuckpermsGroup(sb.luckpermsGroup);
      }

      fp.setChatEnabled(sb.chatEnabled);
      fp.setRespawnOnDeath(sb.respawnOnDeath);
      fp.setHeadAiEnabled(sb.headAiEnabled);
      fp.setPickUpItemsEnabled(sb.pickUpItemsEnabled);
      fp.setPickUpXpEnabled(sb.pickUpXpEnabled);
      fp.setFrozen(sb.frozen);
      fp.setNavParkour(sb.navParkour);
      fp.setNavBreakBlocks(sb.navBreakBlocks);
      fp.setNavPlaceBlocks(sb.navPlaceBlocks);
      fp.setNavAvoidWater(sb.navAvoidWater);
      fp.setNavAvoidLava(sb.navAvoidLava);
      fp.setSwimAiEnabled(sb.swimAiEnabled);
      fp.setAutoEatEnabled(sb.autoEatEnabled);
      fp.setAutoPlaceBedEnabled(sb.autoPlaceBedEnabled);
      fp.setAutoMilkEnabled(sb.autoMilkEnabled);
      fp.setPreventBadOmen(sb.preventBadOmen);
      for (UUID shared : sb.sharedControllers) fp.addSharedController(shared);
      if (sb.pingUserSet && sb.ping >= 0) {
        fp.setUserPing(sb.ping);
        fp.setBasePing(-1);
      } else if (sb.ping >= 0 && Config.pingEnabled()) {
        fp.setUserPing(-1);
        fp.setBasePing(sb.ping);
        fp.setPing(sb.ping);
      } else if (Config.pingEnabled()) {
        fp.setUserPing(-1);
        int min = Config.pingMin();
        int max = Config.pingMax();
        int base = min + ThreadLocalRandom.current()
            .nextInt(Math.max(1, max - min + 1));
        fp.setBasePing(base);
        fp.setPing(base);
      } else {
        fp.setUserPing(-1);
      }
      fp.setPveSmartAttackMode(sb.pveSmartAttackMode);
      fp.setPveRange(sb.pveRange);
      if (sb.pvePriority != null) fp.setPvePriority(sb.pvePriority);
      if (sb.pveMobType != null) fp.setPveMobType(sb.pveMobType);
      if (sb.chatTier != null) fp.setChatTier(sb.chatTier);

      if (sb.aiPersonality != null) fp.setAiPersonality(sb.aiPersonality);

      if (sb.rightClickCommand != null && fp.getRightClickCommand() == null) {
        fp.setRightClickCommand(sb.rightClickCommand);
      }

      manager.persistBotSettings(fp);

      if (sb.ping >= 0) {
        final UUID restoredUuid = fp.getUuid();
        final int restoredPing = sb.ping;
        FppScheduler.runSyncLater(
            plugin,
            () -> {
              FakePlayer restored = manager.getByUuid(restoredUuid);
              if (restored != null) {
                manager.applyPing(restored, restoredPing);
              }
            },
            5L);
      } else if (Config.pingEnabled() && fp.getBasePing() >= 0) {
        final UUID restoredUuid = fp.getUuid();
        FppScheduler.runSyncLater(
            plugin,
            () -> {
              FakePlayer restored = manager.getByUuid(restoredUuid);
              if (restored != null) {
                manager.applyPing(restored, -1);
              }
            },
            5L);
      }

      if (fp.isPveEnabled()) {
        TaskEntry taskEntry = loadedTasks != null ? loadedTasks.get(sb.uuid.toString()) : null;
        boolean hasAttackTask = taskEntry != null && taskEntry.attackWorld() != null;
        if (!hasAttackTask) {
          final FakePlayer pveBot = fp;
          FppScheduler.runSyncLater(
              plugin,
              () -> {
                var attackCmd = plugin.getAttackCommand();
                if (attackCmd != null && pveBot.getPlayer() != null && pveBot.getPlayer().isOnline()) {
                  attackCmd.startMobModeFromSettings(pveBot);
                }
              },
              15L);
        }
      }
    }

    if (loadedTasks != null) {
      TaskEntry te = loadedTasks.get(sb.uuid.toString());
      if (te != null && fp != null && te.rightClickCommand() != null) {
        fp.setRightClickCommand(te.rightClickCommand());
      }
    }

    if (loadedInventories != null) {
      Map<String, String> invSlots = loadedInventories.get(sb.uuid.toString());
      if (invSlots != null && !invSlots.isEmpty()) {
        final UUID restoredUuid = sb.uuid;
        final String restoredName = sb.name;
        FppScheduler.runSyncLater(
            plugin,
            () -> {
              FakePlayer restored = manager.getByUuid(restoredUuid);
              if (restored == null) return;
              Player bot = restored.getPlayer();
              if (bot == null || !bot.isValid()) return;
              applyInventory(bot.getInventory(), invSlots);
              Config.debug("Restored inventory for bot '" + restoredName + "'.");
            },
            10L);
      }
    }

    XpEntry xpEntry = loadedXp != null ? loadedXp.get(sb.uuid.toString()) : null;
    if (xpEntry == null && (sb.xpTotal > 0 || sb.xpLevel > 0 || sb.xpProgress > 0f)) {
      xpEntry = new XpEntry(sb.xpTotal, sb.xpLevel, sb.xpProgress);
    }
    final XpEntry xpToRestore = xpEntry;
    if (xpToRestore != null) {
      final UUID restoredUuid = sb.uuid;
      final String restoredName = sb.name;
      FppScheduler.runSyncLater(
          plugin,
          () -> {
            FakePlayer restored = manager.getByUuid(restoredUuid);
            if (restored == null) return;
            Player bot = restored.getPlayer();
            if (bot == null || !bot.isValid()) return;
            bot.setTotalExperience(0);
            bot.setLevel(0);
            bot.setExp(0f);
            bot.setLevel(xpToRestore.level());
            bot.setExp(xpToRestore.progress());
            bot.setTotalExperience(xpToRestore.totalExperience());
            Config.debug("Restored XP for bot '" + restoredName + "'.");
          },
          12L);
    }

    if (loadedTasks != null) {
      TaskEntry te = loadedTasks.get(sb.uuid.toString());
      if (te != null
          && (te.mineWorld() != null
          || te.useWorld() != null
          || te.areaPos1World() != null
          || te.placeWorld() != null
          || te.roamWorld() != null)) {
        final TaskEntry task = te;
        final UUID restoredUuid = sb.uuid;
        FppScheduler.runSyncLater(
            plugin,
            () -> {
              FakePlayer restored = manager.getByUuid(restoredUuid);
              if (restored == null) return;
              Player bot = restored.getPlayer();
              if (bot == null || !bot.isOnline()) return;

              if (task.mineWorld() != null && mineCommand != null) {
                World w = Bukkit.getWorld(task.mineWorld());
                if (w != null && w.equals(bot.getWorld())) {
                  Location mineLoc =
                      new Location(
                          w,
                          task.mineX(),
                          task.mineY(),
                          task.mineZ(),
                          task.mineYaw(),
                          task.minePitch());
                  mineCommand.resumeMining(restored, task.mineOnce(), mineLoc);
                  Config.debug("Resumed mine task for bot '" + restored.getName() + "'.");
                }
              }

              if (task.areaActive()
                  && task.areaPos1World() != null
                  && task.areaPos2World() != null
                  && mineCommand != null) {
                World w1 = Bukkit.getWorld(task.areaPos1World());
                World w2 = Bukkit.getWorld(task.areaPos2World());
                if (w1 != null && w2 != null && w1.equals(w2) && w1.equals(bot.getWorld())) {
                  Location pos1 =
                      new Location(w1, task.areaPos1X(), task.areaPos1Y(), task.areaPos1Z());
                  Location pos2 =
                      new Location(w2, task.areaPos2X(), task.areaPos2Y(), task.areaPos2Z());
                  mineCommand.restoreAreaJob(restored, pos1, pos2);
                }
              }

              if (task.useWorld() != null && useCommand != null) {
                World w = Bukkit.getWorld(task.useWorld());
                if (w != null && w.equals(bot.getWorld())) {
                  Location useLoc =
                      new Location(
                          w,
                          task.useX(),
                          task.useY(),
                          task.useZ(),
                          task.useYaw(),
                          task.usePitch());
                  Object useTarget = null;
                  if (task.useX() != 0 || task.useY() != 0 || task.useZ() != 0) {
                    useTarget = bot.getWorld().getBlockAt((int) task.useX(), (int) task.useY(), (int) task.useZ());
                  }
                  if (useTarget != null) {
                    useCommand.resumeUsing(restored, task.useOnce(), useLoc, useTarget, UseCommand.UseMode.USE_ONLY);
                  }
                  Config.debug("Resumed use loop for bot '" + restored.getName() + "'.");
                }
              }

              if (task.placeWorld() != null && placeCommand != null) {
                World w = Bukkit.getWorld(task.placeWorld());
                if (w != null && w.equals(bot.getWorld())) {
                  Location placeLoc =
                      new Location(
                          w,
                          task.placeX(),
                          task.placeY(),
                          task.placeZ(),
                          task.placeYaw(),
                          task.placePitch());
                  placeCommand.resumePlacing(restored, task.placeOnce(), placeLoc);
                  Config.debug("Resumed place task for bot '" + restored.getName() + "'.");
                }
              }

              if (task.attackWorld() != null && attackCommand != null) {
                World w = Bukkit.getWorld(task.attackWorld());
                if (w != null && w.equals(bot.getWorld())) {
                  Location attackLoc =
                      new Location(
                          w,
                          task.attackX(),
                          task.attackY(),
                          task.attackZ(),
                          task.attackYaw(),
                          task.attackPitch());
                  String mode = task.attackMode();
                  if ("hunt".equals(mode)) {
                    Set<EntityType> filterTypes = parseEntityTypes(task.attackMobTypes());
                    attackCommand.resumeHuntAttacking(
                        restored,
                        task.attackRange(),
                        task.attackPriority(),
                        filterTypes,
                        attackLoc);
                    Config.debug("Resumed hunt attack task for bot '" + restored.getName() + "'.");
                  } else if ("mob".equals(mode)) {
                    Set<EntityType> filterTypes = parseEntityTypes(task.attackMobTypes());
                    FakePlayer.PveSmartAttackMode smartMode =
                        FakePlayer.PveSmartAttackMode.OFF;
                    if (task.attackSmartMode() != null) {
                      try {
                        smartMode = FakePlayer.PveSmartAttackMode.valueOf(task.attackSmartMode());
                      } catch (IllegalArgumentException ignored) {
                      }
                    }
                    attackCommand.resumeMobAttacking(
                        restored,
                        task.attackRange(),
                        task.attackPriority(),
                        filterTypes,
                        smartMode,
                        attackLoc);
                    Config.debug("Resumed mob attack task for bot '" + restored.getName() + "'.");
                  } else {
                    attackCommand.resumeAttacking(restored, task.attackOnce(), attackLoc);
                    Config.debug("Resumed attack task for bot '" + restored.getName() + "'.");
                  }
                }
              }

              if (task.followTargetUuid() != null && followCommand != null) {
                try {
                  UUID targetUuid =
                      UUID.fromString(task.followTargetUuid());
                  followCommand.resumeFollowing(restored, targetUuid);
                  Config.debug("Resumed follow task for bot '" + restored.getName() + "'.");
                } catch (IllegalArgumentException ignored) {
                }
              }

              if (task.roamWorld() != null && moveCommand != null) {
                World w = Bukkit.getWorld(task.roamWorld());
                if (w != null && w.equals(bot.getWorld())) {
                  Location roamCenter =
                      new Location(w, task.roamX(), task.roamY(), task.roamZ());
                  double roamR = task.roamRadius();
                  if (roamR < 3) roamR = 20;
                  moveCommand.resumeRoaming(restored, roamCenter, roamR);
                  Config.debug(
                      "Resumed roaming for bot '"
                          + restored.getName()
                          + "' (radius "
                          + (int) roamR
                          + ").");
                }
              }
            },
            25L);
      }
    }

    FppScheduler.runSync(plugin, () -> restoreChain(manager, saved, index + 1));
  }

  private void restoreExtensionMetadata(FakePlayer fp) {
    if (fp == null || plugin.getDatabaseManager() == null) return;
    Map<String, Map<String, String>> data =
        plugin.getDatabaseManager().loadAllBotExtensionData(fp.getUuid().toString());
    for (Map<String, String> values : data.values()) {
      for (Map.Entry<String, String> entry : values.entrySet()) {
        fp.setMetadata(entry.getKey(), parseStoredMetadataValue(entry.getValue()));
      }
    }
  }

  private Object parseStoredMetadataValue(String raw) {
    if (raw == null) return null;
    if (raw.equalsIgnoreCase("true")) return true;
    if (raw.equalsIgnoreCase("false")) return false;
    try {
      if (!raw.contains(".") && !raw.contains("e") && !raw.contains("E")) return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
    }
    try {
      return Float.parseFloat(raw);
    } catch (NumberFormatException ignored) {
    }
    return raw;
  }

  private boolean isExtensionLoaded(String extensionName) {
    return plugin.getExtensionLoader() != null
        && plugin.getExtensionLoader().isExtensionLoaded(extensionName);
  }

  private void loadInventoryFile() {
    loadedInventories = new HashMap<>();
    YamlConfiguration unified = BotDataYaml.load(plugin);
    ConfigurationSection invSection = unified.getConfigurationSection(ROOT_INVENTORIES);
    if (invSection == null && inventoryFile.exists()) {
      YamlConfiguration legacy = YamlConfiguration.loadConfiguration(inventoryFile);
      invSection = legacy.getConfigurationSection("inventories");
      if (invSection != null) {
        final ConfigurationSection migrated = invSection;
        try {
          BotDataYaml.replaceSection(
              plugin,
              ROOT_INVENTORIES,
              section -> {
                for (String uuidKey : migrated.getKeys(false)) {
                  ConfigurationSection botSection = migrated.getConfigurationSection(uuidKey);
                  if (botSection == null) continue;
                  for (String slot : botSection.getKeys(false)) {
                    String val = botSection.getString(slot);
                    if (val != null && !val.isEmpty()) section.set(uuidKey + "." + slot, val);
                  }
                }
              });
          deleteFile(inventoryFile);
        } catch (IOException e) {
          FppLogger.warn("Failed to migrate " + INV_FILE_NAME + " to " + BotDataYaml.FILE_NAME + ": " + e.getMessage());
        }
      }
    }
    if (invSection == null) return;
    for (String uuidKey : invSection.getKeys(false)) {
      ConfigurationSection botSection = invSection.getConfigurationSection(uuidKey);
      if (botSection == null) continue;
      Map<String, String> slots = new LinkedHashMap<>();
      for (String slot : botSection.getKeys(false)) {
        String val = botSection.getString(slot);
        if (val != null && !val.isEmpty()) slots.put(slot, val);
      }
      if (!slots.isEmpty()) loadedInventories.put(uuidKey, slots);
    }
    Config.debug(
        "Loaded inventories for "
            + loadedInventories.size()
            + " bot(s) from "
            + BotDataYaml.FILE_NAME
            + ".");
  }

  private void loadXpFile() {
    loadedXp = new HashMap<>();
    YamlConfiguration unified = BotDataYaml.load(plugin);
    ConfigurationSection xpSection = unified.getConfigurationSection(ROOT_XP);
    if (xpSection == null && xpFile.exists()) {
      YamlConfiguration legacy = YamlConfiguration.loadConfiguration(xpFile);
      xpSection = legacy.getConfigurationSection("xp");
      if (xpSection != null) {
        final ConfigurationSection migrated = xpSection;
        try {
          BotDataYaml.replaceSection(
              plugin,
              ROOT_XP,
              section -> {
                for (String uuidKey : migrated.getKeys(false)) {
                  ConfigurationSection sec = migrated.getConfigurationSection(uuidKey);
                  if (sec == null) continue;
                  section.set(uuidKey + ".total", sec.getInt("total", 0));
                  section.set(uuidKey + ".level", sec.getInt("level", 0));
                  section.set(uuidKey + ".progress", sec.getDouble("progress", 0.0));
                }
              });
          deleteFile(xpFile);
        } catch (IOException e) {
          FppLogger.warn("Failed to migrate " + XP_FILE_NAME + " to " + BotDataYaml.FILE_NAME + ": " + e.getMessage());
        }
      }
    }
    if (xpSection == null) return;
    for (String uuidKey : xpSection.getKeys(false)) {
      ConfigurationSection sec = xpSection.getConfigurationSection(uuidKey);
      if (sec == null) continue;
      loadedXp.put(
          uuidKey,
          new XpEntry(
              sec.getInt("total", 0),
              sec.getInt("level", 0),
              (float) sec.getDouble("progress", 0.0)));
    }
    Config.debug("Loaded XP for " + loadedXp.size() + " bot(s) from " + BotDataYaml.FILE_NAME + ".");
  }

  private void loadTasksFile() {
    loadedTasks = new HashMap<>();

    DatabaseManager db = plugin.getDatabaseManager();
    if (db != null) {
      List<DatabaseManager.BotTaskRow> dbRows =
          db.loadBotTasksForThisServer();
      if (!dbRows.isEmpty()) {
        loadedTasks = buildTasksFromDbRows(dbRows);

        db.clearBotTasks();
        Config.debug("Loaded task state for " + loadedTasks.size() + " bot(s) from DB.");
        return;
      }
    }

    YamlConfiguration unified = BotDataYaml.load(plugin);
    ConfigurationSection tasksSection = unified.getConfigurationSection(ROOT_TASKS);
    if (tasksSection == null && tasksFile.exists()) {
      YamlConfiguration legacy = YamlConfiguration.loadConfiguration(tasksFile);
      tasksSection = legacy;
      if (!legacy.getKeys(false).isEmpty()) {
        final YamlConfiguration migrated = legacy;
        try {
          BotDataYaml.replaceSection(
              plugin,
              ROOT_TASKS,
              section -> {
                for (String uuidKey : migrated.getKeys(false)) {
                  ConfigurationSection src = migrated.getConfigurationSection(uuidKey);
                  if (src == null) continue;
                  for (String key : src.getKeys(false)) {
                    section.set(uuidKey + "." + key, src.get(key));
                  }
                }
              });
          deleteFile(tasksFile);
        } catch (IOException e) {
          FppLogger.warn("Failed to migrate " + TASKS_FILE_NAME + " to " + BotDataYaml.FILE_NAME + ": " + e.getMessage());
        }
      }
    }
    if (tasksSection == null) return;

    for (String uuidStr : tasksSection.getKeys(false)) {
      ConfigurationSection sec = tasksSection.getConfigurationSection(uuidStr);
      if (sec == null) continue;
      String rcc = sec.getString("right-click-command");
      String mineWorld = sec.getString("mine-world");
      double mineX = sec.getDouble("mine-x");
      double mineY = sec.getDouble("mine-y");
      double mineZ = sec.getDouble("mine-z");
      float mineYaw = (float) sec.getDouble("mine-yaw");
      float minePitch = (float) sec.getDouble("mine-pitch");
      boolean mineOnce = sec.getBoolean("mine-once", false);
      String useWorld = sec.getString("use-world");
      double useX = sec.getDouble("use-x");
      double useY = sec.getDouble("use-y");
      double useZ = sec.getDouble("use-z");
      float useYaw = (float) sec.getDouble("use-yaw");
      float usePitch = (float) sec.getDouble("use-pitch");
      boolean useOnce = sec.getBoolean("use-once", false);

      String placeWorld = sec.getString("place-world");
      double placeX = sec.getDouble("place-x");
      double placeY = sec.getDouble("place-y");
      double placeZ = sec.getDouble("place-z");
      float placeYaw = (float) sec.getDouble("place-yaw");
      float placePitch = (float) sec.getDouble("place-pitch");
      boolean placeOnce = sec.getBoolean("place-once", false);

      String areaPos1World = sec.getString("area-pos1-world");
      double areaPos1X = sec.getDouble("area-pos1-x");
      double areaPos1Y = sec.getDouble("area-pos1-y");
      double areaPos1Z = sec.getDouble("area-pos1-z");
      String areaPos2World = sec.getString("area-pos2-world");
      double areaPos2X = sec.getDouble("area-pos2-x");
      double areaPos2Y = sec.getDouble("area-pos2-y");
      double areaPos2Z = sec.getDouble("area-pos2-z");
      boolean areaActive = sec.getBoolean("area-active", false);

      String attackWorld = sec.getString("attack-world");
      double attackX = sec.getDouble("attack-x");
      double attackY = sec.getDouble("attack-y");
      double attackZ = sec.getDouble("attack-z");
      float attackYaw = (float) sec.getDouble("attack-yaw");
      float attackPitch = (float) sec.getDouble("attack-pitch");
      boolean attackOnce = sec.getBoolean("attack-once", false);
      String attackMode = sec.getString("attack-mode");
      double attackRange = sec.getDouble("attack-range", Config.attackMobDefaultRange());
      String attackPriority = sec.getString("attack-priority", Config.attackMobDefaultPriority());
      String attackMobTypes = sec.getString("attack-mob-types");
      String attackSmartMode = sec.getString("attack-smart-mode");

      String followTarget = sec.getString("follow-target");

      String roamWorld = sec.getString("roam-world");
      double roamX = sec.getDouble("roam-x");
      double roamY = sec.getDouble("roam-y");
      double roamZ = sec.getDouble("roam-z");
      double roamRadius = sec.getDouble("roam-radius");

      loadedTasks.put(
          uuidStr,
          new TaskEntry(
              rcc,
              mineWorld,
              mineX,
              mineY,
              mineZ,
              mineYaw,
              minePitch,
              mineOnce,
              useWorld,
              useX,
              useY,
              useZ,
              useYaw,
              usePitch,
              useOnce,
              areaPos1World,
              areaPos1X,
              areaPos1Y,
              areaPos1Z,
              areaPos2World,
              areaPos2X,
              areaPos2Y,
              areaPos2Z,
              areaActive,
              placeWorld,
              placeX,
              placeY,
              placeZ,
              placeYaw,
              placePitch,
              placeOnce,
              attackWorld,
              attackX,
              attackY,
              attackZ,
              attackYaw,
              attackPitch,
              attackOnce,
              attackMode,
              attackRange,
              attackPriority,
              attackMobTypes,
              attackSmartMode,
              followTarget,
              roamWorld,
              roamX,
              roamY,
              roamZ,
              roamRadius));
    }
    Config.debug(
        "Loaded task state for " + loadedTasks.size() + " bot(s) from " + BotDataYaml.FILE_NAME + ".");
  }

  private Map<String, TaskEntry> buildTasksFromDbRows(
      List<DatabaseManager.BotTaskRow> rows) {

    Map<String, Map<String, DatabaseManager.BotTaskRow>> byUuid =
        new LinkedHashMap<>();
    for (var row : rows) {
      byUuid.computeIfAbsent(row.botUuid(), k -> new LinkedHashMap<>()).put(row.taskType(), row);
    }
    Map<String, TaskEntry> result = new LinkedHashMap<>();
    for (var entry : byUuid.entrySet()) {
      String uuid = entry.getKey();
      var tasks = entry.getValue();
      var mine = tasks.get("MINE");
      var use = tasks.get("USE");
      var place = tasks.get("PLACE");
      var attack = tasks.get("ATTACK");
      var follow = tasks.get("FOLLOW");
      var roam = tasks.get("ROAM");
      result.put(
          uuid,
          new TaskEntry(
              null,
              mine != null ? mine.worldName() : null,
              mine != null ? mine.posX() : 0,
              mine != null ? mine.posY() : 0,
              mine != null ? mine.posZ() : 0,
              mine != null ? mine.posYaw() : 0f,
              mine != null ? mine.posPitch() : 0f,
              mine != null && mine.onceFlag(),
              use != null ? use.worldName() : null,
              use != null ? use.posX() : 0,
              use != null ? use.posY() : 0,
              use != null ? use.posZ() : 0,
              use != null ? use.posYaw() : 0f,
              use != null ? use.posPitch() : 0f,
              use != null && use.onceFlag(),
              null,
              0,
              0,
              0,
              null,
              0,
              0,
              0,
              false,
              place != null ? place.worldName() : null,
              place != null ? place.posX() : 0,
              place != null ? place.posY() : 0,
              place != null ? place.posZ() : 0,
              place != null ? place.posYaw() : 0f,
              place != null ? place.posPitch() : 0f,
              place != null && place.onceFlag(),
              attack != null ? attack.worldName() : null,
              attack != null ? attack.posX() : 0,
              attack != null ? attack.posY() : 0,
              attack != null ? attack.posZ() : 0,
              attack != null ? attack.posYaw() : 0f,
              attack != null ? attack.posPitch() : 0f,
              attack != null && attack.onceFlag(),
              attack != null && attack.extraStr() != null ? parseAttackMode(attack.extraStr()) : null,
              attack != null && attack.extraStr() != null ? parseAttackRange(attack.extraStr()) : Config.attackMobDefaultRange(),
              attack != null && attack.extraStr() != null ? parseAttackPriority(attack.extraStr()) : Config.attackMobDefaultPriority(),
              attack != null && attack.extraStr() != null ? parseAttackMobTypes(attack.extraStr()) : null,
              attack != null && attack.extraStr() != null ? parseAttackSmartMode(attack.extraStr()) : null,
              follow != null ? follow.extraStr() : null,
              roam != null ? roam.worldName() : null,
              roam != null ? roam.posX() : 0,
              roam != null ? roam.posY() : 0,
              roam != null ? roam.posZ() : 0,
              roam != null ? roam.posYaw() : 0));
    }
    return result;
  }

  private static void applyInventory(PlayerInventory inv, Map<String, String> slots) {
    for (Map.Entry<String, String> entry : slots.entrySet()) {
      try {
        int slot = Integer.parseInt(entry.getKey());
        ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(entry.getValue()));
        if (slot <= 35) inv.setItem(slot, item);
        else if (slot == 36) inv.setBoots(item);
        else if (slot == 37) inv.setLeggings(item);
        else if (slot == 38) inv.setChestplate(item);
        else if (slot == 39) inv.setHelmet(item);
        else if (slot == 40) inv.setItemInOffHand(item);
      } catch (Exception e) {
        FppLogger.warn("Failed to restore item in slot " + entry.getKey() + ": " + e.getMessage());
      }
    }
  }

  public void purgeOrphanedBodiesAndRestore(FakePlayerManager manager) {

    FppScheduler.runSyncLater(
        plugin,
        () -> {
          purgeOrphanedBodies();

          FppScheduler.runSyncLater(plugin, () -> restore(manager), 5L);
        },
        40L);
  }

  private void purgeOrphanedBodies() {
    NamespacedKey key = FakePlayerManager.FAKE_PLAYER_KEY;
    if (key == null) return;

    int removed = 0;
    for (World world : Bukkit.getWorlds()) {
      for (Entity entity : world.getEntities()) {
        if (!entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) continue;
        String val = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (val != null) {
          entity.remove();
          removed++;
          Config.debug("Purged orphaned entity: " + val);
        }
      }
    }
    if (removed > 0) {
      FppLogger.info("Purged " + removed + " orphaned bot entity/entities from previous session.");
    }
  }

  private static double toDouble(Object o) {
    if (o instanceof Number n) return n.doubleValue();
    return 0.0;
  }

  private void clearUnifiedSection(String path) {
    try {
      YamlConfiguration yaml =
          unifiedFile.exists() ? YamlConfiguration.loadConfiguration(unifiedFile) : new YamlConfiguration();
      yaml.set(path, null);
      yaml.save(unifiedFile);
    } catch (IOException e) {
      FppLogger.warn(
          "BotPersistence: could not clear section '" + path + "' in " + BotDataYaml.FILE_NAME + ": "
              + e.getMessage());
    }
  }

  private static void deleteFile(File f) {
    if (f.exists() && !f.delete()) {
      FppLogger.warn("BotPersistence: could not delete " + f.getName());
    }
  }

  private record SavedBot(
      String name,
      UUID uuid,
      String displayName,
      String spawnedBy,
      UUID spawnedByUuid,
      String worldName,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      String luckpermsGroup,
      BotType botType,
      boolean chatEnabled,
      boolean respawnOnDeath,
      String chatTier,
      String aiPersonality,
      boolean headAiEnabled,
      boolean pickUpItemsEnabled,
      boolean pickUpXpEnabled,
      int xpTotal,
      int xpLevel,
      float xpProgress,
      boolean frozen,
      boolean navParkour,
      boolean navBreakBlocks,
      boolean navPlaceBlocks,
      boolean navAvoidWater,
      boolean navAvoidLava,
      boolean swimAiEnabled,
      int ping,
      String rightClickCommand,
      boolean pveEnabled,
      String pveSmartAttackMode,
      double pveRange,
      String pvePriority,
      String pveMobType,
      String skinTexture,
      String skinSignature,
      Set<UUID> sharedControllers,
      boolean autoEatEnabled,
      boolean autoPlaceBedEnabled,
      boolean autoMilkEnabled,
      boolean preventBadOmen,
      boolean pingUserSet) {
  }

  private UUID resolveRestoredUuid(String botName, UUID storedUuid) {
    BotIdentityCache identityCache = plugin.getBotIdentityCache();
    UUID resolved =
        identityCache != null && botName != null && !botName.isBlank()
            ? identityCache.lookupOrCreate(botName)
            : storedUuid;
    if (resolved != null && storedUuid != null && !resolved.equals(storedUuid)) {
      remapLoadedState(storedUuid, resolved);
      Config.debugDatabase(
          "BotPersistence: remapped restored UUID for '"
              + botName
              + "' "
              + storedUuid
              + " -> "
              + resolved);
    }
    return resolved;
  }

  private void remapLoadedState(UUID oldUuid, UUID newUuid) {
    if (oldUuid == null || newUuid == null || oldUuid.equals(newUuid)) return;
    String oldKey = oldUuid.toString();
    String newKey = newUuid.toString();
    remapLoadedMap(loadedInventories, oldKey, newKey);
    remapLoadedMap(loadedXp, oldKey, newKey);
    remapLoadedMap(loadedTasks, oldKey, newKey);
  }

  private static <T> void remapLoadedMap(Map<String, T> map, String oldKey, String newKey) {
    if (map == null || oldKey.equals(newKey)) return;
    T value = map.remove(oldKey);
    if (value != null && !map.containsKey(newKey)) {
      map.put(newKey, value);
    }
  }

  private static UUID parseUuidOrNull(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static Set<EntityType> parseEntityTypes(String mobTypes) {
    Set<EntityType> result = new HashSet<>();
    if (mobTypes == null || mobTypes.isEmpty()) return result;
    for (String name : mobTypes.split(",")) {
      try {
        result.add(EntityType.valueOf(name));
      } catch (IllegalArgumentException ignored) {
      }
    }
    return result;
  }

  private static String parseAttackMode(String extraStr) {
    if (extraStr == null || extraStr.isEmpty()) return null;
    String[] parts = extraStr.split(":", 5);
    return parts.length > 0 && !parts[0].isEmpty() ? parts[0] : null;
  }

  private static double parseAttackRange(String extraStr) {
    if (extraStr == null || extraStr.isEmpty()) return Config.attackMobDefaultRange();
    String[] parts = extraStr.split(":", 5);
    if (parts.length > 1) {
      try {
        return Double.parseDouble(parts[1]);
      } catch (NumberFormatException ignored) {
      }
    }
    return Config.attackMobDefaultRange();
  }

  private static String parseAttackPriority(String extraStr) {
    if (extraStr == null || extraStr.isEmpty()) return Config.attackMobDefaultPriority();
    String[] parts = extraStr.split(":", 5);
    if (parts.length > 2 && !parts[2].isEmpty()) return parts[2];
    return Config.attackMobDefaultPriority();
  }

  private static String parseAttackMobTypes(String extraStr) {
    if (extraStr == null || extraStr.isEmpty()) return null;
    String[] parts = extraStr.split(":", 5);
    if (parts.length > 4 && !parts[4].isEmpty()) return parts[4];
    return null;
  }

  private static String parseAttackSmartMode(String extraStr) {
    if (extraStr == null || extraStr.isEmpty()) return null;
    String[] parts = extraStr.split(":", 5);
    if (parts.length > 3 && !parts[3].isEmpty()) return parts[3];
    return null;
  }

  private record TaskEntry(
      String rightClickCommand,
      String mineWorld,
      double mineX,
      double mineY,
      double mineZ,
      float mineYaw,
      float minePitch,
      boolean mineOnce,
      String useWorld,
      double useX,
      double useY,
      double useZ,
      float useYaw,
      float usePitch,
      boolean useOnce,
      String areaPos1World,
      double areaPos1X,
      double areaPos1Y,
      double areaPos1Z,
      String areaPos2World,
      double areaPos2X,
      double areaPos2Y,
      double areaPos2Z,
      boolean areaActive,
      String placeWorld,
      double placeX,
      double placeY,
      double placeZ,
      float placeYaw,
      float placePitch,
      boolean placeOnce,
      String attackWorld,
      double attackX,
      double attackY,
      double attackZ,
      float attackYaw,
      float attackPitch,
      boolean attackOnce,
      String attackMode,
      double attackRange,
      String attackPriority,
      String attackMobTypes,
      String attackSmartMode,
      String followTargetUuid,
      String roamWorld,
      double roamX,
      double roamY,
      double roamZ,
      double roamRadius) {
  }

  private record XpEntry(int totalExperience, int level, float progress) {
  }
}
