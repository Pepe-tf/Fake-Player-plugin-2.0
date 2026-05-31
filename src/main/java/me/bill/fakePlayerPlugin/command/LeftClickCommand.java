package me.bill.fakePlayerPlugin.command;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBotBlockBreakEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppScheduler;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LeftClickCommand implements FppCommand {

  private static final double CLICK_REACH = 5.0;
  private static final int CLICK_COOLDOWN = 4;

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final PathfindingService pathfinding;

  private final Map<UUID, Integer> clickTasks = new ConcurrentHashMap<>();
  private final Map<UUID, ClickState> clickStates = new ConcurrentHashMap<>();
  private final Map<UUID, ClickMode> clickModes = new ConcurrentHashMap<>();

  public enum ClickMode {
    ONCE,
    REPEAT,
    HOLD,
    STOP
  }

  public LeftClickCommand(
      FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
    this.plugin = plugin;
    this.manager = manager;
    this.pathfinding = pathfinding;
  }

  @Override
  public String getName() {
    return "left-click";
  }

  @Override
  public String getUsage() {
    return "<bot> [--once|--repeat|--hold|--stop]";
  }

  @Override
  public String getDescription() {
    return "Bot left-clicks (breaks blocks and attacks entities). Default: --once";
  }

  @Override
  public String getPermission() {
    return Perm.LEFT_CLICK;
  }

  @Override
  public boolean canUse(CommandSender sender) {
    return Perm.has(sender, Perm.LEFT_CLICK);
  }

  @Override
  public boolean execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
      sender.sendMessage(Lang.get("left-click-usage"));
      return true;
    }

    if (args[0].equalsIgnoreCase("--stop") && args.length == 1) {
      stopAll();
      sender.sendMessage(Lang.get("left-click-stopped-all"));
      return true;
    }

    String botName = args[0];
    FakePlayer fp = manager.getByName(botName);
    if (fp == null) {
      sender.sendMessage(Lang.get("left-click-not-found", "name", botName));
      return true;
    }

    if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
      if (!BotAccess.canAdminister(player, fp)) {
        sender.sendMessage(Lang.get("no-permission"));
        return true;
      }
    }

    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) {
      sender.sendMessage(Lang.get("left-click-bot-offline", "name", fp.getDisplayName()));
      return true;
    }

    ClickMode mode = ClickMode.ONCE;
    if (args.length >= 2) {
      String action = args[1].toLowerCase(Locale.ROOT);
      switch (action) {
        case "--once" -> mode = ClickMode.ONCE;
        case "--repeat" -> mode = ClickMode.REPEAT;
        case "--hold" -> mode = ClickMode.HOLD;
        case "--stop" -> {
          cleanupBot(fp.getUuid());
          sender.sendMessage(Lang.get("left-click-stopped", "name", fp.getDisplayName()));
          return true;
        }
        default -> {
          sender.sendMessage(Lang.get("left-click-usage"));
          return true;
        }
      }
    }

    cancelAll(fp.getUuid());

    Object target = null;
    BlockPos blockTarget = null;
    Entity entityTarget = null;

    if (sender instanceof Player player) {
      Block playerTarget = player.getTargetBlockExact((int) Math.ceil(CLICK_REACH));
      if (playerTarget != null && !playerTarget.getType().isAir()) {
        blockTarget = new BlockPos(playerTarget.getX(), playerTarget.getY(), playerTarget.getZ());
        target = playerTarget;
      } else {
        entityTarget = rayTraceEntity(player);
        if (entityTarget != null) {
          target = entityTarget;
        }
      }
    }

    if (target == null) {
      target = rayTraceTarget(bot);
      if (target instanceof Block b) {
        blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
      }
    }

    final ClickMode finalMode = mode;
    if (target != null) {
      Location targetLoc = getTargetLocation(bot, target);
      if (targetLoc != null) {
        double dist = bot.getLocation().distance(targetLoc);
        if (dist <= CLICK_REACH) {
          lockAndStartClicking(fp, finalMode, target, blockTarget, entityTarget);
          String msgKey = switch (finalMode) {
            case ONCE -> "left-click-started-once";
            case REPEAT -> "left-click-started-repeat";
            case HOLD -> "left-click-started-hold";
            default -> "left-click-started";
          };
          sender.sendMessage(Lang.get(msgKey, "name", fp.getDisplayName()));
          return true;
        } else {
          Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
          if (standLoc != null) {
            final Object finalTarget = target;
            final BlockPos finalBlockTarget = blockTarget;
            final Entity finalEntityTarget = entityTarget;
            startNavigation(fp, standLoc, () ->
                lockAndStartClicking(fp, finalMode, finalTarget, finalBlockTarget, finalEntityTarget));
            sender.sendMessage(Lang.get("left-click-walking", "name", fp.getDisplayName()));
            return true;
          } else {
            sender.sendMessage(Lang.get("left-click-no-path", "name", fp.getDisplayName()));
            return true;
          }
        }
      }
    }

    lockAndStartClicking(fp, finalMode, null, null, null);
    sender.sendMessage(Lang.get("left-click-started", "name", fp.getDisplayName()));
    return true;
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String[] args) {
    if (!canUse(sender)) return List.of();

    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      if ("--stop".startsWith(prefix)) out.add("--stop");
      for (FakePlayer fp : manager.getActivePlayers()) {
        if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
      }
      return out;
    }

    if (args.length == 2
        && !args[0].equalsIgnoreCase("--stop")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      if ("--once".startsWith(prefix)) out.add("--once");
      if ("--repeat".startsWith(prefix)) out.add("--repeat");
      if ("--hold".startsWith(prefix)) out.add("--hold");
      if ("--stop".startsWith(prefix)) out.add("--stop");
      return out;
    }

    return List.of();
  }

  private void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
    BotPathfinder.PathOptions baseOpts = PathfindingService.resolvePathOptions(fp);
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
            0.5,
            0.0,
            Integer.MAX_VALUE,
            onArrive,
            null,
            null,
            null,
            opts));
  }

  private void lockAndStartClicking(
      FakePlayer fp, ClickMode mode, Object target, BlockPos blockTarget, Entity entityTarget) {
    FppApiImpl.fireTaskEvent(fp, "left-click", FppBotTaskEvent.Action.START);
    UUID uuid = fp.getUuid();
    Player bot = fp.getPlayer();
    if (bot == null) return;

    if (target != null) {
      Location faceLoc = faceTowardTarget(bot.getLocation(), target);
      bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
      NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
    }
    NmsPlayerSpawner.setMovementForward(bot, 0f);
    bot.setSprinting(false);

    Location actualLoc = bot.getLocation().clone();
    manager.lockForAction(uuid, actualLoc, false);

    ClickState state = new ClickState();
    state.target = target;
    state.blockTarget = blockTarget;
    state.entityTarget = entityTarget;
    state.mode = mode;
    state.holding = false;
    state.progress = 0;
    state.dynamicTarget = (target != null);
    clickStates.put(uuid, state);
    clickModes.put(uuid, mode);

    final int[] cooldown = {0};
    Player botPlayer = fp.getPlayer();

    int taskId =
        FppScheduler.runSyncRepeatingWithId(
            plugin,
            botPlayer,
            () -> {
              Player b = fp.getPlayer();
              if (b == null || !b.isOnline()) {
                stopClicking(uuid);
                return;
              }

              ServerPlayer nms = ((CraftPlayer) b).getHandle();
              nms.resetLastActionTime();

              if (cooldown[0] > 0) {
                cooldown[0]--;
                return;
              }

              boolean acted = tryBreakBlock(nms, state);

              if (!acted && state.entityTarget != null) {
                tryAttackEntity(nms, state.entityTarget);
                acted = true;
              }

              if (acted) {
                if (mode == ClickMode.ONCE) {
                  stopClicking(uuid);
                  return;
                }
                if (mode == ClickMode.REPEAT) {
                  cooldown[0] = CLICK_COOLDOWN;
                }
              }

              if (mode == ClickMode.HOLD && !state.holding) {
                state.holding = true;
                if (state.blockTarget != null) {
                  startBreakBlock(nms, state.blockTarget);
                }
              }
            },
            0L,
            1L);

    clickTasks.put(uuid, taskId);
  }

  private boolean tryBreakBlock(ServerPlayer nms, ClickState state) {
    BlockPos pos = state.blockTarget;
    
    if (state.dynamicTarget) {
      BlockPos currentTarget = rayTraceBlockTarget(nms);
      if (currentTarget != null) {
        if (pos == null || !pos.equals(currentTarget)) {
          state.progress = 0;
          state.blockTarget = currentTarget;
        }
        pos = currentTarget;
      } else if (pos != null) {
        BlockState blockState = nms.level().getBlockState(pos);
        if (blockState.isAir()) {
          state.progress = 0;
          state.blockTarget = null;
        }
      }

      if (pos == null) {
        Entity nearbyEntity = findEntityInRange(nms);
        if (nearbyEntity != null && nearbyEntity != nms.getBukkitEntity()) {
          state.entityTarget = nearbyEntity;
        }
      }
    }
    
    if (state.entityTarget != null) {
      tryAttackEntity(nms, state.entityTarget);
      return true;
    }
    
    if (pos == null) return false;
    
    BlockState blockState = nms.level().getBlockState(pos);
    if (blockState.isAir()) {
      state.progress = 0;
      state.blockTarget = null;
      return false;
    }

    if (nms.blockActionRestricted(nms.level(), pos, nms.gameMode.getGameModeForPlayer())) {
      return false;
    }

    if (state.progress == 0) {
      blockState.attack(nms.level(), pos, nms);
    }

    float speed = blockState.getDestroyProgress(nms, nms.level(), pos);
    if (speed >= 1.0F) {
      nms.swing(InteractionHand.MAIN_HAND);
      NmsPlayerSpawner.handleBlockBreakAction(nms, pos,
          ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
          Direction.DOWN, nms.level().getMaxY(), -1);
      NmsPlayerSpawner.destroyBlockProgress(nms, -1, pos, -1);
      nms.gameMode.destroyBlock(pos);
      state.progress = 0;
      state.blockTarget = null;
      return true;
    }

    state.progress += speed;
    if (state.progress >= 1.0F) {
      nms.swing(InteractionHand.MAIN_HAND);
      NmsPlayerSpawner.handleBlockBreakAction(nms, pos,
          ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
          Direction.DOWN, nms.level().getMaxY(), -1);
      NmsPlayerSpawner.destroyBlockProgress(nms, -1, pos, -1);
      nms.gameMode.destroyBlock(pos);
      state.progress = 0;
      state.blockTarget = null;
      return true;
    }

    NmsPlayerSpawner.destroyBlockProgress(nms, -1, pos, (int) (state.progress * 10));
    Direction side = Direction.DOWN;
    NmsPlayerSpawner.handleBlockBreakAction(nms, pos,
        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
        side, nms.level().getMaxY(), -1);
    nms.swing(InteractionHand.MAIN_HAND);
    return true;
  }

  @Nullable
  private Entity findEntityInRange(ServerPlayer nms) {
    try {
      Vec3 eyePos = nms.getEyePosition(1.0F);
      Vec3 lookDir = nms.getLookAngle();
      Vec3 endPos = eyePos.add(lookDir.scale(CLICK_REACH));

      org.bukkit.World world = nms.getBukkitEntity().getWorld();
      org.bukkit.Location eye = new org.bukkit.Location(world, eyePos.x, eyePos.y, eyePos.z);
      org.bukkit.util.Vector dir = new org.bukkit.util.Vector(lookDir.x, lookDir.y, lookDir.z);

      org.bukkit.util.RayTraceResult result = world.rayTrace(
          eye, dir, CLICK_REACH,
          org.bukkit.FluidCollisionMode.NEVER,
          true, 0.0,
          e -> e != null && e.isValid() && !e.isDead() && e != nms.getBukkitEntity());

      if (result != null && result.getHitEntity() != null) {
        return result.getHitEntity();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private void startBreakBlock(ServerPlayer nms, BlockPos pos) {
    Direction side = Direction.DOWN;
    NmsPlayerSpawner.handleBlockBreakAction(nms, pos,
        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
        side, nms.level().getMaxY(), -1);
    nms.swing(InteractionHand.MAIN_HAND);
  }

  @Nullable
  private BlockPos rayTraceBlockTarget(ServerPlayer nms) {
    try {
      Vec3 eyePos = nms.getEyePosition(1.0F);
      Vec3 lookDir = nms.getLookAngle();
      Vec3 endPos = eyePos.add(lookDir.scale(CLICK_REACH));
      BlockHitResult result = nms.level().clip(
          new net.minecraft.world.level.ClipContext(
              eyePos, endPos,
              net.minecraft.world.level.ClipContext.Block.OUTLINE,
              net.minecraft.world.level.ClipContext.Fluid.NONE,
              nms));
      if (result.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
        return result.getBlockPos();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private void tryAttackEntity(ServerPlayer nms, Entity entity) {
    if (entity instanceof CraftPlayer cp) {
      nms.attack(cp.getHandle());
    } else if (entity instanceof org.bukkit.craftbukkit.entity.CraftEntity ce) {
      nms.attack(ce.getHandle());
    }
  }

  private void cancelAll(UUID botUuid) {
    pathfinding.cancel(botUuid);
    stopClicking(botUuid);
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

  public void stopClicking(UUID botUuid) {
    stopClicking(botUuid, true);
  }

  public void stopClicking(UUID botUuid, boolean clearState) {
    FakePlayer fp = manager.getByUuid(botUuid);
    if (fp != null) {
      FppApiImpl.fireTaskEvent(fp, "left-click", FppBotTaskEvent.Action.STOP);
      Player bot = fp.getPlayer();
      if (bot != null && bot.isOnline()) {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();
        ClickState state = clickStates.get(botUuid);
        if (state != null && state.blockTarget != null) {
          NmsPlayerSpawner.destroyBlockProgress(nms, -1, state.blockTarget, -1);
          NmsPlayerSpawner.handleBlockBreakAction(nms, state.blockTarget,
              ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
              Direction.DOWN, nms.level().getMaxY(), -1);
        }
      }
    }
    Integer taskId = clickTasks.remove(botUuid);
    if (taskId != null) FppScheduler.cancelTask(taskId);
    manager.unlockAction(botUuid);
    if (clearState) {
      clickStates.remove(botUuid);
      clickModes.remove(botUuid);
    }
  }

  public void stopAll() {
    pathfinding.cancelAll(PathfindingService.Owner.MINE);
    new java.util.HashSet<>(clickTasks.keySet()).forEach(this::cleanupBot);
  }

  public void cleanupBot(UUID botUuid) {
    cancelAll(botUuid);
  }

  public boolean isClicking(UUID botUuid) {
    return clickTasks.containsKey(botUuid);
  }

  public void resumeClicking(FakePlayer fp) {
    ClickMode mode = clickModes.get(fp.getUuid());
    if (mode == null) return;
    Player bot = fp.getPlayer();
    if (bot == null || !bot.isOnline()) return;
    ClickState state = clickStates.get(fp.getUuid());
    Object target = state != null ? state.target : rayTraceTarget(bot);
    Integer taskId = clickTasks.get(fp.getUuid());
    if (taskId != null) return;

    if (target != null) {
      Location faceLoc = faceTowardTarget(bot.getLocation(), target);
      bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
      NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
    }
    NmsPlayerSpawner.setMovementForward(bot, 0f);
    bot.setSprinting(false);

    Location actualLoc = bot.getLocation().clone();
    manager.lockForAction(fp.getUuid(), actualLoc, false);

    if (state == null) {
      state = new ClickState();
      state.target = target;
      state.mode = mode;
      state.holding = false;
      state.progress = 0;
      state.dynamicTarget = (target != null);
      if (target instanceof Block b) {
        state.blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
        state.entityTarget = null;
      } else if (target instanceof Entity e) {
        state.entityTarget = e;
        state.blockTarget = null;
      }
      clickStates.put(fp.getUuid(), state);
    }
    final ClickState finalState = state;
    final ClickMode finalMode = mode;

    final int[] cooldown = {0};
    int newTask = FppScheduler.runSyncRepeatingWithId(
        plugin,
        bot,
        () -> {
          Player b = fp.getPlayer();
          if (b == null || !b.isOnline()) {
            stopClicking(fp.getUuid());
            return;
          }
          ServerPlayer nms = ((CraftPlayer) b).getHandle();
          nms.resetLastActionTime();
          if (cooldown[0] > 0) {
            cooldown[0]--;
            return;
          }
          boolean acted = tryBreakBlock(nms, finalState);

          if (!acted && finalState.entityTarget != null) {
            tryAttackEntity(nms, finalState.entityTarget);
            acted = true;
          }
          if (acted) {
            if (finalMode == ClickMode.ONCE) {
              stopClicking(fp.getUuid());
              return;
            }
            if (finalMode == ClickMode.REPEAT) {
              cooldown[0] = CLICK_COOLDOWN;
            }
          }
          if (finalMode == ClickMode.HOLD && !finalState.holding) {
            finalState.holding = true;
            if (finalState.blockTarget != null) {
              startBreakBlock(nms, finalState.blockTarget);
            }
          }
        },
        0L,
        1L);
    clickTasks.put(fp.getUuid(), newTask);
  }

  @Nullable
  private Object rayTraceTarget(Player bot) {
    try {
      Location eye = bot.getEyeLocation();
      org.bukkit.util.RayTraceResult result = bot.getWorld().rayTraceBlocks(
          eye,
          eye.getDirection(),
          CLICK_REACH,
          org.bukkit.FluidCollisionMode.NEVER,
          false
      );
      if (result != null && result.getHitBlock() != null) {
        return result.getHitBlock();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  @Nullable
  private Entity rayTraceEntity(Player player) {
    List<Entity> nearby = player.getNearbyEntities(CLICK_REACH, CLICK_REACH, CLICK_REACH);
    for (Entity ent : nearby) {
      if (ent instanceof org.bukkit.entity.LivingEntity) {
        Location eye = player.getEyeLocation();
        Location entEye = ent.getLocation().add(0, ent.getHeight() / 2, 0);
        org.bukkit.util.Vector dir = eye.getDirection();
        org.bukkit.util.Vector toEnt = entEye.toVector().subtract(eye.toVector());
        double angle = dir.angle(toEnt);
        if (angle < 0.5) {
          return ent;
        }
      }
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
              if (dist <= CLICK_REACH - 1.5) {
                return faceTowardTarget(loc, targetLoc);
              }
            }
          }
        }
      }
    }
    return null;
  }

  private static final class ClickState {
    Object target;
    BlockPos blockTarget;
    Entity entityTarget;
    ClickMode mode;
    boolean holding;
    float progress;
    boolean dynamicTarget;
  }
}
