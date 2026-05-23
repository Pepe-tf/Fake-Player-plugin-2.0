package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBotBlockPlaceEvent;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class PlaceCommand implements FppCommand {

  private static final boolean AREA_MODE_ENABLED = false;

  private enum Phase {
    FILLING,
    RECHECKING,
    CLEANING_SCAFFOLD
  }

  private static final int CONTROLLER_PERIOD = 1;
  private static final int PROGRESS_INTERVAL = 10;
  private static final int SKIP_RETRY_LIMIT = 3;

  private static final double PLACE_REACH = 4.5;
  private static final int PLACE_COOLDOWN = 5;
  private static final int SCAFFOLD_MAX_RETRIES = 4;

  private static final Material[] SCAFFOLD_PREF = {
      Material.DIRT, Material.COBBLESTONE, Material.STONE, Material.SAND,
      Material.GRAVEL, Material.NETHERRACK, Material.DIORITE, Material.ANDESITE,
      Material.GRANITE, Material.COBBLED_DEEPSLATE
  };

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;
  private final StorageStore storageStore;

  private final Map<UUID, Integer> placingTasks = new ConcurrentHashMap<>();
  private final Map<UUID, PlaceState> placeStates = new ConcurrentHashMap<>();

  private final Map<UUID, AreaSelection> selections = new ConcurrentHashMap<>();
  private final Map<UUID, List<BlockEntry>> blockSpecs = new ConcurrentHashMap<>();
  private final Map<UUID, PlaceJob> placeJobs = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> placeTasks = new ConcurrentHashMap<>();

  public PlaceCommand(
      FakePlayerPlugin plugin,
      FakePlayerManager manager,
      StorageStore storageStore,
      PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.storageStore = storageStore;
    this.pathfinding = pathfinding;
  }

  @Override
  public String getName() {
    return "place";
  }

  @Override
  public String getUsage() {
    return "<bot> [--once|--stop]  |  --stop";
  }

  @Override
  public String getDescription() {
    return "Bot places blocks where it is looking. If too far, bot walks closer then places.";
  }

  @Override
  public String getPermission() {
    return Perm.PLACE;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.PLACE);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("place-usage"));
      return true;
    }

    if ((args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("--stop"))
        && args.length == 1) {
      stopAll();
      sender.sendMessage(Lang.get("place-stopped-all"));
      return true;
    }

    FakePlayer fp = manager.getByName(args[0]);
    if (fp == null) {
      sender.sendMessage(Lang.get("place-not-found", "name", args[0]));
      return true;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("place-bot-offline", "name", fp.getDisplayName()));
      return true;
    }

    if (args.length >= 2) {
      String action = args[1].toLowerCase(Locale.ROOT);

      if (!AREA_MODE_ENABLED) {
        Set<String> areaActions =
            Set.of(
                "--pos1", "pos1", "--pos2", "pos2", "--block", "block", "--clear", "clear",
                "start");
        if (areaActions.contains(action)) {
          sender.sendMessage(Lang.get("place-usage"));
          return true;
        }
      }

      switch (action) {
        case "stop", "--stop" -> {
          cleanupBot(fp.getUuid());
          sender.sendMessage(Lang.get("place-stopped", "name", fp.getDisplayName()));
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
            posLoc = player.getLocation().getBlock().getLocation();
          }
          AreaSelection sel = selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          sel.pos1 = posLoc;
          sender.sendMessage(
              Lang.get(
                  "place-pos1-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          if (sel.isComplete())
            sender.sendMessage(
                Lang.get(
                    "place-selection-ready",
                    "name",
                    fp.getDisplayName(),
                    "count",
                    String.valueOf(sel.blockCount())));
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
            posLoc = player.getLocation().getBlock().getLocation();
          }
          AreaSelection sel = selections.computeIfAbsent(fp.getUuid(), k -> new AreaSelection());
          sel.pos2 = posLoc;
          sender.sendMessage(
              Lang.get(
                  "place-pos2-set",
                  "name",
                  fp.getDisplayName(),
                  "x",
                  String.valueOf(posLoc.getBlockX()),
                  "y",
                  String.valueOf(posLoc.getBlockY()),
                  "z",
                  String.valueOf(posLoc.getBlockZ())));
          if (sel.isComplete())
            sender.sendMessage(
                Lang.get(
                    "place-selection-ready",
                    "name",
                    fp.getDisplayName(),
                    "count",
                    String.valueOf(sel.blockCount())));
          return true;
        }

        case "--block", "block" -> {
          if (args.length < 3) {
            sender.sendMessage(Lang.get("place-block-usage"));
            return true;
          }
          String specStr = String.join("", Arrays.copyOfRange(args, 2, args.length));
          List<BlockEntry> spec = parseBlockSpec(specStr);
          if (spec.isEmpty()) {
            sender.sendMessage(Lang.get("place-block-invalid", "spec", specStr));
            return true;
          }
          blockSpecs.put(fp.getUuid(), spec);
          String summary =
              spec.stream()
                  .map(
                      e ->
                          formatMaterial(e.material())
                              + (spec.size() > 1 ? " " + e.weight() + "%" : ""))
                  .collect(Collectors.joining(", "));
          sender.sendMessage(
              Lang.get("place-block-set", "name", fp.getDisplayName(), "spec", summary));
          return true;
        }

        case "--clear", "clear" -> {
          selections.remove(fp.getUuid());
          blockSpecs.remove(fp.getUuid());
          cleanupBot(fp.getUuid());
          sender.sendMessage(Lang.get("place-cleared", "name", fp.getDisplayName()));
          return true;
        }

        case "status", "info" -> {
          sendStatus(sender, fp);
          return true;
        }

        case "start" -> {
          startAreaPlacing(sender, fp);
          return true;
        }
      }
    }

    boolean once =
        args.length >= 2
            && (args[1].equalsIgnoreCase("once") || args[1].equalsIgnoreCase("--once"));
    if (args.length == 1 || once) {
      cleanupBot(fp.getUuid());

      BlockPos targetPos = getTargetBlockFromBot(bot);
      if (targetPos == null) {
        sender.sendMessage(Lang.get("place-look-at-block"));
        return true;
      }

      double dist = bot.getLocation().distance(new Location(bot.getWorld(), targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
      if (dist <= PLACE_REACH) {
        lockAndStartPlacing(fp, once, targetPos);
        sender.sendMessage(
            once
                ? Lang.get("place-started-once", "name", fp.getDisplayName())
                : Lang.get("place-started", "name", fp.getDisplayName()));
      } else {
        Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetPos);
        if (standLoc != null) {
          startNavigation(fp, standLoc, () -> lockAndStartPlacing(fp, once, targetPos));
          sender.sendMessage(Lang.get("place-walking", "name", fp.getDisplayName()));
        } else {
          sender.sendMessage(Lang.get("place-no-path", "name", fp.getDisplayName()));
        }
      }
      return true;
    }

    sender.sendMessage(Lang.get("place-usage"));
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
      for (FakePlayer fp : manager.getActivePlayers())
        if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
      return out;
    }

    if (args.length == 2
        && !args[0].equalsIgnoreCase("stop")
        && !args[0].equalsIgnoreCase("--stop")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      List<String> opts =
          AREA_MODE_ENABLED
              ? List.of(
              "--pos1",
              "--pos2",
              "--block",
              "--clear",
              "--start",
              "--status",
              "--stop",
              "--once",
              "stop",
              "once")
              : List.of("--once", "--stop", "once", "stop");
      for (String opt : opts) if (opt.startsWith(prefix)) out.add(opt);
      return out;
    }

    if (args.length >= 3 && AREA_MODE_ENABLED && args[1].equalsIgnoreCase("--block")) {

      String full = args[2].toUpperCase(Locale.ROOT);
      int lastComma = full.lastIndexOf(',');
      String prefix = lastComma >= 0 ? full.substring(lastComma + 1) : full;
      String already = lastComma >= 0 ? args[2].substring(0, lastComma + 1) : "";

      if (prefix.isEmpty() && lastComma < 0) return List.of();
      final String alreadyFinal = already;
      return Arrays.stream(Material.values())
          .filter(m -> !m.isAir() && m.isBlock() && m.isSolid() && m.name().startsWith(prefix))
          .map(m -> alreadyFinal + m.name())
          .sorted()
          .limit(20)
          .collect(Collectors.toList());
    }

    return List.of();
  }

  private void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
    BotPathfinder.PathOptions baseOpts =
        PathfindingService.resolvePathOptions(fp);
    BotPathfinder.PathOptions opts =
        new BotPathfinder.PathOptions(
            fp.isNavParkour(),
            fp.isNavBreakBlocks(),
            true,
            baseOpts.avoidWater(),
            baseOpts.avoidLava());
    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.PLACE,
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

  private void lockAndStartPlacing(FakePlayer fp, boolean once, BlockPos targetPos) {
    FppApiImpl.fireTaskEvent(fp, "place", FppBotTaskEvent.Action.START);
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
    manager.lockForAction(uuid, actualLoc);

    PlaceState state = new PlaceState();
    state.once = once;
    state.forcedTarget = targetPos;
    placeStates.put(uuid, state);

    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            () -> {
              Player b = fp.getPlayer();
              if (b == null || !b.isOnline()) {
                stopPlacing(uuid);
                return;
              }
              tickPlacing(fp, state);
            },
            0L,
            1L);

    placingTasks.put(uuid, taskId);
  }

  private void tickPlacing(FakePlayer fp, PlaceState state) {
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      stopPlacing(fp.getUuid());
      return;
    }

    if (state.freeze > 0) {
      state.freeze--;
      return;
    }

    BlockPos targetPos = state.forcedTarget;
    if (targetPos == null) {
      targetPos = getTargetBlockFromBot(bot);
      if (targetPos == null) {
        if (state.once) stopPlacing(fp.getUuid());
        return;
      }
      state.forcedTarget = targetPos;
    }

    Material mat = findPlaceableMaterial(bot.getInventory());
    if (mat != null) equipMaterial(bot, mat);

    BlockHitResult hit = rayTraceBlock(bot);
    if (hit == null) {
      if (state.once) stopPlacing(fp.getUuid());
      return;
    }

    var faceBlockPos = hit.getBlockPos();
    Direction faceDir = hit.getDirection();
    if (faceBlockPos == null || faceDir == null) {
      if (state.once) stopPlacing(fp.getUuid());
      return;
    }

    placeBlockNms(
        fp,
        bot,
        faceBlockPos,
        faceDir);

    if (state.once) {
      stopPlacing(fp.getUuid());
      return;
    }
    state.freeze = PLACE_COOLDOWN;
  }

  private void placeBlockNms(FakePlayer fp, Player bot, BlockPos faceBlockPos, Direction faceDir) {
    try {
      ServerPlayer nms = ((CraftPlayer) bot).getHandle();
      if (!fireBlockPlaceHook(fp, faceBlockPos)) return;
      Vec3 hitVec =
          new Vec3(
              faceBlockPos.getX() + 0.5 + faceDir.getStepX() * 0.5,
              faceBlockPos.getY() + 0.5 + faceDir.getStepY() * 0.5,
              faceBlockPos.getZ() + 0.5 + faceDir.getStepZ() * 0.5);
      BlockHitResult hit = new BlockHitResult(hitVec, faceDir, faceBlockPos, false);
      nms.resetLastActionTime();
      var result = NmsPlayerSpawner.useItemOn(nms, InteractionHand.MAIN_HAND, hit);

      if (NmsPlayerSpawner.consumesAction(result)) {
        nms.swing(InteractionHand.MAIN_HAND);
      }
    } catch (Throwable ignored) {
    }
  }

  private static Direction toNmsDirection(BlockFace face) {
    return switch (face) {
      case UP -> Direction.UP;
      case DOWN -> Direction.DOWN;
      case NORTH -> Direction.NORTH;
      case SOUTH -> Direction.SOUTH;
      case EAST -> Direction.EAST;
      case WEST -> Direction.WEST;
      default -> Direction.UP;
    };
  }

  private boolean fireBlockPlaceHook(FakePlayer fp, BlockPos pos) {
    if (fp == null || pos == null) return false;
    Player bot = fp.getPlayer();
    if (bot == null || bot.getWorld() == null) return false;
    var event =
        new FppBotBlockPlaceEvent(
            new FppBotImpl(fp), bot.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
    Bukkit.getPluginManager().callEvent(event);
    return !event.isCancelled();
  }

  @Nullable
  private BlockHitResult rayTraceBlock(Player bot) {
    try {
      Location eye = bot.getEyeLocation();
      org.bukkit.util.RayTraceResult result = bot.getWorld().rayTraceBlocks(
          eye,
          eye.getDirection(),
          PLACE_REACH,
          org.bukkit.FluidCollisionMode.NEVER,
          false
      );
      return result != null ? result.getHitBlockFace() != null ? 
          new BlockHitResult(
              new Vec3(result.getHitPosition().getX(), result.getHitPosition().getY(), result.getHitPosition().getZ()),
              toNmsDirection(result.getHitBlockFace()),
              new BlockPos(result.getHitBlock().getX(), result.getHitBlock().getY(), result.getHitBlock().getZ()),
              false
          ) : null : null;
    } catch (Exception e) {
      return null;
    }
  }

  private Material findPlaceableMaterial(PlayerInventory inv) {
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getType().isAir()) continue;
      if (item.getType().isBlock() && item.getType().isSolid() && !isLikelyTool(item))
        return item.getType();
    }
    return null;
  }

  public void stopPlacing(UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      FppApiImpl.fireTaskEvent(fp, "place", FppBotTaskEvent.Action.STOP);
    }
    Integer taskId = placingTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    manager.unlockAction(botUuid);
    manager.unlockNavigation(botUuid);
    placeStates.remove(botUuid);
  }

  @Nullable
  public BlockPos getActivePlaceTarget(UUID botUuid) {
    PlaceState state = placeStates.get(botUuid);
    return state != null ? state.forcedTarget : null;
  }

  @Nullable
  public Location getActivePlaceLocation(UUID botUuid) {
    PlaceState state = placeStates.get(botUuid);
    if (state == null || state.forcedTarget == null) return null;
    BlockPos pos = state.forcedTarget;
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp == null) return null;
    Player bot = fp.getPlayer();
    if (bot == null || bot.getWorld() == null) return null;
    return new Location(bot.getWorld(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
  }

  public boolean isActivePlaceOnce(UUID botUuid) {
    PlaceState state = placeStates.get(botUuid);
    return state != null && state.once;
  }

  public boolean isPlacing(UUID botUuid) {
    return placeStates.containsKey(botUuid);
  }

  public void resumePlacing(FakePlayer fp) {
    UUID uuid = fp.getUuid();
    PlaceState state = placeStates.get(uuid);
    if (state != null && state.forcedTarget != null) {
      resumePlacing(fp, state.once, state.forcedTarget);
    }
  }

  public void resumePlacing(FakePlayer fp, boolean once, BlockPos targetPos) {
    if (fp == null || targetPos == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    stopPlacing(fp.getUuid());
    double dist = bot.getLocation().distance(new Location(bot.getWorld(), targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
    if (dist <= PLACE_REACH) {
      lockAndStartPlacing(fp, once, targetPos);
    } else {
      Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetPos);
      if (standLoc != null) {
        startNavigation(fp, standLoc, () -> lockAndStartPlacing(fp, once, targetPos));
      }
    }
  }

  public void resumePlacing(FakePlayer fp, boolean once, Location targetLoc) {
    if (fp == null || targetLoc == null) return;
    BlockPos targetPos = new BlockPos(targetLoc.getBlockX(), targetLoc.getBlockY(), targetLoc.getBlockZ());
    resumePlacing(fp, once, targetPos);
  }

  private void sendStatus(CommandSender sender, FakePlayer fp) {
    AreaSelection sel = selections.get(fp.getUuid());
    List<BlockEntry> spec = blockSpecs.get(fp.getUuid());
    PlaceJob job = placeJobs.get(fp.getUuid());

    if (sel == null || !sel.isComplete()) {
      sender.sendMessage(Lang.get("place-status-no-selection", "name", fp.getDisplayName()));
    } else {
      sender.sendMessage(
          Lang.get(
              "place-status-selection",
              "name",
              fp.getDisplayName(),
              "x1",
              String.valueOf(sel.pos1.getBlockX()),
              "y1",
              String.valueOf(sel.pos1.getBlockY()),
              "z1",
              String.valueOf(sel.pos1.getBlockZ()),
              "x2",
              String.valueOf(sel.pos2.getBlockX()),
              "y2",
              String.valueOf(sel.pos2.getBlockY()),
              "z2",
              String.valueOf(sel.pos2.getBlockZ()),
              "count",
              String.valueOf(sel.blockCount())));
    }

    if (spec != null && !spec.isEmpty()) {
      String summary =
          spec.stream()
              .map(
                  e ->
                      formatMaterial(e.material())
                          + (spec.size() > 1 ? " " + e.weight() + "%" : ""))
              .collect(Collectors.joining(", "));
      sender.sendMessage(
          Lang.get("place-status-spec", "name", fp.getDisplayName(), "spec", summary));
    } else {
      sender.sendMessage(Lang.get("place-status-spec-auto", "name", fp.getDisplayName()));
    }

    if (job != null) {
      sender.sendMessage(
          Lang.get(
              "place-status-active",
              "name",
              fp.getDisplayName(),
              "placed",
              String.valueOf(job.blocksPlaced),
              "total",
              String.valueOf(job.totalFillable)));
    } else {
      sender.sendMessage(Lang.get("place-status-no-job", "name", fp.getDisplayName()));
    }
  }

  private void startAreaPlacing(CommandSender sender, FakePlayer fp) {
    AreaSelection sel = selections.get(fp.getUuid());
    if (sel == null || !sel.isComplete()) {
      sender.sendMessage(Lang.get("place-area-missing-selection", "name", fp.getDisplayName()));
      return;
    }
    if (!sel.sameWorld()) {
      sender.sendMessage(Lang.get("mine-area-world-mismatch"));
      return;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("place-bot-offline", "name", fp.getDisplayName()));
      return;
    }
    if (bot.getWorld() != Objects.requireNonNull(sel.pos1.getWorld())) {
      sender.sendMessage(
          Lang.get(
              "mine-area-bot-world",
              "name",
              fp.getDisplayName(),
              "world",
              sel.pos1.getWorld().getName()));
      return;
    }

    List<BlockEntry> spec = blockSpecs.get(fp.getUuid());
    if (spec == null || spec.isEmpty()) {
      spec = buildSpecFromInventoryAndStorage(fp, bot);
      if (spec.isEmpty()) {
        sender.sendMessage(Lang.get("place-no-blocks", "name", fp.getDisplayName()));
        return;
      }
    }

    World world = Objects.requireNonNull(sel.pos1.getWorld());
    int fillable = countFillableBlocks(world, sel);
    if (fillable == 0) {
      sender.sendMessage(Lang.get("place-area-already-filled", "name", fp.getDisplayName()));
      return;
    }
    if (!checkAndReportMissing(sender, fp, bot, spec, fillable)) return;

    cleanupBot(fp.getUuid());
    PlaceJob job = new PlaceJob(sel.copy(), spec, fillable, sender);
    placeJobs.put(fp.getUuid(), job);
    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin, () -> tickPlaceJob(fp.getUuid()), 0L, CONTROLLER_PERIOD);
    placeTasks.put(fp.getUuid(), taskId);
    sender.sendMessage(
        Lang.get(
            "place-area-started", "name", fp.getDisplayName(), "count", String.valueOf(fillable)));
  }

  private void tickPlaceJob(UUID botUuid) {
  }

  private boolean equipMaterial(Player bot, Material mat) {
    PlayerInventory inv = bot.getInventory();
    int held = inv.getHeldItemSlot();
    ItemStack cur = inv.getItem(held);
    if (cur != null && cur.getType() == mat && cur.getAmount() > 0) return true;
    for (int slot = 0; slot <= 8; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item != null && item.getType() == mat && item.getAmount() > 0) {
        inv.setHeldItemSlot(slot);
        return true;
      }
    }
    for (int slot = 9; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item != null && item.getType() == mat && item.getAmount() > 0) {
        ItemStack heldItem = inv.getItem(held);
        inv.setItem(held, item.clone());
        inv.setItem(slot, heldItem);
        return true;
      }
    }
    return false;
  }

  private boolean isLikelyTool(ItemStack item) {
    String n = item.getType().name();
    return n.endsWith("_PICKAXE")
        || n.endsWith("_AXE")
        || n.endsWith("_SHOVEL")
        || n.endsWith("_HOE")
        || n.endsWith("_SWORD")
        || item.getType() == Material.SHEARS;
  }

  public void cleanupBot(UUID botUuid) {
    stopPlacing(botUuid);
    stopPlaceJob(botUuid, false);
  }

  private void stopPlaceJob(UUID botUuid, boolean notifyStop) {
    Integer taskId = placeTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    PlaceJob job = placeJobs.remove(botUuid);
    if (notifyStop && job != null) {
      FakePlayer fp = manager.getByUuid(botUuid);
      notifyStarter(
          job, "place-area-stopped", "name", fp != null ? fp.getDisplayName() : botUuid.toString());
    }
  }

  private void notifyStarter(PlaceJob job, String key, String... args) {
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

  public void stopAll() {
    pathfinding.cancelAll(PathfindingService.Owner.PLACE);
    new HashSet<>(placingTasks.keySet()).forEach(this::stopPlacing);
    new HashSet<>(placeTasks.keySet()).forEach(this::cleanupBot);
  }

  private static final class PlaceState {
    boolean once;
    BlockPos forcedTarget;
    int freeze;
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

  private record BlockEntry(Material material, int weight) {}

  private static final class PlaceJob {
    final AreaSelection selection;
    final UUID starterUuid;
    final boolean consoleStarted;
    final List<BlockEntry> spec;
    final int totalFillable;
    int blocksPlaced = 0;

    PlaceJob(AreaSelection selection, List<BlockEntry> spec, int fillable, CommandSender sender) {
      this.selection = selection;
      this.spec = spec;
      this.totalFillable = fillable;
      this.starterUuid = sender instanceof Player p ? p.getUniqueId() : null;
      this.consoleStarted = !(sender instanceof Player);
    }
  }

  private List<BlockEntry> buildSpecFromInventoryAndStorage(FakePlayer fp, Player bot) {
    Map<Material, Integer> counts = new LinkedHashMap<>();
    PlayerInventory inv = bot.getInventory();
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = inv.getItem(slot);
      if (item == null || item.getType().isAir() || !item.getType().isBlock() || isLikelyTool(item))
        continue;
      counts.merge(item.getType(), item.getAmount(), Integer::sum);
    }
    for (StorageStore.StoragePoint point : storageStore.getStorages(fp.getName())) {
      Block block = point.location().getBlock();
      if (!(block.getState() instanceof InventoryHolder holder)) continue;
      for (ItemStack item : holder.getInventory().getContents()) {
        if (item == null
            || item.getType().isAir()
            || !item.getType().isBlock()
            || isLikelyTool(item)) continue;
        counts.merge(item.getType(), item.getAmount(), Integer::sum);
      }
    }
    return counts.entrySet().stream()
        .filter(e -> e.getValue() > 0)
        .map(e -> new BlockEntry(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  private List<BlockEntry> parseBlockSpec(String spec) {
    List<BlockEntry> result = new ArrayList<>();
    String[] parts = spec.split(",");
    Pattern p = Pattern.compile("^([A-Z_]+?)(\\d+)?%?$");
    int defaultWeight = Math.max(1, 100 / parts.length);
    for (String part : parts) {
      part = part.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
      Matcher m = p.matcher(part);
      if (!m.matches()) continue;
      String matName = m.group(1);
      int weight = m.group(2) != null ? Math.max(1, Integer.parseInt(m.group(2))) : defaultWeight;
      Material mat = Material.matchMaterial(matName);
      if (mat == null || mat.isAir() || !mat.isBlock()) continue;
      result.add(new BlockEntry(mat, weight));
    }
    return result;
  }

  private String formatMaterial(Material mat) {
    String name = mat.name().replace('_', ' ').toLowerCase();
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private int countFillableBlocks(World world, AreaSelection sel) {
    int count = 0;
    for (int y = sel.minY(); y <= sel.maxY(); y++)
      for (int x = sel.minX(); x <= sel.maxX(); x++)
        for (int z = sel.minZ(); z <= sel.maxZ(); z++)
          if (world.getBlockAt(x, y, z).getType().isAir()) count++;
    return count;
  }

  private boolean checkAndReportMissing(
      CommandSender sender, FakePlayer fp, Player bot, List<BlockEntry> spec, int fillable) {
    return true;
  }

  @Nullable
  private BlockPos getTargetBlockFromBot(Player bot) {
    try {
      Location eye = bot.getEyeLocation();
      org.bukkit.util.RayTraceResult result = bot.getWorld().rayTraceBlocks(
          eye,
          eye.getDirection(),
          PLACE_REACH,
          org.bukkit.FluidCollisionMode.NEVER,
          false
      );
      if (result != null && result.getHitBlock() != null) {
        Block b = result.getHitBlock();
        return new BlockPos(b.getX(), b.getY(), b.getZ());
      }
    } catch (Exception ignored) {
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
              if (dist <= PLACE_REACH - 1.5) {
                return faceTowardBlock(loc, target);
              }
            }
          }
        }
      }
    }
    return null;
  }

  static double parseCoord(String token, double base) {
    if (token.startsWith("~")) {
      String rest = token.substring(1);
      return rest.isEmpty() ? base : base + Double.parseDouble(rest);
    }
    return Double.parseDouble(token);
  }
}
