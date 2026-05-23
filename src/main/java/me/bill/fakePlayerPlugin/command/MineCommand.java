package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBotBlockBreakEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.fakeplayer.StorageInteractionHelper;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.WorldEditHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MineCommand implements FppCommand {

  private static final boolean AREA_MODE_ENABLED = false;

  private static final int LOOK_BLOCK_RANGE = 6;
  private static final double MINE_REACH = 5.0;
  private static final int AREA_CONTROLLER_PERIOD = 5;
  private static final int AREA_PICKUP_WAIT_TICKS = 8;
  private static final int AREA_PICKUP_EXTRA_TICKS = 20;

  private static final long DROP_LOITER_MS = 8_000L;
  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;
  private final StorageStore storageStore;
  private FindCommand findCommand;

  private final Map<UUID, Integer> miningTasks = new ConcurrentHashMap<>();
  private final Map<UUID, MiningState> miningStates = new ConcurrentHashMap<>();

  private final Map<UUID, AreaSelection> selections = new ConcurrentHashMap<>();
  private final Map<UUID, AreaMineJob> areaJobs = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> areaTasks = new ConcurrentHashMap<>();

  public MineCommand(
      FakePlayerPlugin plugin,
      FakePlayerManager manager,
      StorageStore storageStore,
      PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.pathfinding = pathfinding;
    this.storageStore = storageStore;
  }

  public void setFindCommand(FindCommand findCommand) {
    this.findCommand = findCommand;
  }

  @Override
  public String getName() {
    return "mine";
  }

  @Override
  public String getUsage() {
    return "<bot> [--once|--stop|--pos1|--pos2|--start|--wesel]  |  --stop";
  }

  @Override
  public String getDescription() {
    return "Bot mines the block it is looking at. If too far, bot walks closer then mines.";
  }

  @Override
  public String getPermission() {
    return Perm.MINE;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.MINE);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("mine-usage"));
      return true;
    }

    if ((args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("--stop"))
        && args.length == 1) {
      stopAll();
      sender.sendMessage(Lang.get("mine-stopped-all"));
      return true;
    }

    String botName = args[0];
    FakePlayer fp = manager.getByName(botName);
    if (fp == null) {
      sender.sendMessage(Lang.get("mine-not-found", "name", botName));
      return true;
    }

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("mine-bot-offline", "name", fp.getDisplayName()));
      return true;
    }

    if (args.length >= 2) {
      String action = args[1].toLowerCase(Locale.ROOT);

      if (action.equals("--find") || action.equals("find")) {
        if (findCommand == null || args.length < 3) {
          sender.sendMessage(Lang.get("find-usage"));
          return true;
        }
        boolean started =
            findCommand.startFindTask(sender, fp, Arrays.copyOfRange(args, 2, args.length));
        if (started) {
          sender.sendMessage(
              Lang.get(
                  "find-started",
                  "name", fp.getDisplayName(),
                  "block", args[2].toLowerCase(Locale.ROOT).replace('_', ' '),
                  "radius", String.valueOf(32),
                  "count", args.length >= 4 && !args[3].startsWith("-") ? args[3] : "∞"));
        }
        return true;
      }

      if (!AREA_MODE_ENABLED) {
        Set<String> areaActions =
            Set.of(
                "--pos1",
                "pos1",
                "--pos2",
                "pos2",
                "start",
                "--status",
                "status",
                "--clear",
                "clear");
        if (areaActions.contains(action)) {
          sender.sendMessage(Lang.get("mine-usage"));
          return true;
        }
      }

      switch (action) {
        case "stop", "--stop" -> {
          cleanupBot(fp.getUuid());
          sender.sendMessage(Lang.get("mine-stopped", "name", fp.getDisplayName()));
          return true;
        }
        case "--pos1", "pos1" -> {
          Location posLoc;
          if (args.length >= 5) {
            Location senderLoc = (sender instanceof Player pl) ? pl.getLocation() : bot.getLocation();
            try {
              int px = (int) Math.floor(parseCoord(args[2], senderLoc.getX()));
              int py = (int) Math.floor(parseCoord(args[3], senderLoc.getY()));
              int pz = (int) Math.floor(parseCoord(args[4], senderLoc.getZ()));
              posLoc = new Location(bot.getWorld(), px, py, pz);
            } catch (NumberFormatException e) {
              sender.sendMessage(Lang.get("mine-coords-invalid"));
              return true;
            }
          } else {
            if (!(sender instanceof Player player)) {
              sender.sendMessage(Lang.get("player-only"));
              return true;
            }
            Block target = player.getTargetBlockExact(LOOK_BLOCK_RANGE);
            if (target == null) {
              sender.sendMessage(Lang.get("mine-look-at-block"));
              return true;
            }
            posLoc = target.getLocation();
          }
          AreaSelection selection =
              selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          selection.pos1 = posLoc;
          sender.sendMessage(
              Lang.get(
                  "mine-pos1-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          return true;
        }
        case "--pos2", "pos2" -> {
          Location posLoc;
          if (args.length >= 5) {
            Location senderLoc = (sender instanceof Player pl) ? pl.getLocation() : bot.getLocation();
            try {
              int px = (int) Math.floor(parseCoord(args[2], senderLoc.getX()));
              int py = (int) Math.floor(parseCoord(args[3], senderLoc.getY()));
              int pz = (int) Math.floor(parseCoord(args[4], senderLoc.getZ()));
              posLoc = new Location(bot.getWorld(), px, py, pz);
            } catch (NumberFormatException e) {
              sender.sendMessage(Lang.get("mine-coords-invalid"));
              return true;
            }
          } else {
            if (!(sender instanceof Player player)) {
              sender.sendMessage(Lang.get("player-only"));
              return true;
            }
            Block target = player.getTargetBlockExact(LOOK_BLOCK_RANGE);
            if (target == null) {
              sender.sendMessage(Lang.get("mine-look-at-block"));
              return true;
            }
            posLoc = target.getLocation();
          }
          AreaSelection selection =
              selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          selection.pos2 = posLoc;
          sender.sendMessage(
              Lang.get(
                  "mine-pos2-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          return true;
        }
        case "start" -> {
          startAreaMining(sender, fp);
          return true;
        }
        case "--wesel" -> {
          if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
          }
          if (!plugin.isWorldEditAvailable()) {
            sender.sendMessage(Lang.get("worldedit-not-available"));
            return true;
          }
          Location[] corners = WorldEditHelper.getSelection(player);
          if (corners == null) {
            sender.sendMessage(Lang.get("worldedit-no-selection"));
            return true;
          }
          AreaSelection weSelection = new AreaSelection();
          weSelection.pos1 = corners[0];
          weSelection.pos2 = corners[1];
          selections.put(fp.getUuid(), weSelection);
          startAreaMining(sender, fp);
          sender.sendMessage(Lang.get("mine-wesel-applied", "name", fp.getDisplayName()));
          return true;
        }
      }
    }

    boolean once =
        args.length >= 2
            && (args[1].equalsIgnoreCase("once") || args[1].equalsIgnoreCase("--once"));
    stopAreaJob(fp.getUuid(), false);
    cancelAll(fp.getUuid());

    BlockPos targetPos = null;
    if (sender instanceof Player player) {
      Block playerTarget = player.getTargetBlockExact(LOOK_BLOCK_RANGE);
      if (playerTarget != null) {
        targetPos = new BlockPos(playerTarget.getX(), playerTarget.getY(), playerTarget.getZ());
      }
    }
    if (targetPos == null) {
      targetPos = getTargetBlockFromBot(bot);
    }
    if (targetPos == null) {
      sender.sendMessage(Lang.get("mine-look-at-block"));
      return true;
    }

    final BlockPos finalTargetPos = targetPos;
    double dist = bot.getLocation().distance(new Location(bot.getWorld(), targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
    if (dist <= MINE_REACH) {
      lockAndStartMining(fp, once, targetPos);
      sender.sendMessage(
          once
              ? Lang.get("mine-started-once", "name", fp.getDisplayName())
              : Lang.get("mine-started", "name", fp.getDisplayName()));
    } else {
      Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetPos);
      if (standLoc != null) {
        startNavigation(fp, standLoc, () -> lockAndStartMining(fp, once, finalTargetPos));
        sender.sendMessage(Lang.get("mine-walking", "name", fp.getDisplayName()));
      } else {
        sender.sendMessage(Lang.get("mine-no-path", "name", fp.getDisplayName()));
      }
    }
    return true;
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (!canUse(sender)) return List.of();

    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      if ("--stop".startsWith(prefix)) out.add("--stop");
      if ("stop".startsWith(prefix)) out.add("stop");
      for (FakePlayer fp : manager.getActivePlayers()) {
        if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
      }
      return out;
    }

    if (args.length == 2
        && !args[0].equalsIgnoreCase("stop")
        && !args[0].equalsIgnoreCase("--stop")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      List<String> options =
          AREA_MODE_ENABLED
              ? List.of(
              "--once",
              "--stop",
              "once",
              "stop",
              "--pos1",
              "--pos2",
              "--start",
              "--wesel",
              "--status",
              "--clear")
              : List.of("--once", "--stop", "--wesel", "once", "stop");
      for (String option : options) {
        if (option.startsWith(prefix)) out.add(option);
      }
      return out;
    }

    return List.of();
  }

  private void startAreaMining(CommandSender sender, FakePlayer fp) {
    AreaSelection selection = selections.get(fp.getUuid());
    if (selection == null || !selection.isComplete()) {
      sender.sendMessage(Lang.get("mine-area-missing-selection", "name", fp.getDisplayName()));
      return;
    }
    if (!selection.sameWorld()) {
      sender.sendMessage(Lang.get("mine-area-world-mismatch"));
      return;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("mine-bot-offline", "name", fp.getDisplayName()));
      return;
    }
    if (bot.getWorld() != Objects.requireNonNull(selection.pos1.getWorld())) {
      sender.sendMessage(
          Lang.get(
              "mine-area-bot-world",
              "name",
              fp.getDisplayName(),
              "world",
              selection.pos1.getWorld().getName()));
      return;
    }

    cleanupBot(fp.getUuid());
    AreaMineJob job = new AreaMineJob(selection.copy(), sender);
    areaJobs.put(fp.getUuid(), job);
    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin, () -> tickAreaJob(fp.getUuid()), 0L, AREA_CONTROLLER_PERIOD);
    areaTasks.put(fp.getUuid(), taskId);

    sender.sendMessage(
        Lang.get(
            "mine-area-started",
            "name",
            fp.getDisplayName(),
            "count",
            String.valueOf(selection.blockCount())));
  }

  private void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
    BotPathfinder.PathOptions baseOpts =
        PathfindingService.resolvePathOptions(fp);
    BotPathfinder.PathOptions opts =
        new BotPathfinder.PathOptions(
            fp.isNavParkour(),
            true,
            fp.isNavPlaceBlocks(),
            baseOpts.avoidWater(),
            baseOpts.avoidLava());
    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.MINE,
            () -> dest,
            Config.pathfindingArrivalDistance(),
            0.0,
            Integer.MAX_VALUE,
            onArrive,
            null,
            null,
            null,
            opts));
  }

  private void lockAndStartMining(FakePlayer fp, boolean once, BlockPos targetPos) {
    FppApiImpl.fireTaskEvent(fp, "mine", FppBotTaskEvent.Action.START);
    UUID uuid = fp.getUuid();
    Player bot = fp.getPlayer();
    if (bot == null) return;

    Location faceLoc = faceTowardBlock(bot.getLocation(), targetPos);
    bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
    NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
    NmsPlayerSpawner.setMovementForward(bot, 0f);
    bot.setSprinting(false);

    Location actualLoc = bot.getLocation().clone();
    actualLoc.setYaw(faceLoc.getYaw());
    actualLoc.setPitch(faceLoc.getPitch());
    manager.lockForAction(uuid, actualLoc, false);

    MiningState state = new MiningState();
    state.once = once;
    state.forcedTarget = null;
    miningStates.put(uuid, state);

    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            () -> {
              Player b = fp.getPlayer();
              if (b == null || !b.isOnline()) {
                stopMining(uuid);
                return;
              }
              tickMining(fp, state);
            },
            0L,
            1L);

    miningTasks.put(uuid, taskId);
  }

  private void cancelAll(UUID botUuid) {
    pathfinding.cancel(botUuid);
    stopMining(botUuid);

    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      Player bot = fp.getPlayer();
      if (bot != null && bot.isOnline()) {
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        NmsPlayerSpawner.setJumping(bot, false);
        bot.setSprinting(false);
      }
    }
  }

  public void stopMining(UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      FppApiImpl.fireTaskEvent(fp, "mine", FppBotTaskEvent.Action.STOP);
    }
    Integer taskId = miningTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);

    manager.unlockAction(botUuid);
    manager.unlockNavigation(botUuid);

    MiningState state = miningStates.remove(botUuid);
    if (state != null) {
      if (state.currentPos != null) {
        if (fp != null) {
          Player bot = fp.getPlayer();
          if (bot != null && bot.isOnline()) {
            ServerPlayer nms = ((CraftPlayer) bot).getHandle();
            NmsPlayerSpawner.destroyBlockProgress(nms, -1, state.currentPos, -1);
            if (fireBlockBreakHook(fp, state.currentPos)) {
              NmsPlayerSpawner.handleBlockBreakAction(nms,
                  state.currentPos,
                  ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                  Direction.DOWN,
                  nms.level().getMaxY(),
                  -1);
            }
          }
        }
      }
    }
  }

  public void stopAll() {
    pathfinding.cancelAll(PathfindingService.Owner.MINE);
    new HashSet<>(miningTasks.keySet()).forEach(this::cleanupBot);
    new HashSet<>(areaTasks.keySet()).forEach(this::cleanupBot);
  }

  public void cleanupBot(UUID botUuid) {
    cancelAll(botUuid);
    stopAreaJob(botUuid, false);
  }

  public void clearSelection(UUID botUuid) {
    selections.remove(botUuid);
  }

  public boolean isNavigating(UUID botUuid) {
    return pathfinding.isNavigating(botUuid);
  }

  public boolean isMining(UUID botUuid) {
    return miningTasks.containsKey(botUuid);
  }

  @Nullable
  public BlockPos getActiveMineTarget(UUID botUuid) {
    MiningState state = miningStates.get(botUuid);
    return state != null ? state.forcedTarget : null;
  }

  public boolean isActiveMineOnce(UUID botUuid) {
    MiningState state = miningStates.get(botUuid);
    return state != null && state.once;
  }

  @Nullable
  public Location getSelectionPos1(UUID botUuid) {
    AreaSelection s = selections.get(botUuid);
    return s != null ? s.pos1 : null;
  }

  @Nullable
  public Location getSelectionPos2(UUID botUuid) {
    AreaSelection s = selections.get(botUuid);
    return s != null ? s.pos2 : null;
  }

  public boolean hasActiveAreaJob(UUID botUuid) {
    return areaJobs.containsKey(botUuid);
  }

  public void restoreAreaJob(FakePlayer fp, Location pos1, Location pos2) {
    if (!AREA_MODE_ENABLED) return;
    if (fp == null || pos1 == null || pos2 == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    if (pos1.getWorld() == null || pos2.getWorld() == null) return;
    if (!pos1.getWorld().equals(pos2.getWorld())) return;
    if (!pos1.getWorld().equals(bot.getWorld())) return;

    AreaSelection sel = new AreaSelection();
    sel.pos1 = pos1.clone();
    sel.pos2 = pos2.clone();
    selections.put(fp.getUuid(), sel);

    cleanupBot(fp.getUuid());
    AreaMineJob job = new AreaMineJob(sel.copy(), Bukkit.getConsoleSender());
    areaJobs.put(fp.getUuid(), job);
    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin, () -> tickAreaJob(fp.getUuid()), 0L, AREA_CONTROLLER_PERIOD);
    areaTasks.put(fp.getUuid(), taskId);
    Config.debug(
        "Restored area mining for bot '" + fp.getName() + "' (" + sel.blockCount() + " blocks).");
  }

  public void resumeMining(FakePlayer fp) {
    UUID uuid = fp.getUuid();
    MiningState state = miningStates.get(uuid);
    if (state != null && state.forcedTarget != null) {
      resumeMining(fp, state.once, state.forcedTarget);
    }
  }

  public void resumeMining(FakePlayer fp, boolean once, BlockPos targetPos) {
    if (fp == null || targetPos == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    cancelAll(fp.getUuid());
    double dist = bot.getLocation().distance(new Location(bot.getWorld(), targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
    if (dist <= MINE_REACH) {
      lockAndStartMining(fp, once, targetPos);
    } else {
      Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetPos);
      if (standLoc != null) {
        startNavigation(fp, standLoc, () -> lockAndStartMining(fp, once, targetPos));
      }
    }
  }

  public void resumeMining(FakePlayer fp, boolean once, Location targetLoc) {
    if (fp == null || targetLoc == null) return;
    BlockPos targetPos = new BlockPos(targetLoc.getBlockX(), targetLoc.getBlockY(), targetLoc.getBlockZ());
    resumeMining(fp, once, targetPos);
  }

  private void tickAreaJob(UUID botUuid) {
    AreaMineJob job = areaJobs.get(botUuid);
    if (job == null) {
      stopAreaJob(botUuid, false);
      return;
    }
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp == null) {
      stopAreaJob(botUuid, false);
      return;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      stopAreaJob(botUuid, false);
      return;
    }

    if (miningTasks.containsKey(botUuid)) {
      return;
    }

    if (pathfinding.isNavigating(botUuid)) return;
    if (job.depositingToStorage) return;

    boolean hasStorage = !storageStore.getStorages(fp.getName()).isEmpty();
    if (hasStorage && isStorageOffloadNeeded(bot.getInventory())) {
      if (!startStorageOffload(fp, job)) {
        notifyStarter(job, "mine-storage-unavailable", "name", fp.getDisplayName());
        stopAreaJob(botUuid, false);
      }
      return;
    }

    if (job.finishingDeposit) {
      notifyStarter(
          job,
          "mine-area-finished",
          "name",
          fp.getDisplayName(),
          "count",
          String.valueOf(job.blocksMined));
      stopAreaJob(botUuid, false);
      return;
    }

    if (fp.isPickUpItemsEnabled()) {
      Location dropTarget = nearestAnticipatedDrop(bot, job);
      if (dropTarget != null && bot.getLocation().distanceSquared(dropTarget) > 1.5 * 1.5) {
        startNavigation(fp, dropTarget, () -> {
        });
        return;
      }
    } else if (!job.anticipatedDrops.isEmpty()) {
      job.anticipatedDrops.clear();
    }

    AreaBlock next = findNextAreaTarget(bot, job.selection, job);
    if (next == null) {
      if (!job.skipped.isEmpty() && job.skipRetries < 2) {
        job.skipped.clear();
        job.skipRetries++;
        return;
      }

      if (job.currentLayer > job.selection.minY()) {
        job.currentLayer--;
        job.skipped.clear();
        job.skipRetries = 0;
        return;
      }

      if (hasStorage && hasDepositableLoot(bot.getInventory())) {
        job.finishingDeposit = true;
        if (!startStorageOffload(fp, job)) {
          notifyStarter(job, "mine-storage-unavailable", "name", fp.getDisplayName());
          notifyStarter(
              job,
              "mine-area-finished",
              "name",
              fp.getDisplayName(),
              "count",
              String.valueOf(job.blocksMined));
          stopAreaJob(botUuid, false);
        }
        return;
      }
      notifyStarter(
          job,
          "mine-area-finished",
          "name",
          fp.getDisplayName(),
          "count",
          String.valueOf(job.blocksMined));
      stopAreaJob(botUuid, false);
      return;
    }
    job.skipRetries = 0;
    job.finishingDeposit = false;

    Location standLoc = findStandLocationNearTarget(bot.getWorld(), next.toBlockPos());
    if (standLoc == null) {
      job.skipped.add(next);
      return;
    }

    job.currentTarget = next;
    startNavigation(fp, standLoc, () -> lockAndStartMining(fp, false, next.toBlockPos()));
  }

  private boolean startStorageOffload(FakePlayer fp, AreaMineJob job) {
    List<StorageStore.StoragePoint> storages = storageStore.getStorages(fp.getName());
    if (storages.isEmpty()) return false;
    Player bot = fp.getPlayer();
    if (bot == null) return false;

    for (int attempt = 0; attempt < storages.size(); attempt++) {
      int idx = (job.preferredStorageIndex + attempt) % storages.size();
      StorageStore.StoragePoint point = storages.get(idx);
      if (point.location().getWorld() != bot.getWorld()) continue;
      Block block = point.location().getBlock();
      if (!(block.getState() instanceof InventoryHolder holder)) continue;
      if (!containerCanAcceptAny(bot.getInventory(), holder.getInventory())) continue;
      Location standLoc = findStandLocationNearTarget(bot.getWorld(), new BlockPos(block.getX(), block.getY(), block.getZ()));
      if (standLoc == null) continue;
      startNavigation(fp, standLoc, () -> depositToStorage(fp, job, idx, point));
      return true;
    }
    return false;
  }

  private void depositToStorage(
      FakePlayer fp, AreaMineJob job, int storageIndex, StorageStore.StoragePoint point) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    Block block = point.location().getBlock();
    if (!(block.getState() instanceof InventoryHolder)) {
      job.preferredStorageIndex =
          (storageIndex + 1) % Math.max(1, storageStore.getStorages(fp.getName()).size());
      return;
    }
    Location standLoc = findStandLocationNearTarget(bot.getWorld(), new BlockPos(block.getX(), block.getY(), block.getZ()));
    if (standLoc == null) {
      job.preferredStorageIndex =
          (storageIndex + 1) % Math.max(1, storageStore.getStorages(fp.getName()).size());
      return;
    }

    final int sizes = Math.max(1, storageStore.getStorages(fp.getName()).size());
    job.depositingToStorage = true;
    StorageInteractionHelper.interact(
        fp,
        standLoc,
        block,
        plugin,
        manager,
        (holder, liveBot) -> {
          int moved = moveBotInventoryToStorage(liveBot.getInventory(), holder.getInventory());
          job.preferredStorageIndex = moved > 0 ? storageIndex : (storageIndex + 1) % sizes;
        },
        () -> job.depositingToStorage = false);
  }

  private void tickMining(FakePlayer fp, MiningState state) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      stopMining(fp.getUuid());
      return;
    }

    ServerPlayer nms = ((CraftPlayer) bot).getHandle();
    if (state.freeze > 0) {
      state.freeze--;
      return;
    }

    if (state.waitingForDrops) {
      if (state.pickupWaitTicks > 0) {
        state.pickupWaitTicks--;
        return;
      }
      if (fp.isPickUpItemsEnabled()
          && state.pickupWaitExtraTicks > 0
          && hasNearbyDrops(bot, state.currentPos)) {
        state.pickupWaitExtraTicks--;
        return;
      }
      state.waitingForDrops = false;
      state.pickupWaitExtraTicks = 0;
      if (state.stopAfterForcedTarget) {
        stopMining(fp.getUuid());
      }
      return;
    }

    BlockPos targetPos = getTargetBlockFromBot(bot);
    if (targetPos == null) {
      if (state.currentPos != null) abortMining(fp, nms, state);
      if (state.stopAfterForcedTarget) stopMining(fp.getUuid());
      return;
    }

    BlockState blockState = nms.level().getBlockState(targetPos);
    if (blockState.isAir()) {
      if (state.currentPos != null && state.currentPos.equals(targetPos)) {
        state.currentPos = null;
        state.progress = 0;
      }
      if (state.stopAfterForcedTarget) stopMining(fp.getUuid());
      return;
    }

    if (nms.blockActionRestricted(nms.level(), targetPos, nms.gameMode.getGameModeForPlayer()))
      return;

    equipBestMiningTool(bot, targetPos);

    Direction side = Direction.DOWN;
    if (bot.getGameMode() == GameMode.CREATIVE) {
      if (fireBlockBreakHook(fp, targetPos)) {
        NmsPlayerSpawner.handleBlockBreakAction(nms,
            targetPos,
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            side,
            nms.level().getMaxY(),
            -1);
      }
      nms.swing(InteractionHand.MAIN_HAND);
      if (state.once || state.stopAfterForcedTarget) {
        beginPickupWait(fp, state, targetPos);
      } else {
        state.freeze = 5;
        state.currentPos = null;
      }
      return;
    }

    if (state.currentPos == null || !state.currentPos.equals(targetPos)) {
      if (state.currentPos != null) {
        if (fireBlockBreakHook(fp, state.currentPos)) {
          NmsPlayerSpawner.handleBlockBreakAction(nms,
              state.currentPos,
              ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
              Direction.DOWN,
              nms.level().getMaxY(),
              -1);
        }
      }

      if (fireBlockBreakHook(fp, targetPos)) {
        NmsPlayerSpawner.handleBlockBreakAction(nms,
            targetPos,
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            side,
            nms.level().getMaxY(),
            -1);
      }

      if (state.progress == 0) blockState.attack(nms.level(), targetPos, nms);

      float speed = blockState.getDestroyProgress(nms, nms.level(), targetPos);
      if (speed >= 1.0F) {
        nms.swing(InteractionHand.MAIN_HAND);
        if (state.once || state.stopAfterForcedTarget) {
          beginPickupWait(fp, state, targetPos);
        } else {
          state.currentPos = null;
          state.freeze = 5;
        }
        return;
      }
      state.currentPos = targetPos;
      state.progress = 0;
    } else {
      float speed = blockState.getDestroyProgress(nms, nms.level(), targetPos);
      state.progress += speed;
      if (state.progress >= 1.0F) {
        if (fireBlockBreakHook(fp, targetPos)) {
          NmsPlayerSpawner.handleBlockBreakAction(nms,
              targetPos,
              ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
              side,
              nms.level().getMaxY(),
              -1);
        }
        nms.swing(InteractionHand.MAIN_HAND);
        NmsPlayerSpawner.destroyBlockProgress(nms, -1, targetPos, -1);
        nms.gameMode.destroyBlock(targetPos);
        if (state.once || state.stopAfterForcedTarget) {
          beginPickupWait(fp, state, targetPos);
        } else {
          state.currentPos = null;
          state.progress = 0;
          state.freeze = 5;
        }
        return;
      }
      NmsPlayerSpawner.destroyBlockProgress(nms, -1, targetPos, (int) (state.progress * 10));
    }

    nms.swing(InteractionHand.MAIN_HAND);
    nms.resetLastActionTime();
  }

  private void abortMining(FakePlayer fp, ServerPlayer nms, MiningState state) {
    if (state.currentPos == null) return;
    NmsPlayerSpawner.destroyBlockProgress(nms, -1, state.currentPos, -1);
    if (fp != null && fireBlockBreakHook(fp, state.currentPos)) {
      NmsPlayerSpawner.handleBlockBreakAction(nms,
          state.currentPos,
          ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
          Direction.DOWN,
          nms.level().getMaxY(),
          -1);
    }
    state.currentPos = null;
    state.progress = 0;
  }

  private boolean fireBlockBreakHook(FakePlayer fp, BlockPos pos) {
    if (fp == null || pos == null) return false;
    Player bot = fp.getPlayer();
    if (bot == null || bot.getWorld() == null) return false;
    var event =
        new FppBotBlockBreakEvent(
            new FppBotImpl(fp), bot.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
    Bukkit.getPluginManager().callEvent(event);
    return !event.isCancelled();
  }

  private BlockPos getTargetBlockFromBot(Player bot) {
    try {
      Location eye = bot.getEyeLocation();
      BlockIterator iter =
          new BlockIterator(bot.getWorld(), eye.toVector(), eye.getDirection(), 0, (int) MINE_REACH);
      while (iter.hasNext()) {
        Block b = iter.next();
        if (!b.getType().isAir() && b.getType().isSolid()) {
          return new BlockPos(b.getX(), b.getY(), b.getZ());
        }
      }
    } catch (IllegalStateException ignored) {
    }
    return null;
  }

  private Location faceTowardBlock(Location botLoc, BlockPos target) {
    double tx = target.getX() + 0.5;
    double ty = target.getY() + 0.5;
    double tz = target.getZ() + 0.5;
    double dx = tx - botLoc.getX();
    double dy = ty - (botLoc.getY() + 1.62);
    double dz = tz - botLoc.getZ();
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    Location result = botLoc.clone();
    result.setYaw(yaw);
    result.setPitch(pitch);
    return result;
  }

  @Nullable
  private Location findStandLocationNearTarget(World world, BlockPos target) {
    int tx = target.getX(), ty = target.getY(), tz = target.getZ();
    for (int r = 1; r <= 4; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          if (Math.abs(dx) < r && Math.abs(dz) < r) continue;
          int cx = tx + dx, cz = tz + dz;
          for (int dy : new int[]{0, -1, 1}) {
            int cy = ty + dy;
            if (BotPathfinder.walkable(world, cx, cy, cz)) {
              Location loc = new Location(world, cx + 0.5, cy, cz + 0.5);
              double dist = loc.distance(new Location(world, tx + 0.5, ty + 0.5, tz + 0.5));
              if (dist <= MINE_REACH - 1.5) {
                return faceTowardBlock(loc, target);
              }
            }
          }
        }
      }
    }
    return null;
  }

  private AreaBlock findNextAreaTarget(Player bot, AreaSelection selection, AreaMineJob job) {
    World world = bot.getWorld();
    AreaBlock best = null;
    double bestDist = Double.MAX_VALUE;

    int y = job.currentLayer;
    if (y < selection.minY() || y > selection.maxY()) return null;

    for (int x = selection.minX(); x <= selection.maxX(); x++) {
      for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
        AreaBlock candidate = new AreaBlock(x, y, z);
        if (job.completed.contains(candidate) || job.skipped.contains(candidate)) continue;
        Block block = world.getBlockAt(x, y, z);
        if (!isMineable(block)) {
          job.completed.add(candidate);
          continue;
        }
        double dist = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(bot.getLocation());
        if (dist < bestDist) {
          bestDist = dist;
          best = candidate;
        }
      }
    }
    return best;
  }

  private boolean isMineable(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    if (type.isAir() || !type.isSolid()) return false;
    if (block.getState() instanceof InventoryHolder) return false;
    return switch (type) {
      case BEDROCK,
           BARRIER,
           END_PORTAL,
           END_PORTAL_FRAME,
           NETHER_PORTAL,
           COMMAND_BLOCK,
           CHAIN_COMMAND_BLOCK,
           REPEATING_COMMAND_BLOCK,
           STRUCTURE_BLOCK,
           JIGSAW,
           LIGHT,
           REINFORCED_DEEPSLATE -> false;
      default -> true;
    };
  }

  private boolean isStorageOffloadNeeded(PlayerInventory inv) {
    return inv.firstEmpty() == -1;
  }

  private boolean hasDepositableLoot(PlayerInventory inv) {
    int heldSlot = inv.getHeldItemSlot();
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (isDepositCandidate(slot, heldSlot, item)) return true;
    }
    return false;
  }

  private int moveBotInventoryToStorage(PlayerInventory source, Inventory target) {
    int moved = 0;
    int heldSlot = source.getHeldItemSlot();
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = source.getItem(slot);
      if (!isDepositCandidate(slot, heldSlot, item)) continue;
      ItemStack original = item.clone();
      Map<Integer, ItemStack> leftovers = target.addItem(original);
      if (leftovers.isEmpty()) {
        moved += original.getAmount();
        source.setItem(slot, null);
      } else {
        ItemStack left = leftovers.values().iterator().next();
        int movedNow = original.getAmount() - left.getAmount();
        if (movedNow > 0) {
          moved += movedNow;
          source.setItem(slot, left);
        }
      }
    }
    return moved;
  }

  private boolean containerCanAcceptAny(PlayerInventory source, Inventory target) {
    int heldSlot = source.getHeldItemSlot();
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = source.getItem(slot);
      if (!isDepositCandidate(slot, heldSlot, item)) continue;
      if (inventoryCanFit(target, item)) return true;
    }
    return false;
  }

  private boolean inventoryCanFit(Inventory inv, ItemStack item) {
    for (ItemStack content : inv.getContents()) {
      if (content == null || content.getType() == Material.AIR) return true;
      if (content.isSimilar(item) && content.getAmount() < content.getMaxStackSize()) return true;
    }
    return false;
  }

  private boolean isDepositCandidate(int slot, int heldSlot, ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return false;
    if (slot == heldSlot && isLikelyMiningTool(item)) return false;
    return !isLikelyMiningTool(item);
  }

  private boolean isLikelyMiningTool(ItemStack item) {
    Material type = item.getType();
    return type.name().endsWith("_PICKAXE")
        || type.name().endsWith("_AXE")
        || type.name().endsWith("_SHOVEL")
        || type.name().endsWith("_HOE")
        || type.name().endsWith("_SWORD")
        || type == Material.SHEARS;
  }

  @Nullable
  private Location nearestAnticipatedDrop(Player bot, AreaMineJob job) {
    long now = System.currentTimeMillis();
    job.anticipatedDrops.entrySet().removeIf(e -> e.getValue() < now);
    if (job.anticipatedDrops.isEmpty()) return null;

    Location botLoc = bot.getLocation();
    Location nearest = null;
    double nearestDistSq = Double.MAX_VALUE;

    for (Map.Entry<BlockPos, Long> entry : new ArrayList<>(job.anticipatedDrops.entrySet())) {
      BlockPos pos = entry.getKey();
      Location center =
          new Location(bot.getWorld(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

      Location itemTarget = nearestItemNearTrackedDrop(bot.getWorld(), center);
      if (itemTarget != null) {
        double distSq = botLoc.distanceSquared(itemTarget);
        if (distSq < nearestDistSq) {
          nearestDistSq = distSq;
          nearest = itemTarget;
        }
        continue;
      }

      double centerDistSq = botLoc.distanceSquared(center);
      if (centerDistSq <= 1.5 * 1.5) {
        job.anticipatedDrops.remove(pos);
        continue;
      }

      if (centerDistSq < nearestDistSq) {
        nearestDistSq = centerDistSq;
        nearest = center;
      }
    }
    return nearest;
  }

  @Nullable
  private Location nearestItemNearTrackedDrop(World world, Location center) {
    Location nearest = null;
    double nearestDistSq = Double.MAX_VALUE;
    for (Entity entity : world.getNearbyEntities(center, 4.0, 2.5, 4.0)) {
      if (!(entity instanceof Item item) || item.isDead() || item.getPickupDelay() > 0) continue;
      Location itemLoc = item.getLocation();
      double distSq = itemLoc.distanceSquared(center);
      if (distSq < nearestDistSq) {
        nearestDistSq = distSq;
        nearest = itemLoc.clone();
      }
    }
    return nearest;
  }

  private void beginPickupWait(FakePlayer fp, MiningState state, BlockPos targetPos) {
    state.currentPos = null;
    state.progress = 0;
    state.freeze = 0;
    state.forcedTarget = targetPos;
    state.completeForcedTargetOnStop = state.stopAfterForcedTarget;
    state.waitingForDrops = true;

    AreaMineJob activeJob = areaJobs.get(fp.getUuid());
    if (activeJob != null && targetPos != null && fp.isPickUpItemsEnabled()) {
      activeJob.anticipatedDrops.put(targetPos, System.currentTimeMillis() + DROP_LOITER_MS);
    }

    boolean hasStorage = !storageStore.getStorages(fp.getName()).isEmpty();
    if (!hasStorage) {
      state.pickupWaitTicks = 0;
      state.pickupWaitExtraTicks = 0;
    } else {
      state.pickupWaitTicks = fp.isPickUpItemsEnabled() ? AREA_PICKUP_WAIT_TICKS : 1;
      state.pickupWaitExtraTicks = fp.isPickUpItemsEnabled() ? AREA_PICKUP_EXTRA_TICKS : 0;
    }
  }

  private boolean hasNearbyDrops(Player bot, BlockPos targetPos) {
    if (targetPos == null) return false;
    Location center =
        new Location(
            bot.getWorld(), targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
    for (Entity entity : bot.getNearbyEntities(2.0, 1.5, 2.0)) {
      if (!(entity instanceof Item item) || item.isDead()) continue;
      if (item.getLocation().distanceSquared(center) <= 6.25) return true;
    }
    return false;
  }

  private void equipBestMiningTool(Player bot, BlockPos targetPos) {
  }

  private int toolScore(ItemStack item, ToolClass preferred) {
    if (item == null || item.getType() == Material.AIR) return Integer.MIN_VALUE;
    Material type = item.getType();
    ToolClass actual = classifyTool(type);
    if (actual == ToolClass.NONE) return Integer.MIN_VALUE;

    int score = toolTierScore(type);
    if (actual == preferred) score += 10_000;
    else if (preferred == ToolClass.SHEARS && type == Material.SHEARS) score += 10_000;
    else if (preferred == ToolClass.NONE) score += 100;
    else score += 1_000;

    if (type == Material.SHEARS && preferred != ToolClass.SHEARS) score -= 500;
    return score;
  }

  private ToolClass determineToolClass(Material blockType) {
    if (blockType == Material.COBWEB) return ToolClass.SWORD;
    if (blockType.name().contains("LEAVES")
        || blockType == Material.VINE
        || blockType == Material.GLOW_LICHEN
        || blockType.name().endsWith("_WOOL")) {
      return ToolClass.SHEARS;
    }
    if (Tag.MINEABLE_PICKAXE.isTagged(blockType)) return ToolClass.PICKAXE;
    if (Tag.MINEABLE_AXE.isTagged(blockType)) return ToolClass.AXE;
    if (Tag.MINEABLE_SHOVEL.isTagged(blockType)) return ToolClass.SHOVEL;
    if (Tag.MINEABLE_HOE.isTagged(blockType)) return ToolClass.HOE;
    return ToolClass.NONE;
  }

  private ToolClass classifyTool(Material toolType) {
    String name = toolType.name();
    if (toolType == Material.SHEARS) return ToolClass.SHEARS;
    if (name.endsWith("_PICKAXE")) return ToolClass.PICKAXE;
    if (name.endsWith("_AXE")) return ToolClass.AXE;
    if (name.endsWith("_SHOVEL")) return ToolClass.SHOVEL;
    if (name.endsWith("_HOE")) return ToolClass.HOE;
    if (name.endsWith("_SWORD")) return ToolClass.SWORD;
    return ToolClass.NONE;
  }

  private int toolTierScore(Material toolType) {
    String name = toolType.name();
    if (toolType == Material.SHEARS) return 650;
    if (name.startsWith("NETHERITE_")) return 900;
    if (name.startsWith("DIAMOND_")) return 800;
    if (name.startsWith("IRON_")) return 700;
    if (name.startsWith("GOLDEN_")) return 600;
    if (name.startsWith("STONE_")) return 500;
    if (name.startsWith("WOODEN_")) return 400;
    return 100;
  }

  private void stopAreaJob(UUID botUuid, boolean notifyStop) {
    Integer taskId = areaTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    AreaMineJob job = areaJobs.remove(botUuid);
    if (notifyStop && job != null) {
      FakePlayer fp = manager.getByUuid(botUuid);
      notifyStarter(
          job, "mine-area-stopped", "name", fp != null ? fp.getDisplayName() : botUuid.toString());
    }
  }

  private void notifyStarter(AreaMineJob job, String key, String... args) {
    if (job.starterUuid != null) {
      Player p = Bukkit.getPlayer(job.starterUuid);
      if (p != null && p.isOnline()) {
        p.sendMessage(Lang.get(key, args));
        return;
      }
    }
    if (job.consoleStarted) {
      plugin.getLogger().info(Lang.raw(key, args));
    }
  }

  private static final class MiningState {
    BlockPos currentPos;
    float progress;
    int freeze;
    boolean once;
    BlockPos forcedTarget;
    boolean stopAfterForcedTarget;
    boolean completeForcedTargetOnStop;
    boolean waitingForDrops;
    int pickupWaitTicks;
    int pickupWaitExtraTicks;
  }

  private enum ToolClass {
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SHEARS,
    SWORD,
    NONE
  }

  private static final class AreaSelection {
    Location pos1;
    Location pos2;

    boolean isComplete() {
      return pos1 != null && pos2 != null;
    }

    boolean sameWorld() {
      return isComplete() && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
    }

    int minX() {
      return Math.min(pos1.getBlockX(), pos2.getBlockX());
    }

    int maxX() {
      return Math.max(pos1.getBlockX(), pos2.getBlockX());
    }

    int minY() {
      return Math.min(pos1.getBlockY(), pos2.getBlockY());
    }

    int maxY() {
      return Math.max(pos1.getBlockY(), pos2.getBlockY());
    }

    int minZ() {
      return Math.min(pos1.getBlockZ(), pos2.getBlockZ());
    }

    int maxZ() {
      return Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    int blockCount() {
      return (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
    }

    boolean contains(int x, int y, int z) {
      return isComplete()
          && x >= minX()
          && x <= maxX()
          && y >= minY()
          && y <= maxY()
          && z >= minZ()
          && z <= maxZ();
    }

    AreaSelection copy() {
      AreaSelection copy = new AreaSelection();
      copy.pos1 = pos1.clone();
      copy.pos2 = pos2.clone();
      return copy;
    }
  }

  private static final class AreaMineJob {
    final AreaSelection selection;
    final UUID starterUuid;
    final boolean consoleStarted;
    final Set<AreaBlock> completed = new HashSet<>();
    final Set<AreaBlock> skipped = new HashSet<>();

    final Map<BlockPos, Long> anticipatedDrops = new ConcurrentHashMap<>();
    AreaBlock currentTarget;
    int preferredStorageIndex = 0;
    int blocksMined = 0;

    boolean finishingDeposit = false;

    int currentLayer;

    int skipRetries = 0;

    boolean depositingToStorage = false;

    AreaMineJob(AreaSelection selection, CommandSender sender) {
      this.selection = selection;
      this.starterUuid = sender instanceof Player p ? p.getUniqueId() : null;
      this.consoleStarted = !(sender instanceof Player);
      this.currentLayer = selection.maxY();
    }
  }

  private record AreaBlock(int x, int y, int z) {
    BlockPos toBlockPos() {
      return new BlockPos(x, y, z);
    }

    Location center(World world) {
      return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }
  }

  static double parseCoord(String token, double base) {
    if (token.startsWith("~")) {
      String rest = token.substring(1);
      return rest.isEmpty() ? base : base + Double.parseDouble(rest);
    }
    return Double.parseDouble(token);
  }
}
