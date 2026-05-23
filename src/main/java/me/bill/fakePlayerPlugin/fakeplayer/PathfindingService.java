package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PathfindingService {

  public enum Owner {
    MOVE,
    MINE,
    PLACE,
    USE,
    ATTACK,
    FOLLOW,
    SLEEP,
    SYSTEM
  }

  public record NavigationRequest(
      @NotNull Owner owner,
      @NotNull Supplier<@Nullable Location> destinationSupplier,
      double arrivalDistance,
      double recalcDistance,
      int maxNullPathRecalculations,
      @Nullable Runnable onArrive,
      @Nullable Runnable onCancel,
      @Nullable Runnable onPathFailure,
      @Nullable Location lockOnArrival,
      @Nullable BotPathfinder.PathOptions overrideOpts) {

    /** Backward-compatible 9-arg constructor (no overrideOpts). */
    public NavigationRequest(
        @NotNull Owner owner,
        @NotNull Supplier<@Nullable Location> destinationSupplier,
        double arrivalDistance,
        double recalcDistance,
        int maxNullPathRecalculations,
        @Nullable Runnable onArrive,
        @Nullable Runnable onCancel,
        @Nullable Runnable onPathFailure,
        @Nullable Location lockOnArrival) {
      this(
          owner,
          destinationSupplier,
          arrivalDistance,
          recalcDistance,
          maxNullPathRecalculations,
          onArrive,
          onCancel,
          onPathFailure,
          lockOnArrival,
          null);
    }

    /** Backward-compatible 8-arg constructor (no lockOnArrival, no overrideOpts). */
    public NavigationRequest(
        @NotNull Owner owner,
        @NotNull Supplier<@Nullable Location> destinationSupplier,
        double arrivalDistance,
        double recalcDistance,
        int maxNullPathRecalculations,
        @Nullable Runnable onArrive,
        @Nullable Runnable onCancel,
        @Nullable Runnable onPathFailure) {
      this(
          owner,
          destinationSupplier,
          arrivalDistance,
          recalcDistance,
          maxNullPathRecalculations,
          onArrive,
          onCancel,
          onPathFailure,
          null,
          null);
    }

    public NavigationRequest {
      if (owner == null) throw new IllegalArgumentException("owner");
      if (destinationSupplier == null) throw new IllegalArgumentException("destinationSupplier");
      if (arrivalDistance <= 0) throw new IllegalArgumentException("arrivalDistance must be > 0");
      if (recalcDistance < 0) throw new IllegalArgumentException("recalcDistance must be >= 0");
      if (maxNullPathRecalculations <= 0) maxNullPathRecalculations = Integer.MAX_VALUE;
    }
  }

  private record Session(Owner owner, int taskId) {}

  private final FakePlayerPlugin plugin;
  private final FakePlayerManager manager;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

  private Material cachedPlaceMaterial = null;
  private String cachedPlaceMaterialName = null;

  public PathfindingService(FakePlayerPlugin plugin, FakePlayerManager manager) {
    this.plugin = plugin;
    this.manager = manager;

    me.bill.fakePlayerPlugin.util.AttributionManager.quickAuthorCheck();
  }

  public boolean isNavigating(@NotNull UUID botUuid) {
    return sessions.containsKey(botUuid);
  }

  public boolean isNavigating(@NotNull UUID botUuid, @NotNull Owner owner) {
    Session session = sessions.get(botUuid);
    return session != null && session.owner() == owner;
  }

  @Nullable
  public Owner getOwner(@NotNull UUID botUuid) {
    Session session = sessions.get(botUuid);
    return session != null ? session.owner() : null;
  }

  public void cancel(@NotNull UUID botUuid) {
    Session session = sessions.remove(botUuid);
    if (session != null) {
      FppScheduler.cancelTask(session.taskId());
    }
    manager.clearNavJump(botUuid);
    manager.unlockNavigation(botUuid);
    Player bot = Bukkit.getPlayer(botUuid);
    if (bot != null && bot.isOnline()) {
      NmsPlayerSpawner.setMovementForward(bot, 0f);
      NmsPlayerSpawner.setJumping(bot, false);
      bot.setSprinting(false);
    }
  }

  public void cancelAll() {
    for (UUID uuid : List.copyOf(sessions.keySet())) {
      cancel(uuid);
    }
  }

  public void cancelAll(@NotNull Owner owner) {
    for (Map.Entry<UUID, Session> entry : List.copyOf(sessions.entrySet())) {
      if (entry.getValue().owner() == owner) {
        cancel(entry.getKey());
      }
    }
  }

  public void navigate(@NotNull FakePlayer fp, @NotNull NavigationRequest request) {
    UUID botUuid = fp.getUuid();
    cancel(botUuid);
    manager.lockForNavigation(botUuid);
    manager.clearNavJump(botUuid);

    Player initialBot = fp.getPlayer();
    if (initialBot == null || !initialBot.isOnline()) {
      manager.unlockNavigation(botUuid);
      if (request.onCancel() != null) request.onCancel().run();
      return;
    }

    Location initialDest = safeDestination(request.destinationSupplier().get());
    if (initialDest == null
        || initialDest.getWorld() == null
        || !initialDest.getWorld().equals(initialBot.getWorld())) {
      manager.unlockNavigation(botUuid);
      if (request.onCancel() != null) request.onCancel().run();
      return;
    }

    primeInitialMovement(initialBot, initialDest, request.arrivalDistance());

    @SuppressWarnings("unchecked")
    final List<BotPathfinder.Move>[] pathRef = (List<BotPathfinder.Move>[]) new List<?>[] {null};
    final int[] wpIdx = {0};
    final Location[] lastCalc = {initialDest.clone()};
    final int[] recalcIn = {0};
    final int[] stuckFor = {0};
    final int[] nullPathRecalcs = {0};
    final double[] prevX = {initialBot.getLocation().getX()};
    final double[] prevY = {initialBot.getLocation().getY()};
    final double[] prevZ = {initialBot.getLocation().getZ()};

    final boolean[] isBreaking = {false};
    final int[] breakLeft = {0};
    final Location[] breakLoc = {null};

    final boolean[] isPlacing = {false};
    final int[] placeLeft = {0};

    final int[] taskIdRef = {-1};

    final org.bukkit.Location[] pendingCloseLoc = {null};
    final boolean[] pendingCloseWasOpen = {false};
    final int[] pendingCloseTick = {0};

    Runnable tick =
        new Runnable() {
          @Override
          public void run() {
            Player bot = fp.getPlayer();
            if (bot == null || !bot.isOnline()) {
              cleanup(null, false, false);
              return;
            }

            Location dest = safeDestination(request.destinationSupplier().get());
            if (dest == null
                || dest.getWorld() == null
                || !dest.getWorld().equals(bot.getWorld())) {
              cleanup(bot, false, false);
              return;
            }

            Location botLoc = bot.getLocation();

            if (pendingCloseLoc[0] != null) {
              org.bukkit.Location doorCenter = pendingCloseLoc[0].clone().add(0.5, 0.5, 0.5);
              boolean passed =
                  doorCenter.getWorld().equals(botLoc.getWorld())
                      && xzDistRaw(botLoc.getX(), botLoc.getZ(), doorCenter.getX(), doorCenter.getZ()) > 1.2;
              if (passed || ++pendingCloseTick[0] > 40) {
                org.bukkit.block.Block block = pendingCloseLoc[0].getBlock();
                org.bukkit.block.data.BlockData data = block.getBlockData();
                if (!pendingCloseWasOpen[0]) {
                  if (data instanceof org.bukkit.block.data.type.Door door) {
                    Block target = door.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? block : block.getRelative(0, -1, 0);
                    if (target.getBlockData() instanceof org.bukkit.block.data.type.Door targetDoor && targetDoor.isOpen()) {
                      targetDoor.setOpen(false);
                      target.setBlockData(targetDoor);
                      playDoorSound(target, false);
                    }
                  } else if (data instanceof org.bukkit.block.data.type.Gate gate && gate.isOpen()) {
                    gate.setOpen(false);
                    block.setBlockData(gate);
                    playDoorSound(block, false);
                  } else if (data instanceof org.bukkit.block.data.type.TrapDoor trap && trap.isOpen()) {
                    trap.setOpen(false);
                    block.setBlockData(trap);
                    playDoorSound(block, false);
                  }
                }
                pendingCloseLoc[0] = null;
              }
            }

            double arrivalSq = request.arrivalDistance() * request.arrivalDistance();
            double dx0 = botLoc.getX() - dest.getX();
            double dz0 = botLoc.getZ() - dest.getZ();
            double distToTargetSq = dx0 * dx0 + dz0 * dz0;
            if (distToTargetSq <= arrivalSq && request.owner() != Owner.FOLLOW) {
              cleanup(bot, true, false);
              return;
            }

            double distToTarget = Math.sqrt(distToTargetSq);
            boolean closeFollow = request.owner() == Owner.FOLLOW && distToTargetSq <= arrivalSq;

            boolean targetMoved =
                request.recalcDistance() > 0
                    && lastCalc[0] != null
                    && lastCalc[0].distanceSquared(dest)
                        > request.recalcDistance() * request.recalcDistance();
            boolean pathExhausted = (pathRef[0] == null || wpIdx[0] >= pathRef[0].size());
            boolean heartbeat = (--recalcIn[0] <= 0);

            if (!closeFollow && (targetMoved || pathExhausted || heartbeat)) {
              recalcIn[0] = Config.pathfindingRecalcInterval();

              if (lastCalc[0] == null) {
                lastCalc[0] = dest.clone();
              } else {
                lastCalc[0].setX(dest.getX());
                lastCalc[0].setY(dest.getY());
                lastCalc[0].setZ(dest.getZ());
                lastCalc[0].setWorld(dest.getWorld());
              }
              isBreaking[0] = false;
              breakLoc[0] = null;
              isPlacing[0] = false;

              BotPathfinder.PathOptions opts =
                  resolvePathOptions(fp, request.overrideOpts());

              int destX = dest.getBlockX();
              int destY = dest.getBlockY();
              int destZ = dest.getBlockZ();
              if (request.owner() == Owner.FOLLOW && (bot.isInWater() || bot.isInLava()) && destY < botLoc.getBlockY()) {
                destY = botLoc.getBlockY();
              }

              List<BotPathfinder.Move> newPath =
                  BotPathfinder.findPathMoves(
                      botLoc.getWorld(),
                      botLoc.getBlockX(),
                      botLoc.getBlockY(),
                      botLoc.getBlockZ(),
                      destX,
                      destY,
                      destZ,
                      opts);

              if (newPath == null) {
                if (++nullPathRecalcs[0] >= request.maxNullPathRecalculations()) {
                  cleanup(bot, false, true);
                  return;
                }
              } else {
                nullPathRecalcs[0] = 0;
              }

              pathRef[0] = newPath;
              wpIdx[0] = (newPath != null && newPath.size() > 1) ? 1 : 0;
              stuckFor[0] = 0;
            }

            if (closeFollow) {
              NmsPlayerSpawner.setMovementForward(bot, 0f);
              bot.setSprinting(false);
              float yaw = (float) Math.toDegrees(Math.atan2(-(dest.getX() - botLoc.getX()), dest.getZ() - botLoc.getZ()));
              bot.setRotation(yaw, 0f);
              NmsPlayerSpawner.setHeadYaw(bot, yaw);
              prevX[0] = botLoc.getX();
              prevY[0] = botLoc.getY();
              prevZ[0] = botLoc.getZ();
            } else {
              List<BotPathfinder.Move> path = pathRef[0];
              if (path == null || path.isEmpty() || wpIdx[0] >= path.size()) {
                Location walkDest = dest;
                if (request.owner() == Owner.FOLLOW && (bot.isInWater() || bot.isInLava()) && dest.getY() < botLoc.getY()) {
                  walkDest = dest.clone();
                  walkDest.setY(botLoc.getY());
                }
                walkToward(bot, walkDest, distToTarget);
                prevX[0] = botLoc.getX();
                prevY[0] = botLoc.getY();
                prevZ[0] = botLoc.getZ();
                return;
              }

              BotPathfinder.Move wp = path.get(wpIdx[0]);
              double wpCX = wp.x() + 0.5;
              double wpCZ = wp.z() + 0.5;

              if (wp.type() == BotPathfinder.MoveType.BREAK) {
                if (!isBreaking[0]) {
                  Location breakTarget = findBreakTarget(botLoc, wp);
                  if (breakTarget != null) {
                    isBreaking[0] = true;
                    breakLeft[0] = Config.pathfindingBreakTicks();
                    breakLoc[0] = breakTarget;
                  } else {
                    recalcIn[0] = 0;
                    return;
                  }
                }
                stuckFor[0] = 0;
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                bot.setSprinting(false);
                Location blk = breakLoc[0];
                if (blk != null) {
                  double bdx = blk.getX() - botLoc.getX();
                  double bdz = blk.getZ() - botLoc.getZ();
                  double bdy = blk.getY() - botLoc.getY();
                  float bYaw = (float) Math.toDegrees(Math.atan2(-bdx, bdz));
                  float bPitch =
                      (float) -Math.toDegrees(Math.atan2(bdy, Math.sqrt(bdx * bdx + bdz * bdz)));
                  bot.setRotation(bYaw, bPitch);
                  NmsPlayerSpawner.setHeadYaw(bot, bYaw);
                }
                if (--breakLeft[0] <= 0) {
                  if (breakLoc[0] != null) {
                    breakLoc[0].getBlock().breakNaturally();
                    breakLoc[0] = null;
                  }
                  isBreaking[0] = false;
                  recalcIn[0] = 0;
                  stuckFor[0] = 0;
                }
                prevX[0] = botLoc.getX();
                prevY[0] = botLoc.getY();
                prevZ[0] = botLoc.getZ();
                return;
              }

              if (wp.type() == BotPathfinder.MoveType.PLACE) {
                if (!isPlacing[0]) {
                  isPlacing[0] = true;
                  placeLeft[0] = Config.pathfindingPlaceTicks();
                }
                stuckFor[0] = 0;
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                bot.setSprinting(false);
                double pdx = (wp.x() + 0.5) - botLoc.getX();
                double pdz = (wp.z() + 0.5) - botLoc.getZ();
                float pYaw = (float) Math.toDegrees(Math.atan2(-pdx, pdz));
                bot.setRotation(pYaw, 70f);
                NmsPlayerSpawner.setHeadYaw(bot, pYaw);
                if (--placeLeft[0] <= 0) {
                  Block gapBlock = bot.getWorld().getBlockAt(wp.x(), wp.y() - 1, wp.z());
                  if (gapBlock.isPassable()) {
                    gapBlock.setType(resolvePlaceMaterial());
                  }
                  isPlacing[0] = false;
                  recalcIn[0] = 0;
                  stuckFor[0] = 0;
                }
                prevX[0] = botLoc.getX();
                prevY[0] = botLoc.getY();
                prevZ[0] = botLoc.getZ();
                return;
              }

              if (wp.type() == BotPathfinder.MoveType.PILLAR) {
                stuckFor[0] = 0;
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                bot.setSprinting(false);

                manager.requestNavJump(botUuid);

                if (botLoc.getY() - botLoc.getBlockY() > 0.4) {
                  Block below =
                      bot.getWorld()
                          .getBlockAt(botLoc.getBlockX(), botLoc.getBlockY() - 1, botLoc.getBlockZ());
                  if (below.isPassable()) {
                    below.setType(resolvePlaceMaterial());
                  }

                  wpIdx[0]++;
                  recalcIn[0] = 0;
                }
                prevX[0] = botLoc.getX();
                prevY[0] = botLoc.getY();
                prevZ[0] = botLoc.getZ();
                return;
              }

              if (wp.type() == BotPathfinder.MoveType.SWIM) {
                stuckFor[0] = 0;
                double sdx = wpCX - botLoc.getX();
                double sdz = wpCZ - botLoc.getZ();
                float sYaw = (float) Math.toDegrees(Math.atan2(-sdx, sdz));
                bot.setRotation(sYaw, wp.y() > botLoc.getBlockY() ? -30f : 20f);
                NmsPlayerSpawner.setHeadYaw(bot, sYaw);
                NmsPlayerSpawner.setMovementForward(bot, 1.0f);
                bot.setSprinting(false);

                if (bot.isInWater() || bot.isInLava()) {
                  NmsPlayerSpawner.setJumping(bot, true);
                }
                prevX[0] = botLoc.getX();
                prevY[0] = botLoc.getY();
                prevZ[0] = botLoc.getZ();

                double swimDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
                if (swimDist < 0.8 && Math.abs(botLoc.getY() - wp.y()) < 1.5) {
                  wpIdx[0]++;
                }
                return;
              }

              if (wp.type() == BotPathfinder.MoveType.OPEN) {
                stuckFor[0] = 0;
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                bot.setSprinting(false);
                double odx = (wp.x() + 0.5) - botLoc.getX();
                double odz = (wp.z() + 0.5) - botLoc.getZ();
                float oYaw = (float) Math.toDegrees(Math.atan2(-odx, odz));
                bot.setRotation(oYaw, 0f);
                NmsPlayerSpawner.setHeadYaw(bot, oYaw);
                Block openBlock = bot.getWorld().getBlockAt(wp.x(), wp.y(), wp.z());
                org.bukkit.block.data.BlockData data = openBlock.getBlockData();
                boolean wasOpen = false;
                Block stateBlock = openBlock;
                if (data instanceof org.bukkit.block.data.type.Door door) {
                  Block target = door.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? openBlock : openBlock.getRelative(0, -1, 0);
                  if (target.getBlockData() instanceof org.bukkit.block.data.type.Door targetDoor) {
                    wasOpen = targetDoor.isOpen();
                    stateBlock = target;
                    if (!wasOpen) {
                      targetDoor.setOpen(true);
                      target.setBlockData(targetDoor);
                      playDoorSound(target, true);
                    }
                  }
                } else if (data instanceof org.bukkit.block.data.type.Gate gate) {
                  wasOpen = gate.isOpen();
                  if (!wasOpen) {
                    gate.setOpen(true);
                    openBlock.setBlockData(gate);
                    playDoorSound(openBlock, true);
                  }
                } else if (data instanceof org.bukkit.block.data.type.TrapDoor trap) {
                  wasOpen = trap.isOpen();
                  if (!wasOpen) {
                    trap.setOpen(true);
                    openBlock.setBlockData(trap);
                    playDoorSound(openBlock, true);
                  }
                }
                pendingCloseLoc[0] = stateBlock.getLocation();
                pendingCloseWasOpen[0] = wasOpen;
                pendingCloseTick[0] = 0;
                wpIdx[0]++;
                recalcIn[0] = 0;
                prevX[0] = botLoc.getX();
                prevY[0] = botLoc.getY();
                prevZ[0] = botLoc.getZ();
                return;
              }

              if (wp.type() == BotPathfinder.MoveType.CLIMB) {
                stuckFor[0] = 0;
                double cdx = (wp.x() + 0.5) - botLoc.getX();
                double cdz = (wp.z() + 0.5) - botLoc.getZ();
                double cDistSq = cdx * cdx + cdz * cdz;

                if (cDistSq > 0.09) {
                  float cYaw = (float) Math.toDegrees(Math.atan2(-cdx, cdz));
                  bot.setRotation(cYaw, 0f);
                  NmsPlayerSpawner.setHeadYaw(bot, cYaw);
                  NmsPlayerSpawner.setMovementForward(bot, 0.5f);
                } else {
                  NmsPlayerSpawner.setMovementForward(bot, 0f);
                  if (wp.y() > botLoc.getBlockY()) {
                    NmsPlayerSpawner.setJumping(bot, true);
                  }
                }

                bot.setSprinting(false);
                prevX[0] = botLoc.getX();
                prevY[0] = botLoc.getY();
                prevZ[0] = botLoc.getZ();
                if (Math.abs(botLoc.getY() - wp.y()) < 0.8 && cDistSq < 0.25) {
                  wpIdx[0]++;
                }
                return;
              }

              double wpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
              boolean wpYClose = Math.abs(botLoc.getY() - wp.y()) < 1.2;
              double arrivalDist = wp.type() == BotPathfinder.MoveType.PARKOUR
                  ? 1.1
                  : Config.pathfindingWaypointArrivalDistance();
              if (wpXZDist < arrivalDist && wpYClose) {
                wpIdx[0]++;
                if (wpIdx[0] >= path.size()) {
                  recalcIn[0] = 0;
                  return;
                }
                wp = path.get(wpIdx[0]);
                wpCX = wp.x() + 0.5;
                wpCZ = wp.z() + 0.5;
                wpXZDist = xzDistRaw(botLoc.getX(), botLoc.getZ(), wpCX, wpCZ);
              }

              double dx = wpCX - botLoc.getX();
              double dz = wpCZ - botLoc.getZ();
              float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
              bot.setRotation(yaw, 0f);
              NmsPlayerSpawner.setHeadYaw(bot, yaw);

              boolean isParkour = wp.type() == BotPathfinder.MoveType.PARKOUR;
              bot.setSprinting(
                  distToTarget > Config.pathfindingSprintDistance() || isParkour);
              NmsPlayerSpawner.setMovementForward(bot, 1.0f);

              if (!bot.isInWater() && !bot.isInLava()) {
                if (wp.y() > botLoc.getBlockY()) {
                  manager.requestNavJump(botUuid);
                } else if (isParkour && wp.y() >= botLoc.getBlockY()) {
                  manager.requestNavJump(botUuid);
                }
              }

              double moved = xzDistRaw(botLoc.getX(), botLoc.getZ(), prevX[0], prevZ[0]);
              double movedY = Math.abs(botLoc.getY() - prevY[0]);
              boolean makingProgress = moved >= Config.pathfindingStuckThreshold() || movedY >= 0.08;
              if (!makingProgress) {
                if (++stuckFor[0] >= Config.pathfindingStuckTicks()) {
                  if (!bot.isInWater() && !bot.isInLava()) {
                    manager.requestNavJump(botUuid);
                  }
                  recalcIn[0] = 0;
                  stuckFor[0] = 0;
                }
              } else {
                stuckFor[0] = 0;
              }

              prevX[0] = botLoc.getX();
              prevY[0] = botLoc.getY();
              prevZ[0] = botLoc.getZ();
            }
          }

          private void cleanup(@Nullable Player bot, boolean arrived, boolean pathFailure) {
            manager.clearNavJump(botUuid);
            manager.unlockNavigation(botUuid);
            if (bot != null) {
              NmsPlayerSpawner.setMovementForward(bot, 0f);
              NmsPlayerSpawner.setJumping(bot, false);
              bot.setSprinting(false);
            }
            if (pendingCloseLoc[0] != null && !pendingCloseWasOpen[0]) {
              org.bukkit.block.Block block = pendingCloseLoc[0].getBlock();
              org.bukkit.block.data.BlockData data = block.getBlockData();
              if (data instanceof org.bukkit.block.data.type.Door door) {
                Block target = door.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? block : block.getRelative(0, -1, 0);
                if (target.getBlockData() instanceof org.bukkit.block.data.type.Door targetDoor && targetDoor.isOpen()) {
                  targetDoor.setOpen(false);
                  target.setBlockData(targetDoor);
                  playDoorSound(target, false);
                }
              } else if (data instanceof org.bukkit.block.data.type.Gate gate && gate.isOpen()) {
                gate.setOpen(false);
                block.setBlockData(gate);
                playDoorSound(block, false);
              } else if (data instanceof org.bukkit.block.data.type.TrapDoor trap && trap.isOpen()) {
                trap.setOpen(false);
                block.setBlockData(trap);
                playDoorSound(block, false);
              }
              pendingCloseLoc[0] = null;
            }
            sessions.remove(botUuid);
            int currentTaskId = taskIdRef[0];
            if (currentTaskId != -1) {
              FppScheduler.cancelTask(currentTaskId);
              taskIdRef[0] = -1;
            }
            if (arrived) {
              if (request.lockOnArrival() != null) {
                manager.lockForAction(botUuid, request.lockOnArrival());
              }
              if (request.onArrive() != null) request.onArrive().run();
            } else if (pathFailure) {
              if (request.onPathFailure() != null) request.onPathFailure().run();
              else if (request.onCancel() != null) request.onCancel().run();
            } else if (request.onCancel() != null) {
              request.onCancel().run();
            }
          }

          private void walkToward(Player bot, Location target, double dist) {
            Location bl = bot.getLocation();
            float yaw =
                (float)
                    Math.toDegrees(
                        Math.atan2(-(target.getX() - bl.getX()), target.getZ() - bl.getZ()));
            bot.setRotation(yaw, 0f);
            NmsPlayerSpawner.setHeadYaw(bot, yaw);
            bot.setSprinting(dist > Config.pathfindingSprintDistance());
            NmsPlayerSpawner.setMovementForward(bot, 1.0f);
          }
        };

    int taskId = FppScheduler.runAtEntityRepeatingWithId(plugin, initialBot, tick, 0L, 1L);
    taskIdRef[0] = taskId;
    sessions.put(botUuid, new Session(request.owner(), taskId));
  }

  private static void playDoorSound(org.bukkit.block.Block block, boolean open) {
    try {
      org.bukkit.Sound sound;
      String mat = block.getType().name();
      if (mat.contains("IRON")) {
        sound = open ? org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN : org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE;
      } else if (mat.contains("TRAPDOOR")) {
        sound = open ? org.bukkit.Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : org.bukkit.Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;
      } else {
        sound = open ? org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN : org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE;
      }
      block.getWorld().playSound(block.getLocation(), sound, 1.0f, 1.0f);
    } catch (Exception ignored) {
    }
  }

  public static void tickSwimAi(Player bot, boolean navJump, boolean isNavigating) {
    try {
      tickSwimAiUnsafe(bot, navJump, isNavigating);
    } catch (NullPointerException e) {
      if (isFoliaWorldDataNotReady(e)) {
        NmsPlayerSpawner.setJumping(bot, bot.isInWater() || bot.isInLava());
        return;
      }
      throw e;
    }
  }

  private static void tickSwimAiUnsafe(Player bot, boolean navJump, boolean isNavigating) {
    boolean inFluid = bot.isInWater() || bot.isInLava();
    if (inFluid) {
      NmsPlayerSpawner.setJumping(bot, true);
      if (!isNavigating) {
        bot.setSprinting(false);
      }
      return;
    }

    if (isNavigating && navJump) {
      NmsPlayerSpawner.setJumping(bot, true);
    } else {
      NmsPlayerSpawner.setJumping(bot, false);
    }
  }

  private static boolean isFoliaWorldDataNotReady(NullPointerException e) {
    String msg = e.getMessage();
    return msg != null && msg.contains("getCurrentWorldData()");
  }

  private void primeInitialMovement(Player bot, Location dest, double arrivalDistance) {
    Location bl = bot.getLocation();
    double initDist = xzDist(bl, dest);
    if (initDist <= arrivalDistance) return;
    float initYaw =
        (float) Math.toDegrees(Math.atan2(-(dest.getX() - bl.getX()), dest.getZ() - bl.getZ()));
    bot.setRotation(initYaw, 0f);
    NmsPlayerSpawner.setHeadYaw(bot, initYaw);
    bot.setSprinting(initDist > Config.pathfindingSprintDistance());
    NmsPlayerSpawner.setMovementForward(bot, 1.0f);
  }

  @Nullable
  private static Location safeDestination(@Nullable Location loc) {
    return loc != null ? loc.clone() : null;
  }

  @Nullable
  private static Location findBreakTarget(Location botLoc, BotPathfinder.Move wp) {
    World w = botLoc.getWorld();
    int wx = wp.x(), wy = wp.y(), wz = wp.z();
    if (!w.getBlockAt(wx, wy, wz).isPassable())
      return new Location(w, wx + 0.5, wy + 0.5, wz + 0.5);
    if (!w.getBlockAt(wx, wy + 1, wz).isPassable())
      return new Location(w, wx + 0.5, wy + 1.5, wz + 0.5);
    int by = botLoc.getBlockY();
    int bx = botLoc.getBlockX(), bz = botLoc.getBlockZ();
    if (!w.getBlockAt(bx, by + 2, bz).isPassable())
      return new Location(w, bx + 0.5, by + 2.5, bz + 0.5);
    return null;
  }

  private Material resolvePlaceMaterial() {
    String raw = Config.pathfindingPlaceMaterial();
    if (cachedPlaceMaterial != null && raw.equals(cachedPlaceMaterialName)) {
      return cachedPlaceMaterial;
    }
    cachedPlaceMaterialName = raw;
    Material mat = Material.matchMaterial(raw.toUpperCase());
    cachedPlaceMaterial = (mat != null && mat.isBlock() && mat.isSolid()) ? mat : Material.DIRT;
    return cachedPlaceMaterial;
  }

  public static BotPathfinder.PathOptions resolvePathOptions(@NotNull FakePlayer fp) {
    return resolvePathOptions(fp, null);
  }

  public static BotPathfinder.PathOptions resolvePathOptions(
      @NotNull FakePlayer fp, @Nullable BotPathfinder.PathOptions overrideOpts) {
    if (overrideOpts != null) {
      return overrideOpts;
    }
    return new BotPathfinder.PathOptions(
        fp.isNavParkour(),
        fp.isNavBreakBlocks(),
        fp.isNavPlaceBlocks(),
        fp.isNavAvoidWater() || fp.isDefaultWaterPathAvoidanceEnabled(),
        fp.isNavAvoidLava() || !fp.isSwimAiEnabled());
  }

  public static double xzDist(Location a, Location b) {
    return xzDistRaw(a.getX(), a.getZ(), b.getX(), b.getZ());
  }

  public static double xzDistRaw(double ax, double az, double bx, double bz) {
    double dx = ax - bx;
    double dz = az - bz;
    return Math.sqrt(dx * dx + dz * dz);
  }
}
