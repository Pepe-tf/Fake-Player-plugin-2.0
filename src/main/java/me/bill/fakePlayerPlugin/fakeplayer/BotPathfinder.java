package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.*;
import me.bill.fakePlayerPlugin.config.Config;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.TrapDoor;

public final class BotPathfinder {

  private BotPathfinder() {}

  private static final int WALK = 10;
  private static final int DIAGONAL = 14;
  private static final int ASCEND = 12;
  private static final int FALL_PER = 3;
  private static final int PARKOUR_C = 20;
  private static final int BREAK_C = 30;
  private static final int PLACE_C = 20;
  private static final int PILLAR_C = 18;
  private static final int SWIM_C = 14;
  private static final int WATER_PEN = 6;
  private static final int OPEN_C = 12;
  private static final int CLIMB_C = 10;

  private static final Set<Material> HAZARDS =
      EnumSet.of(
          Material.LAVA,
          Material.FIRE,
          Material.SOUL_FIRE,
          Material.CACTUS,
          Material.SWEET_BERRY_BUSH,
          Material.MAGMA_BLOCK,
          Material.CAMPFIRE,
          Material.SOUL_CAMPFIRE,
          Material.WITHER_ROSE,
          Material.POWDER_SNOW,
          Material.POINTED_DRIPSTONE);

  private static final Set<Material> SLOW_BLOCKS =
      EnumSet.of(Material.SOUL_SAND, Material.HONEY_BLOCK, Material.COBWEB);

  private static final int[][] DIRS = {
    {1, 0, WALK}, {-1, 0, WALK},
    {0, 1, WALK}, {0, -1, WALK},
    {1, 1, DIAGONAL}, {1, -1, DIAGONAL},
    {-1, 1, DIAGONAL}, {-1, -1, DIAGONAL}
  };

  private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

  public record Pos(int x, int y, int z) {}

  public enum MoveType {
    WALK,
    ASCEND,
    DESCEND,
    PARKOUR,
    BREAK,
    PLACE,
    PILLAR,
    SWIM,
    OPEN,
    CLIMB
  }

  public record Move(int x, int y, int z, MoveType type) {
    public Pos toPos() {
      return new Pos(x, y, z);
    }
  }

  public record PathOptions(boolean parkour, boolean breakBlocks, boolean placeBlocks,
                            boolean avoidWater, boolean avoidLava) {
    public PathOptions(boolean parkour, boolean breakBlocks, boolean placeBlocks) {
      this(parkour, breakBlocks, placeBlocks, false, false);
    }

    public static final PathOptions DEFAULT = new PathOptions(false, false, false, false, false);

    public boolean anyEnabled() {
      return parkour || breakBlocks || placeBlocks;
    }
  }

  private record Node(Pos pos, Node parent, int g, int h, MoveType action) {
    int f() {
      return g + h;
    }
  }

  public static List<Move> findPathMoves(
      World world, int sx, int sy, int sz, int tx, int ty, int tz, PathOptions opts) {

    int configuredMaxRange = Config.pathfindingMaxRange();
    if (Math.abs(sx - tx) + Math.abs(sy - ty) + Math.abs(sz - tz) > configuredMaxRange * 3) {
      return null;
    }

    Pos start = snap(world, sx, sy, sz);
    Pos goal = snap(world, tx, ty, tz);
    if (start == null || goal == null) return null;
    if (start.equals(goal))
      return List.of(new Move(start.x(), start.y(), start.z(), MoveType.WALK));

    int nodeLimit =
        opts.anyEnabled() ? Config.pathfindingMaxNodesExtended() : Config.pathfindingMaxNodes();

    PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f));

    Map<Long, Integer> best = new HashMap<>(nodeLimit);

    open.add(new Node(start, null, 0, heuristic(start, goal), MoveType.WALK));
    best.put(posKey(start.x(), start.y(), start.z()), 0);

    int explored = 0;
    while (!open.isEmpty() && explored++ < nodeLimit) {
      Node cur = open.poll();

      long curKey = posKey(cur.pos().x(), cur.pos().y(), cur.pos().z());
      Integer bestG = best.get(curKey);
      if (bestG != null && cur.g() > bestG) continue;

      if (Math.abs(cur.pos().x() - goal.x()) <= 1
          && cur.pos().y() == goal.y()
          && Math.abs(cur.pos().z() - goal.z()) <= 1) {
        return buildPathMoves(cur);
      }

      for (int[] nb : neighbors(world, cur.pos().x(), cur.pos().y(), cur.pos().z(), opts)) {
        Pos np = new Pos(nb[0], nb[1], nb[2]);
        long npKey = posKey(nb[0], nb[1], nb[2]);
        int newG = cur.g() + nb[3];
        MoveType mt = MOVE_TYPES[nb[4]];

        Integer existing = best.get(npKey);
        if (existing == null || newG < existing) {
          best.put(npKey, newG);
          open.add(new Node(np, cur, newG, heuristic(np, goal), mt));
        }
      }
    }

    return null;
  }

  public static List<Pos> findPath(
      World world, int sx, int sy, int sz, int tx, int ty, int tz, PathOptions opts) {
    List<Move> moves = findPathMoves(world, sx, sy, sz, tx, ty, tz, opts);
    if (moves == null) return null;
    List<Pos> result = new ArrayList<>(moves.size());
    for (Move m : moves) result.add(m.toPos());
    return result;
  }

  private static final MoveType[] MOVE_TYPES = MoveType.values();

  private static final long X_BIAS = 1 << 19;
  private static final long Z_BIAS = 1 << 19;
  private static final long Y_BIAS = 1 << 11;

  private static long posKey(int x, int y, int z) {
    return ((long) (x + X_BIAS) << 32) | ((long) (y + Y_BIAS) << 20) | (z + Z_BIAS);
  }

  private static List<int[]> neighbors(World world, int x, int y, int z, PathOptions opts) {
    List<int[]> out = new ArrayList<>(48);

    final int WK = MoveType.WALK.ordinal(),
        AS = MoveType.ASCEND.ordinal(),
        DE = MoveType.DESCEND.ordinal(),
        PK = MoveType.PARKOUR.ordinal(),
        BK = MoveType.BREAK.ordinal(),
        PL = MoveType.PLACE.ordinal(),
        PI = MoveType.PILLAR.ordinal(),
        SW = MoveType.SWIM.ordinal(),
        OP = MoveType.OPEN.ordinal(),
        CL = MoveType.CLIMB.ordinal();

    int maxFall = Config.pathfindingMaxFall();

    for (int[] d : DIRS) {
      int dx = d[0], dz = d[1], base = d[2];
      int nx = x + dx, nz = z + dz;
      boolean isDiag = (dx != 0 && dz != 0);

      if (isDiag) {
        if (!canPassThrough(world, x + dx, y, z)
            || !canPassThrough(world, x + dx, y + 1, z)
            || !canPassThrough(world, x, y, z + dz)
            || !canPassThrough(world, x, y + 1, z + dz)) continue;
      }

      boolean feetClear = canPassThrough(world, nx, y, nz);
      boolean headClear = canPassThrough(world, nx, y + 1, nz);
      boolean floorSolid = canStandOn(world, nx, y - 1, nz);

      if (feetClear && headClear && floorSolid) {
        int cost = base;

        if (isWater(world, nx, y, nz)) cost += WATER_PEN;
        else if (isSlowBlock(world, nx, y, nz)) cost += WATER_PEN;

        if (!isHazard(world, nx, y, nz) && !isHazard(world, nx, y - 1, nz)) {
          out.add(new int[] {nx, y, nz, cost, WK});
        }
      } else if (!isDiag && opts.breakBlocks() && floorSolid) {
        int cost = base;
        if (!feetClear && canBreak(world, nx, y, nz)) cost += BREAK_C;
        else if (!feetClear) continue;
        if (!headClear && canBreak(world, nx, y + 1, nz)) cost += BREAK_C;
        else if (!headClear) continue;
        if (cost > base) {
          out.add(new int[] {nx, y, nz, cost, BK});
        }
      }

      if (!isDiag) {
        if (!feetClear && isOpenable(world, nx, y, nz)) {
          out.add(new int[] {nx, y, nz, base + OPEN_C, OP});
        } else if (feetClear && !headClear && isOpenable(world, nx, y + 1, nz)) {
          out.add(new int[] {nx, y, nz, base + OPEN_C, OP});
        }
      }

      if (!isDiag && opts.placeBlocks() && !floorSolid && feetClear && headClear) {

        if (hasAdjacentSolid(world, nx, y - 1, nz)) {
          if (!isHazard(world, nx, y, nz)) {
            out.add(new int[] {nx, y, nz, base + PLACE_C, PL});
          }
        }
      }

      boolean srcHeadClear = canPassThrough(world, x, y + 2, z);
      boolean tgtFeetClear = canPassThrough(world, nx, y + 1, nz);
      boolean tgtHeadClear = canPassThrough(world, nx, y + 2, nz);
      boolean tgtFloorSolid = canStandOn(world, nx, y, nz);

      if (srcHeadClear && tgtFeetClear && tgtHeadClear && tgtFloorSolid) {
        if (!isHazard(world, nx, y + 1, nz)) {
          int cost = base + ASCEND;
          if (isSlowBlock(world, x, y, z)) cost += 4;
          out.add(new int[] {nx, y + 1, nz, cost, AS});
        }
      } else if (!isDiag
          && opts.breakBlocks()
          && tgtFeetClear
          && tgtFloorSolid
          && !srcHeadClear
          && canBreak(world, x, y + 2, z)
          && tgtHeadClear) {
        out.add(new int[] {nx, y + 1, nz, base + ASCEND + BREAK_C, BK});
      }

      if (feetClear && headClear) {
        for (int drop = 1; drop <= maxFall; drop++) {
          int ny = y - drop;
          if (!inBounds(world, ny)) break;
          if (canStandOn(world, nx, ny - 1, nz)
              && canPassThrough(world, nx, ny, nz)
              && canPassThrough(world, nx, ny + 1, nz)) {
            if (!isHazard(world, nx, ny, nz) && !isHazard(world, nx, ny - 1, nz)) {
              int fallCost = base + drop * FALL_PER;

              if (drop >= 4) fallCost += (drop - 3) * 8;
              out.add(new int[] {nx, ny, nz, fallCost, DE});
            }
            break;
          }
          if (!canPassThrough(world, nx, ny, nz)) break;
        }
      }

      if (isWater(world, nx, y, nz) && isWater(world, nx, y + 1, nz)) {

        out.add(new int[] {nx, y, nz, SWIM_C, SW});

        if (canPassThrough(world, nx, y + 2, nz) || isWater(world, nx, y + 2, nz)) {
          out.add(new int[] {nx, y + 1, nz, SWIM_C + 2, SW});
        }

        if (isWater(world, nx, y - 1, nz) || canPassThrough(world, nx, y - 1, nz)) {
          out.add(new int[] {nx, y - 1, nz, SWIM_C + 2, SW});
        }
      }

      if (!isDiag && opts.parkour()) {
        tryParkour(world, x, y, z, dx, dz, out, PK, feetClear, headClear);
      }
    }

    if (opts.placeBlocks() && canPassThrough(world, x, y + 2, z)) {

      if (canPassThrough(world, x, y + 1, z) && canPassThrough(world, x, y + 2, z)) {
        out.add(new int[] {x, y + 1, z, PILLAR_C, PI});
      }
    }

    if (isWater(world, x, y, z)) {
      if (isWater(world, x, y + 1, z) || canPassThrough(world, x, y + 1, z)) {
        out.add(new int[] {x, y + 1, z, SWIM_C, SW});
      }
      if (isWater(world, x, y - 1, z) || canPassThrough(world, x, y - 1, z)) {
        out.add(new int[] {x, y - 1, z, SWIM_C + 2, SW});
      }
    }

    if (isClimbable(world, x, y, z)) {
      if (canPassThrough(world, x, y + 1, z)) {
        out.add(new int[] {x, y + 1, z, CLIMB_C, CL});
      }
      if (canPassThrough(world, x, y - 1, z)) {
        out.add(new int[] {x, y - 1, z, CLIMB_C, CL});
      }
    }

    return out;
  }

  private static void tryParkour(
      World world,
      int x,
      int y,
      int z,
      int dx,
      int dz,
      List<int[]> out,
      int PK,
      boolean feetClear,
      boolean headClear) {

    if (isSlowBlock(world, x, y - 1, z)) return;
    if (isWater(world, x, y, z)) return;
    if (isWater(world, x, y - 1, z)) return;

    if (!canPassThrough(world, x, y + 2, z)) return;

    for (int dist = 2; dist <= 4; dist++) {

      boolean gapClear = true;
      for (int step = 1; step < dist; step++) {
        int gx = x + dx * step, gz = z + dz * step;
        if (!canPassThrough(world, gx, y, gz)
            || !canPassThrough(world, gx, y + 1, gz)
            || !canPassThrough(world, gx, y + 2, gz)) {
          gapClear = false;
          break;
        }
      }
      if (!gapClear) break;

      int lx = x + dx * dist, lz = z + dz * dist;

      if (canStandOn(world, lx, y - 1, lz)
          && canPassThrough(world, lx, y, lz)
          && canPassThrough(world, lx, y + 1, lz)
          && !isHazard(world, lx, y, lz)
          && !isHazard(world, lx, y - 1, lz)) {
        int cost = WALK * dist + PARKOUR_C + (dist - 2) * 6;
        out.add(new int[] {lx, y, lz, cost, PK});
      }

      if (dist <= 3
          && canStandOn(world, lx, y, lz)
          && canPassThrough(world, lx, y + 1, lz)
          && canPassThrough(world, lx, y + 2, lz)
          && !isHazard(world, lx, y + 1, lz)
          && !isHazard(world, lx, y, lz)) {
        int cost = WALK * dist + PARKOUR_C + ASCEND + (dist - 2) * 6;
        out.add(new int[] {lx, y + 1, lz, cost, PK});
      }

      if (dist <= 3
          && canStandOn(world, lx, y - 2, lz)
          && canPassThrough(world, lx, y - 1, lz)
          && canPassThrough(world, lx, y, lz)
          && !isHazard(world, lx, y - 1, lz)
          && !isHazard(world, lx, y - 2, lz)) {
        int cost = WALK * dist + PARKOUR_C + FALL_PER + (dist - 2) * 6;
        out.add(new int[] {lx, y - 1, lz, cost, PK});
      }

      if (dist <= 2
          && canStandOn(world, lx, y - 3, lz)
          && canPassThrough(world, lx, y - 2, lz)
          && canPassThrough(world, lx, y - 1, lz)
          && !isHazard(world, lx, y - 2, lz)
          && !isHazard(world, lx, y - 3, lz)) {
        int cost = WALK * dist + PARKOUR_C + FALL_PER * 2 + 4;
        out.add(new int[] {lx, y - 2, lz, cost, PK});
      }
    }
  }

  public static boolean canPassThrough(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return true;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      Block block = world.getBlockAt(x, y, z);
      Material mat = block.getType();

      if (mat.isAir()) return true;

      if (mat == Material.WATER) return true;

      if (mat == Material.LAVA) return false;

      if (block.getBlockData() instanceof Fence) return false;
      if (mat.name().contains("WALL") && !mat.name().contains("WALL_")) return false;
      if (mat.name().contains("_WALL")
          || mat == Material.COBBLESTONE_WALL
          || mat == Material.MOSSY_COBBLESTONE_WALL) return false;

      if (block.getBlockData() instanceof Door door) {
        return door.isOpen();
      }

      if (block.getBlockData() instanceof Gate gate) {
        return gate.isOpen();
      }

      if (block.getBlockData() instanceof TrapDoor trapDoor) {
        return trapDoor.isOpen();
      }

      if (block.getBlockData() instanceof Slab slab) {
        return slab.getType() == Slab.Type.BOTTOM;
      }

      if (mat == Material.COBWEB) return false;

      if (isClimbable(mat)) return true;

      return block.isPassable();
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean canStandOn(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      Block block = world.getBlockAt(x, y, z);
      Material mat = block.getType();

      if (mat.isAir()) return false;

      if (mat.isSolid() && mat.isOccluding()) return true;

      if (block.getBlockData() instanceof Slab slab) {
        return true;
      }

      if (mat.name().contains("STAIRS")) return true;

      if (block.getBlockData() instanceof Fence) return false;
      if (mat.name().contains("WALL")) return false;

      if (mat == Material.GLASS
          || mat.name().contains("STAINED_GLASS") && !mat.name().contains("PANE")) return true;

      if (mat == Material.CHEST
          || mat == Material.TRAPPED_CHEST
          || mat == Material.ENDER_CHEST
          || mat == Material.BARREL) return true;

      if (mat.name().contains("LEAVES")) return true;

      if (mat == Material.FARMLAND || mat == Material.DIRT_PATH || mat == Material.SOUL_SAND)
        return true;

      if (mat == Material.HONEY_BLOCK) return true;

      if (mat.name().contains("_BED")) return true;

      if (mat == Material.SCAFFOLDING) return true;

      if (isClimbable(mat)) return true;

      if (block.getBlockData() instanceof TrapDoor trapDoor) {
        return !trapDoor.isOpen() && trapDoor.getHalf() == org.bukkit.block.data.Bisected.Half.TOP;
      }

      if (mat == Material.WATER) return false;

      if (mat == Material.MAGMA_BLOCK) return true;

      return !block.isPassable();
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean walkable(World world, int x, int y, int z) {
    if (!inBounds(world, y) || !inBounds(world, y + 1)) return false;
    return canStandOn(world, x, y - 1, z)
        && canPassThrough(world, x, y, z)
        && canPassThrough(world, x, y + 1, z);
  }

  public static boolean passable(World world, int x, int y, int z) {
    return canPassThrough(world, x, y, z);
  }

  private static boolean isHazard(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      return HAZARDS.contains(world.getBlockAt(x, y, z).getType());
    } catch (Exception e) {
      return true;
    }
  }

  private static boolean isWater(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      Block block = world.getBlockAt(x, y, z);
      if (block.getType() == Material.WATER) return true;

      if (block.getBlockData() instanceof Waterlogged wl) {
        return wl.isWaterlogged();
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isSlowBlock(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      return SLOW_BLOCKS.contains(world.getBlockAt(x, y, z).getType());
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isOpenable(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      Block block = world.getBlockAt(x, y, z);
      if (block.getBlockData() instanceof Door door) {
        Block bottom = door.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? block : block.getRelative(0, -1, 0);
        if (bottom.getBlockData() instanceof Door bottomDoor) return !bottomDoor.isOpen();
        return false;
      }
      if (block.getBlockData() instanceof Gate gate) return !gate.isOpen();
      if (block.getBlockData() instanceof TrapDoor trap) return !trap.isOpen();
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isClimbable(Material mat) {
    return mat == Material.LADDER
        || mat == Material.VINE
        || mat == Material.TWISTING_VINES
        || mat == Material.TWISTING_VINES_PLANT
        || mat == Material.WEEPING_VINES
        || mat == Material.WEEPING_VINES_PLANT
        || mat == Material.CAVE_VINES
        || mat == Material.CAVE_VINES_PLANT
        || mat == Material.SCAFFOLDING;
  }

  private static boolean isClimbable(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      return isClimbable(world.getBlockAt(x, y, z).getType());
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean canBreak(World world, int x, int y, int z) {
    if (y < world.getMinHeight() || y > world.getMaxHeight()) return false;
    try {
      if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
      Material mat = world.getBlockAt(x, y, z).getType();
      if (mat.isAir()) return false;

      if (mat == Material.BEDROCK
          || mat == Material.END_PORTAL_FRAME
          || mat == Material.END_PORTAL
          || mat == Material.BARRIER
          || mat == Material.COMMAND_BLOCK
          || mat == Material.CHAIN_COMMAND_BLOCK
          || mat == Material.REPEATING_COMMAND_BLOCK
          || mat == Material.STRUCTURE_BLOCK
          || mat == Material.JIGSAW
          || mat == Material.REINFORCED_DEEPSLATE) return false;

      if (mat == Material.OBSIDIAN
          || mat == Material.CRYING_OBSIDIAN
          || mat == Material.RESPAWN_ANCHOR
          || mat == Material.ANCIENT_DEBRIS
          || mat == Material.NETHERITE_BLOCK
          || mat == Material.ENDER_CHEST) return false;
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean hasAdjacentSolid(World world, int x, int y, int z) {
    return canStandOn(world, x, y - 1, z)
        || canStandOn(world, x + 1, y, z)
        || canStandOn(world, x - 1, y, z)
        || canStandOn(world, x, y, z + 1)
        || canStandOn(world, x, y, z - 1);
  }

  private static Pos snap(World world, int x, int y, int z) {
    for (int dy = 0; dy <= 3; dy++) {
      if (dy == 0 && walkable(world, x, y, z)) return new Pos(x, y, z);
      if (dy > 0 && walkable(world, x, y + dy, z)) return new Pos(x, y + dy, z);
      if (dy > 0 && walkable(world, x, y - dy, z)) return new Pos(x, y - dy, z);
    }

    if (isWater(world, x, y, z)) return new Pos(x, y, z);
    return null;
  }

  private static int heuristic(Pos a, Pos b) {
    int dx = Math.abs(a.x() - b.x());
    int dy = Math.abs(a.y() - b.y());
    int dz = Math.abs(a.z() - b.z());
    int maxXZ = Math.max(dx, dz), minXZ = Math.min(dx, dz);

    return (WALK * maxXZ) + ((DIAGONAL - WALK) * minXZ) + dy * ASCEND;
  }

  private static boolean inBounds(World world, int y) {
    return y > world.getMinHeight() && y < world.getMaxHeight() - 1;
  }

  private static List<Move> buildPathMoves(Node end) {
    List<Move> path = new ArrayList<>();
    for (Node n = end; n != null; n = n.parent()) {
      path.addFirst(new Move(n.pos().x(), n.pos().y(), n.pos().z(), n.action()));
    }
    return path;
  }
}
