package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotInteractEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UseCommand implements FppCommand {

  private static final double USE_ACTION_ARRIVAL_DISTANCE = 0.35;
  private static final int INSTANT_USE_COOLDOWN = 4;
  private static final int PLACE_COOLDOWN = 5;
  private static final double USE_REACH = 4.5;

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;

  private final Map<UUID, Integer> useTasks = new ConcurrentHashMap<>();
  private final Map<UUID, Location> activeUseLocations = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> activeUseOnceFlags = new ConcurrentHashMap<>();
  private final Map<UUID, Object> activeUseTargets = new ConcurrentHashMap<>();
  private final Map<UUID, UseMode> activeUseModes = new ConcurrentHashMap<>();

  public UseCommand(
      FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.pathfinding = pathfinding;
  }

  @Override
  public String getName() {
    return "use";
  }

  @Override
  public String getUsage() {
    return "<bot> [--once|--stop]  |  --stop";
  }

  public enum UseMode {
    USE_ONLY,
    PLACE_ONLY,
    USE_AND_PLACE
  }

  @Override
  public String getDescription() {
    return "Bot right-clicks what it is looking at (blocks, entities, air). Bot can move freely while using.";
  }

  @Override
  public String getPermission() {
    return Perm.USE_CMD;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.USE_CMD);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("use-usage"));
      return true;
    }

    if ((args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("--stop"))
        && args.length == 1) {
      stopAll();
      sender.sendMessage(Lang.get("use-stopped-all"));
      return true;
    }

    FakePlayer fp = manager.getByName(args[0]);
    if (fp == null) {
      sender.sendMessage(Lang.get("use-not-found", "name", args[0]));
      return true;
    }
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("use-bot-offline", "name", fp.getDisplayName()));
      return true;
    }

    if (args.length >= 2) {
      String action = args[1].toLowerCase();
      if (action.equals("stop") || action.equals("--stop")) {
        cancelAll(fp.getUuid());
        sender.sendMessage(Lang.get("use-stopped", "name", fp.getDisplayName()));
        return true;
      }
    }

    UseMode mode = UseMode.USE_ONLY;
    boolean once = false;
    if (args.length >= 2) {
      String action = args[1].toLowerCase();
      if (action.equals("once") || action.equals("--once")) {
        once = true;
      } else if (action.equals("--place") || action.equals("place")) {
        mode = UseMode.PLACE_ONLY;
      } else if (action.equals("--both") || action.equals("both")) {
        mode = UseMode.USE_AND_PLACE;
      }
    }
    if (args.length == 1) {
      mode = UseMode.USE_AND_PLACE;
    }

    cancelAll(fp.getUuid());

    Object target = null;
    if (sender instanceof Player player) {
      target = rayTraceTargetPlayer(player);
    }
    if (target == null) {
      target = rayTraceTarget(bot);
    }
    if (target == null) {
      sender.sendMessage(Lang.get("use-look-at-target"));
      return true;
    }

    Location targetLoc = getTargetLocation(bot, target);
    if (targetLoc == null) {
      sender.sendMessage(Lang.get("use-look-at-target"));
      return true;
    }

    double dist = bot.getLocation().distance(targetLoc);
    if (dist <= USE_REACH) {
      lockAndStartUsing(fp, once, target, mode);
      sender.sendMessage(
          once
              ? Lang.get("use-started-once", "name", fp.getDisplayName())
              : Lang.get("use-started", "name", fp.getDisplayName()));
    } else {
      Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
      if (standLoc != null) {
        startNavigation(fp, once, standLoc, target, mode);
        sender.sendMessage(Lang.get("use-walking", "name", fp.getDisplayName()));
      } else {
        sender.sendMessage(Lang.get("use-no-path", "name", fp.getDisplayName()));
      }
    }
    return true;
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (!canUse(sender)) return List.of();

    if (args.length == 1) {
      String prefix = args[0].toLowerCase();
      List<String> out = new ArrayList<>();
      if ("--stop".startsWith(prefix)) out.add("--stop");
      if ("stop".startsWith(prefix)) out.add("stop");
      for (FakePlayer fp : manager.getActivePlayers())
        if (fp.getName().toLowerCase().startsWith(prefix)) out.add(fp.getName());
      return out;
    }

    if (args.length == 2
        && !args[0].equalsIgnoreCase("stop")
        && !args[0].equalsIgnoreCase("--stop")) {
      String prefix = args[1].toLowerCase();
      List<String> out = new ArrayList<>();
      if ("--once".startsWith(prefix)) out.add("--once");
      if ("--stop".startsWith(prefix)) out.add("--stop");
      if ("--place".startsWith(prefix)) out.add("--place");
      if ("--both".startsWith(prefix)) out.add("--both");
      if ("once".startsWith(prefix)) out.add("once");
      if ("stop".startsWith(prefix)) out.add("stop");
      if ("place".startsWith(prefix)) out.add("place");
      if ("both".startsWith(prefix)) out.add("both");
      return out;
    }

    return List.of();
  }

  // ── Navigation ──

  private void startNavigation(FakePlayer fp, boolean once, Location dest, Object target, UseMode mode) {
    pathfinding.navigate(
        fp,
        new PathfindingService.NavigationRequest(
            PathfindingService.Owner.USE,
            () -> dest,
            USE_ACTION_ARRIVAL_DISTANCE,
            0.0,
            Integer.MAX_VALUE,
            () -> lockAndStartUsing(fp, once, target, mode),
            null,
            null));
  }

  // ── Lock + Use Loop ──

  private void lockAndStartUsing(FakePlayer fp, boolean once, Object target, UseMode mode) {
    FppApiImpl.fireTaskEvent(fp, "use", FppBotTaskEvent.Action.START);
    UUID uuid = fp.getUuid();
    Player bot = fp.getPlayer();
    if (bot == null) return;

    Location faceLoc = faceTowardTarget(bot.getLocation(), target);
    bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
    NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
    NmsPlayerSpawner.setMovementForward(bot, 0f);
    bot.setSprinting(false);

    Location actualLoc = bot.getLocation().clone();
    actualLoc.setYaw(faceLoc.getYaw());
    actualLoc.setPitch(faceLoc.getPitch());
    manager.lockForAction(uuid, actualLoc, false);

    activeUseLocations.put(uuid, actualLoc.clone());
    activeUseOnceFlags.put(uuid, once);
    activeUseTargets.put(uuid, target);
    activeUseModes.put(uuid, mode);

    final int[] cooldown = {0};

    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            () -> {
              Player b = fp.getPlayer();
              if (b == null || !b.isOnline()) {
                stopUsing(uuid);
                return;
              }

              ServerPlayer nms = ((CraftPlayer) b).getHandle();
              nms.resetLastActionTime();

              if (plugin.getInventoryCommand().isInventoryOpen(uuid)) {
                if (nms.isUsingItem()) {
                  ((CraftPlayer) b).getHandle().releaseUsingItem();
                }
                return;
              }

              if (nms.isUsingItem()) {
                if (once) stopUsing(uuid);
                return;
              }

              if (cooldown[0] > 0) {
                cooldown[0]--;
                return;
              }

              HitResult hit = rayTraceNms(nms);
              Object currentTarget = getTargetFromHit(hit, b);
              if (currentTarget != null) {
                activeUseTargets.put(uuid, currentTarget);
              }

              boolean acted = false;
              boolean startedHolding = false;

              if ((mode == UseMode.PLACE_ONLY || mode == UseMode.USE_AND_PLACE) && hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                var pos = blockHit.getBlockPos();
                Direction side = blockHit.getDirection();
                var placed = tryPlaceBlock(nms, pos, side);
                if (placed) {
                  nms.swing(InteractionHand.MAIN_HAND);
                  acted = true;
                  if (once) {
                    stopUsing(uuid);
                    return;
                  }
                  cooldown[0] = PLACE_COOLDOWN;
                }
              }

              if (!acted && (mode == UseMode.USE_ONLY || mode == UseMode.USE_AND_PLACE)) {
                for (InteractionHand hand : InteractionHand.values()) {
                  if (acted) break;

                  if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    var pos = blockHit.getBlockPos();
                    Direction side = blockHit.getDirection();

                    if (pos.getY() < nms.level().getMaxY() - (side == Direction.UP ? 1 : 0)
                        && nms.level().mayInteract(nms, pos)) {
                      Object result = NmsPlayerSpawner.useItemOn(nms, hand, blockHit);
                      if (NmsPlayerSpawner.consumesAction(result)) {
                        nms.swing(hand);
                        acted = true;
                        break;
                      }
                    }

                  } else if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHit = (EntityHitResult) hit;
                    var entity = entityHit.getEntity();

                    boolean handWasEmpty = nms.getItemInHand(hand).isEmpty();
                    boolean itemFrameEmpty =
                        (entity instanceof ItemFrame ife) && ife.getItem().isEmpty();

                    var bukkitEntity = entity.getBukkitEntity();
                    var equipSlot =
                        hand == InteractionHand.MAIN_HAND
                            ? EquipmentSlot.HAND
                            : EquipmentSlot.OFF_HAND;
                    var interactEvent =
                        new FppBotInteractEvent(new FppBotImpl(fp), bukkitEntity, equipSlot);
                    Bukkit.getPluginManager().callEvent(interactEvent);
                    if (interactEvent.isCancelled()) {
                      acted = true;
                      break;
                    }

                    if (NmsPlayerSpawner.interactOnEntity(nms, entity, hand)
                        && !(handWasEmpty && itemFrameEmpty)) {
                      nms.swing(hand);
                      acted = true;
                      break;
                    }
                  }

                  Object useResult = NmsPlayerSpawner.useItem(nms, hand);
                  if (NmsPlayerSpawner.consumesAction(useResult)) {
                    if (nms.isUsingItem()) {
                      startedHolding = true;
                    }
                    acted = true;
                    break;
                  }
                }
              }

              if (acted) {
                if (startedHolding) {
                  // holding-use item (food, shield, bow, etc.) — stays active
                } else {
                  cooldown[0] = INSTANT_USE_COOLDOWN;
                }
              }

              if (once && acted) stopUsing(uuid);
            },
            0L,
            1L);

    useTasks.put(uuid, taskId);
  }

  // ── Stop / Cancel ──

  private void cancelAll(UUID botUuid) {
    pathfinding.cancel(botUuid);
    stopUsing(botUuid);
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

  public void stopUsing(UUID botUuid) {
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      FppApiImpl.fireTaskEvent(fp, "use", FppBotTaskEvent.Action.STOP);
    }
    Integer taskId = useTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    manager.unlockAction(botUuid);
    activeUseLocations.remove(botUuid);
    activeUseOnceFlags.remove(botUuid);
    activeUseTargets.remove(botUuid);
    activeUseModes.remove(botUuid);
    if (fp != null) {
      Player bot = fp.getPlayer();
      if (bot != null && bot.isOnline()) ((CraftPlayer) bot).getHandle().releaseUsingItem();
    }
  }

  public void stopAll() {
    pathfinding.cancelAll(PathfindingService.Owner.USE);
    new HashSet<>(useTasks.keySet()).forEach(this::cancelAll);
  }

  // ── Public API ──

  public boolean isNavigating(UUID botUuid) {
    return pathfinding.isNavigating(botUuid);
  }

  public boolean isUsing(UUID botUuid) {
    return useTasks.containsKey(botUuid);
  }

  @Nullable
  public Location getActiveUseLocation(UUID botUuid) {
    return activeUseLocations.get(botUuid);
  }

  public boolean isActiveUseOnce(UUID botUuid) {
    Boolean v = activeUseOnceFlags.get(botUuid);
    return v != null && v;
  }

  @Nullable
  public Object getActiveUseTarget(UUID botUuid) {
    return activeUseTargets.get(botUuid);
  }

  public void resumeUsing(FakePlayer fp) {
    UUID uuid = fp.getUuid();
    Location useLoc = getActiveUseLocation(uuid);
    boolean once = isActiveUseOnce(uuid);
    Object target = getActiveUseTarget(uuid);
    UseMode mode = activeUseModes.getOrDefault(uuid, UseMode.USE_ONLY);
    if (useLoc != null && target != null) {
      resumeUsing(fp, once, useLoc, target, mode);
    }
  }

  public void resumeUsing(FakePlayer fp, boolean once, Location loc, Object target, UseMode mode) {
    if (fp == null || loc == null || target == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    cancelAll(fp.getUuid());
    if (PathfindingService.xzDist(bot.getLocation(), loc) <= USE_ACTION_ARRIVAL_DISTANCE) {
      lockAndStartUsing(fp, once, target, mode);
    } else {
      startNavigation(fp, once, loc, target, mode);
    }
  }

  // ── Place Block Helper ──

  private boolean tryPlaceBlock(ServerPlayer nms, net.minecraft.core.BlockPos facePos, Direction faceDir) {
    try {
      var item = nms.getItemInHand(InteractionHand.MAIN_HAND);
      if (item.isEmpty()) {
        return false;
      }
      Vec3 hitVec = new Vec3(
          facePos.getX() + 0.5 + faceDir.getStepX() * 0.5,
          facePos.getY() + 0.5 + faceDir.getStepY() * 0.5,
          facePos.getZ() + 0.5 + faceDir.getStepZ() * 0.5);
      BlockHitResult hit = new BlockHitResult(hitVec, faceDir, facePos, false);
      nms.resetLastActionTime();
      var result = NmsPlayerSpawner.useItemOn(nms, InteractionHand.MAIN_HAND, hit);
      if (NmsPlayerSpawner.consumesAction(result)) {
        nms.swing(InteractionHand.MAIN_HAND);
        return true;
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  // ── Ray Trace ──

  @Nullable
  private Object rayTraceTargetPlayer(Player player) {
    try {
      Block playerTarget = player.getTargetBlockExact((int) Math.ceil(USE_REACH));
      if (playerTarget != null && !playerTarget.getType().isAir()) {
        if (Config.debugCommands()) {
          Config.debug("UseCommand.rayTraceTargetPlayer: hit block=" + playerTarget.getType() + " @ " + playerTarget.getLocation());
        }
        return playerTarget;
      }
      List<Entity> nearby = player.getNearbyEntities(USE_REACH, USE_REACH, USE_REACH);
      for (Entity ent : nearby) {
        if (ent instanceof org.bukkit.entity.LivingEntity) {
          Location eye = player.getEyeLocation();
          Location entEye = ent.getLocation().add(0, ent.getHeight() / 2, 0);
          org.bukkit.util.Vector dir = eye.getDirection();
          org.bukkit.util.Vector toEnt = entEye.toVector().subtract(eye.toVector());
          double angle = dir.angle(toEnt);
          if (angle < 0.5) {
            if (Config.debugCommands()) {
              Config.debug("UseCommand.rayTraceTargetPlayer: hit entity=" + ent.getType() + " @ " + ent.getLocation());
            }
            return ent;
          }
        }
      }
    } catch (Exception e) {
      if (Config.debugCommands()) {
        Config.debug("UseCommand.rayTraceTargetPlayer: exception: " + e.getMessage());
      }
    }
    return null;
  }

  @Nullable
  private Object rayTraceTarget(Player bot) {
    try {
      Location eye = bot.getEyeLocation();
      if (Config.debugCommands()) {
        Config.debug("UseCommand.rayTraceTarget: eye=" + eye + ", dir=" + eye.getDirection() + ", reach=" + USE_REACH);
      }
      org.bukkit.util.RayTraceResult result = bot.getWorld().rayTraceBlocks(
          eye,
          eye.getDirection(),
          USE_REACH,
          org.bukkit.FluidCollisionMode.NEVER,
          false
      );
      if (result != null && result.getHitBlock() != null) {
        if (Config.debugCommands()) {
          Config.debug("UseCommand.rayTraceTarget: hit block=" + result.getHitBlock().getType() + " @ " + result.getHitBlock().getLocation());
        }
        return result.getHitBlock();
      }
      org.bukkit.entity.Entity hitEntity = result != null ? result.getHitEntity() : null;
      if (hitEntity != null) {
        if (Config.debugCommands()) {
          Config.debug("UseCommand.rayTraceTarget: hit entity=" + hitEntity.getType() + " @ " + hitEntity.getLocation());
        }
        return hitEntity;
      }
      if (Config.debugCommands()) {
        Config.debug("UseCommand.rayTraceTarget: Bukkit rayTrace returned null/no hit");
      }
    } catch (Exception e) {
      if (Config.debugCommands()) {
        Config.debug("UseCommand.rayTraceTarget: Bukkit exception: " + e.getMessage());
      }
    }
    ServerPlayer nms = ((CraftPlayer) bot).getHandle();
    HitResult nmsHit = rayTraceNms(nms);
    return getTargetFromHit(nmsHit, bot);
  }

  @Nullable
  private Object getTargetFromHit(HitResult hit, Player bot) {
    if (hit == null) {
      if (Config.debugCommands()) {
        Config.debug("UseCommand.getTargetFromHit: no target found (Bukkit or NMS)");
      }
      return null;
    }
    if (hit.getType() == HitResult.Type.BLOCK) {
      BlockHitResult bhr = (BlockHitResult) hit;
      if (Config.debugCommands()) {
        Config.debug("UseCommand.getTargetFromHit: NMS hit block @ " + bhr.getBlockPos());
      }
      return bot.getWorld().getBlockAt(bhr.getBlockPos().getX(), bhr.getBlockPos().getY(), bhr.getBlockPos().getZ());
    } else if (hit.getType() == HitResult.Type.ENTITY) {
      EntityHitResult ehr = (EntityHitResult) hit;
      if (Config.debugCommands()) {
        Config.debug("UseCommand.getTargetFromHit: NMS hit entity=" + ehr.getEntity().getType() + " @ " + ehr.getEntity().position());
      }
      return ehr.getEntity().getBukkitEntity();
    }
    return null;
  }

  @Nullable
  private Location getTargetLocation(Player bot, Object target) {
    if (target instanceof Block b) {
      return new Location(bot.getWorld(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
    } else if (target instanceof org.bukkit.entity.Entity e) {
      return e.getLocation().clone();
    }
    return null;
  }

  private Location faceTowardTarget(Location botLoc, Object target) {
    double tx, ty, tz;
    if (target instanceof Block b) {
      tx = b.getX() + 0.5;
      ty = b.getY() + 0.5;
      tz = b.getZ() + 0.5;
    } else if (target instanceof org.bukkit.entity.Entity e) {
      Location eLoc = e.getLocation();
      tx = eLoc.getX() + 0.5;
      ty = eLoc.getY() + 1.0;
      tz = eLoc.getZ() + 0.5;
    } else {
      return botLoc.clone();
    }
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
  private Location findStandLocationNearTarget(World world, Location targetLoc) {
    int tx = targetLoc.getBlockX(), ty = targetLoc.getBlockY(), tz = targetLoc.getBlockZ();
    for (int r = 1; r <= 4; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          if (Math.abs(dx) < r && Math.abs(dz) < r) continue;
          int cx = tx + dx, cz = tz + dz;
          for (int dy : new int[]{0, -1, 1}) {
            int cy = ty + dy;
            if (BotPathfinder.walkable(world, cx, cy, cz)) {
              Location loc = new Location(world, cx + 0.5, cy, cz + 0.5);
              double dist = loc.distance(targetLoc);
              if (dist <= USE_REACH - 1.5) {
                return faceTowardTarget(loc, targetLoc);
              }
            }
          }
        }
      }
    }
    return null;
  }

  @SuppressWarnings("resource")
  private static HitResult rayTraceNms(ServerPlayer player) {
    double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
    Vec3 eyePos = player.getEyePosition(1.0f);
    Vec3 viewVec = player.getViewVector(1.0f);
    Vec3 endPos = eyePos.add(viewVec.x * reach, viewVec.y * reach, viewVec.z * reach);

    BlockHitResult blockHit;
    try {
      blockHit =
          player
              .level()
              .clip(
                  new ClipContext(
                      eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    } catch (Exception e) {
      return null;
    }

    double maxSqDist = reach * reach;
    if (blockHit.getType() != HitResult.Type.MISS)
      maxSqDist = blockHit.getLocation().distanceToSqr(eyePos);

    AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0);

    EntityHitResult entityHit = null;
    double entityDistSq = maxSqDist;

    for (var entity :
        player.level().getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable())) {
      AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
      var hitOpt = entityBox.clip(eyePos, endPos);
      if (entityBox.contains(eyePos)) {
        if (entityDistSq >= 0) {
          entityHit = new EntityHitResult(entity, hitOpt.orElse(eyePos));
          entityDistSq = 0;
        }
      } else if (hitOpt.isPresent()) {
        double d = eyePos.distanceToSqr(hitOpt.get());
        if (d < entityDistSq || entityDistSq == 0) {
          entityHit = new EntityHitResult(entity, hitOpt.get());
          entityDistSq = d;
        }
      }
    }

    if (entityHit != null) return (HitResult) entityHit;
    return (HitResult) blockHit;
  }
}