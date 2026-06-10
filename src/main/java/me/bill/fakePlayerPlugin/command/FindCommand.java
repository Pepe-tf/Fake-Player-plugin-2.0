package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppBotBlockBreakEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/**
 * /fpp find <bot> <block> [--radius <n>] [--count <n>] [--prefer-visible]
 *
 */
public final class FindCommand implements FppCommand {

    /**
     * Default search radius if --radius is not specified.
     */
    private static final int DEFAULT_RADIUS = 32;

    /**
     * Hard cap on search radius to prevent freezing the server.
     */
    private static final int MAX_RADIUS = 128;

    /**
     * Ticks to wait after a block is broken before trying to mine the next one.
     */
    private static final int POST_MINE_PAUSE_TICKS = 10;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final PathfindingService pathfinding;

    private final Map<UUID, FindJob> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> miningTasks = new ConcurrentHashMap<>();
    private final Map<String, UUID> reservedBlocks = new ConcurrentHashMap<>();

    private final Map<UUID, SimpleMiningState> miningStates = new ConcurrentHashMap<>();

    public FindCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getUsage() {
        return "<bot> <block> [--radius <n>] [--count <n>] [--prefer-visible]  |  <bot> --stop  |" + "  --stop";
    }

    @Override
    public String getDescription() {
        return "Path to and mine nearby blocks of a chosen type. Repeats until --count blocks are"
                + " mined or no more are found.";
    }

    @Override
    public String getPermission() {
        return Perm.FIND;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.FIND);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("find-usage"));
            return true;
        }

        // /fpp find --stop  (stop all bots)
        if (isStop(args[0]) && args.length == 1) {
            stopAll();
            sender.sendMessage(Lang.get("find-stopped-all"));
            return true;
        }

        String botName = args[0];
        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("find-not-found", "name", botName));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("find-bot-offline", "name", fp.getDisplayName()));
            return true;
        }

        // /fpp find <bot> --stop
        if (args.length >= 2 && isStop(args[1])) {
            cleanupBot(fp.getUuid());
            sender.sendMessage(Lang.get("find-stopped", "name", fp.getDisplayName()));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.get("find-usage"));
            return true;
        }

        // Parse block material
        String blockArg = args[1].toUpperCase(Locale.ROOT);
        Material material;
        try {
            material = Material.valueOf(blockArg);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Lang.get("find-invalid-block", "block", args[1]));
            return true;
        }
        if (material.isAir() || !material.isBlock()) {
            sender.sendMessage(Lang.get("find-invalid-block", "block", args[1]));
            return true;
        }

        int radius = DEFAULT_RADIUS;
        int count = -1; // -1 = unlimited
        boolean preferVisible = false;

        for (int i = 2; i < args.length; i++) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            switch (flag) {
                case "--radius", "-r" -> {
                    if (i + 1 >= args.length) {
                        sender.sendMessage(Lang.get("find-usage"));
                        return true;
                    }
                    try {
                        radius = Math.min(MAX_RADIUS, Math.max(1, Integer.parseInt(args[++i])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Lang.get("find-invalid-radius", "value", args[i]));
                        return true;
                    }
                }
                case "--count", "-c" -> {
                    if (i + 1 >= args.length) {
                        sender.sendMessage(Lang.get("find-usage"));
                        return true;
                    }
                    try {
                        count = Math.max(1, Integer.parseInt(args[++i]));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Lang.get("find-invalid-count", "value", args[i]));
                        return true;
                    }
                }
                case "--prefer-visible", "--prefervisible" -> preferVisible = true;
                default -> {
                    // ignore unknown flags gracefully
                }
            }
        }

        // Stop any existing find/click jobs for this bot
        cleanupBot(fp.getUuid());
        var leftCmd = plugin.getLeftClickCommand();
        if (leftCmd != null) leftCmd.stopClicking(fp.getUuid());
        var rightCmd = plugin.getRightClickCommand();
        if (rightCmd != null) rightCmd.stopClicking(fp.getUuid());

        UUID starterUuid = sender instanceof Player p ? p.getUniqueId() : null;
        FindJob job = new FindJob(material, radius, count, preferVisible, starterUuid, sender instanceof Player);
        jobs.put(fp.getUuid(), job);

        String countDisplay = count < 0 ? "∞" : String.valueOf(count);
        sender.sendMessage(Lang.get(
                "find-started",
                "name",
                fp.getDisplayName(),
                "block",
                material.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                "radius",
                String.valueOf(radius),
                "count",
                countDisplay));

        // Begin first search cycle
        findAndMineNext(fp, job);
        return true;
    }

    public boolean startFindTask(CommandSender sender, FakePlayer fp, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("find-usage"));
            return false;
        }
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("find-bot-offline", "name", fp.getDisplayName()));
            return false;
        }
        Material material;
        try {
            material = Material.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Lang.get("find-invalid-block", "block", args[0]));
            return false;
        }
        if (material.isAir() || !material.isBlock()) {
            sender.sendMessage(Lang.get("find-invalid-block", "block", args[0]));
            return false;
        }

        int radius = DEFAULT_RADIUS;
        int count = -1;
        boolean preferVisible = false;
        int i = 1;
        if (i < args.length && !args[i].startsWith("-")) {
            try {
                count = Math.max(1, Integer.parseInt(args[i++]));
            } catch (NumberFormatException ignored) {
            }
        }
        for (; i < args.length; i++) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            switch (flag) {
                case "--radius", "-r" -> {
                    if (i + 1 >= args.length) return false;
                    try {
                        radius = Math.min(MAX_RADIUS, Math.max(1, Integer.parseInt(args[++i])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Lang.get("find-invalid-radius", "value", args[i]));
                        return false;
                    }
                }
                case "--count", "-c" -> {
                    if (i + 1 >= args.length) return false;
                    try {
                        count = Math.max(1, Integer.parseInt(args[++i]));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Lang.get("find-invalid-count", "value", args[i]));
                        return false;
                    }
                }
                case "--prefer-visible", "--prefervisible" -> preferVisible = true;
                default -> {}
            }
        }

        cleanupBot(fp.getUuid());
        var leftCmd = plugin.getLeftClickCommand();
        if (leftCmd != null) leftCmd.stopClicking(fp.getUuid());
        var rightCmd = plugin.getRightClickCommand();
        if (rightCmd != null) rightCmd.stopClicking(fp.getUuid());
        UUID starterUuid = sender instanceof Player p ? p.getUniqueId() : null;
        FindJob job = new FindJob(material, radius, count, preferVisible, starterUuid, sender instanceof Player);
        jobs.put(fp.getUuid(), job);
        findAndMineNext(fp, job);
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

        if (args.length == 2) {
            String prefix = args[1].toUpperCase(Locale.ROOT);
            if (isStop(args[0])) return List.of();
            // Suggest --stop or block names
            List<String> out = new ArrayList<>();
            if ("--STOP".startsWith(prefix)) out.add("--stop");
            for (Material m : Material.values()) {
                if (!m.isAir() && m.isBlock() && m.name().startsWith(prefix)) {
                    out.add(m.name().toLowerCase(Locale.ROOT));
                    if (out.size() >= 50) break; // cap for UX
                }
            }
            return out;
        }

        if (args.length >= 3) {
            String prev = args[args.length - 2].toLowerCase(Locale.ROOT);
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            if (prev.equals("--radius") || prev.equals("-r")) {
                return List.of("16", "32", "64");
            }
            if (prev.equals("--count") || prev.equals("-c")) {
                return List.of("1", "5", "10", "32", "64");
            }
            List<String> flags = new ArrayList<>();
            Set<String> usedFlags = new HashSet<>();
            for (int i = 2; i < args.length - 1; i++) {
                usedFlags.add(args[i].toLowerCase(Locale.ROOT));
            }
            if (!usedFlags.contains("--radius") && "--radius".startsWith(prefix)) flags.add("--radius");
            if (!usedFlags.contains("--count") && "--count".startsWith(prefix)) flags.add("--count");
            if (!usedFlags.contains("--prefer-visible") && "--prefer-visible".startsWith(prefix))
                flags.add("--prefer-visible");
            return flags;
        }

        return List.of();
    }

    private void findAndMineNext(FakePlayer fp, FindJob job) {
        if (!jobs.containsKey(fp.getUuid())) return; // stopped

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            cleanupBot(fp.getUuid());
            return;
        }

        Location origin = bot.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) {
            cleanupBot(fp.getUuid());
            return;
        }

        List<ChunkSnapshot> snapshots = snapshotChunks(world, origin, job.radius);
        FppScheduler.runAsync(plugin, () -> {
            BlockTarget found = findNearestBlockAsync(origin, snapshots, job, fp.getUuid());
            FppScheduler.runAtEntity(plugin, bot, () -> handleFindResult(fp, job, found));
        });
    }

    private void handleFindResult(FakePlayer fp, FindJob job, BlockTarget found) {
        if (!jobs.containsKey(fp.getUuid())) return;

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            cleanupBot(fp.getUuid());
            return;
        }

        if (found == null) {
            // No more blocks found
            cleanupBot(fp.getUuid());
            notifySender(
                    job,
                    job.minedCount > 0 ? "find-finished" : "find-none-found",
                    "name",
                    fp.getDisplayName(),
                    "block",
                    job.material.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    "count",
                    String.valueOf(job.minedCount));
            return;
        }

        Block target = bot.getWorld().getBlockAt(found.x(), found.y(), found.z());
        if (target.getType() != job.material || !isMineable(target)) {
            releaseReservation(found.key(), fp.getUuid());
            job.mined.add(found.key());
            findAndMineNext(fp, job);
            return;
        }

        job.mined.add(found.key()); // mark before nav so it won't be re-targeted during travel

        // Find a safe stand position adjacent to the target block
        Location standLoc =
                BotNavUtil.findStandLocation(bot.getWorld(), null, target.getX(), target.getY(), target.getZ());

        if (standLoc == null) {
            // Can't stand next to it — skip and try next
            releaseReservation(found.key(), fp.getUuid());
            findAndMineNext(fp, job);
            return;
        }

        Location faceLoc = BotNavUtil.faceToward(standLoc, target.getLocation().add(0.5, 0.5, 0.5));

        double xzDist = PathfindingService.xzDist(bot.getLocation(), faceLoc);

        if (xzDist <= Config.pathfindingArrivalDistance()) {
            lockAndMineTarget(fp, job, target, faceLoc);
        } else {
            pathfinding.navigate(
                    fp,
                    new PathfindingService.NavigationRequest(
                            PathfindingService.Owner.MINE,
                            () -> faceLoc,
                            Config.pathfindingArrivalDistance(),
                            0.0,
                            Integer.MAX_VALUE,
                            () -> lockAndMineTarget(fp, job, target, faceLoc),
                            () -> {
                                // Navigation cancelled externally — stop job
                                cleanupBot(fp.getUuid());
                            },
                            () -> {
                                // Path failed — skip this block and try next
                                releaseReservation(found.key(), fp.getUuid());
                                findAndMineNext(fp, job);
                            },
                            faceLoc));
        }
    }

    /**
     * Lock the bot at {@code lockLoc} facing the target and start the single-block mining ticker.
     */
    private void lockAndMineTarget(FakePlayer fp, FindJob job, Block target, Location lockLoc) {
        if (!jobs.containsKey(fp.getUuid())) return;

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            cleanupBot(fp.getUuid());
            return;
        }

        // Re-verify the block still exists (someone else may have mined it)
        if (target.getType() != job.material) {
            releaseReservation(blockKey(target), fp.getUuid());
            findAndMineNext(fp, job);
            return;
        }

        if (!isAtLockLocation(bot, lockLoc)) {
            pathfinding.navigate(
                    fp,
                    new PathfindingService.NavigationRequest(
                            PathfindingService.Owner.MINE,
                            () -> lockLoc,
                            Config.pathfindingArrivalDistance(),
                            0.0,
                            Integer.MAX_VALUE,
                            () -> lockAndMineTarget(fp, job, target, lockLoc),
                            () -> cleanupBot(fp.getUuid()),
                            () -> findAndMineNext(fp, job),
                            lockLoc));
            return;
        }

        BlockPos desiredPos = new BlockPos(target.getX(), target.getY(), target.getZ());
        BlockPos minePos = resolveMineTarget(bot, desiredPos, job.material);
        if (minePos == null) {
            releaseReservation(blockKey(target), fp.getUuid());
            findAndMineNext(fp, job);
            return;
        }

        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);
        manager.lockForAction(fp.getUuid(), lockLoc);

        SimpleMiningState state = new SimpleMiningState(minePos, desiredPos, lockLoc, minePos.equals(desiredPos));
        miningStates.put(fp.getUuid(), state);

        int taskId = FppScheduler.runAtEntityRepeatingWithId(
                plugin,
                bot,
                () -> {
                    Player b = fp.getPlayer();
                    if (b == null || !b.isOnline()) {
                        stopCurrentMine(fp.getUuid());
                        cleanupBot(fp.getUuid());
                        return;
                    }
                    tickMine(fp, job, state);
                },
                0L,
                1L);

        miningTasks.put(fp.getUuid(), taskId);
    }

    /**
     * Per-tick mining logic for a single target block. Mirrors MineCommand.tickMining logic.
     */
    private void tickMine(FakePlayer fp, FindJob job, SimpleMiningState state) {
        if (!jobs.containsKey(fp.getUuid())) {
            stopCurrentMine(fp.getUuid());
            return;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            stopCurrentMine(fp.getUuid());
            cleanupBot(fp.getUuid());
            return;
        }

        if (fp.isInventoryOpen()) {
            return;
        }

        ServerPlayer nms = ((CraftPlayer) bot).getHandle();
        collectNearbyDrops(bot);

        if (state.freeze > 0) {
            state.freeze--;
            return;
        }

        // Post-mine pause before moving on
        if (state.done) {
            if (state.postMinePause > 0) {
                collectNearbyDrops(bot);
                state.postMinePause--;
                return;
            }
            stopCurrentMine(fp.getUuid());

            if (state.countsTowardGoal) {
                job.minedCount++;
                releaseReservation(
                        packBlockKey(state.desiredPos.getX(), state.desiredPos.getY(), state.desiredPos.getZ()),
                        fp.getUuid());

                if (job.count > 0 && job.minedCount >= job.count) {
                    cleanupBot(fp.getUuid());
                    notifySender(
                            job,
                            "find-finished",
                            "name",
                            fp.getDisplayName(),
                            "block",
                            job.material.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                            "count",
                            String.valueOf(job.minedCount));
                    return;
                }

                FppScheduler.runAtEntityLaterWithId(plugin, bot, () -> findAndMineNext(fp, job), 2L);
                return;
            }

            Block desiredBlock = bot.getWorld()
                    .getBlockAt(state.desiredPos.getX(), state.desiredPos.getY(), state.desiredPos.getZ());
            if (desiredBlock.getType() != job.material) {
                releaseReservation(
                        packBlockKey(state.desiredPos.getX(), state.desiredPos.getY(), state.desiredPos.getZ()),
                        fp.getUuid());
                FppScheduler.runAtEntityLaterWithId(plugin, bot, () -> findAndMineNext(fp, job), 2L);
                return;
            }

            FppScheduler.runAtEntityLaterWithId(
                    plugin, bot, () -> lockAndMineTarget(fp, job, desiredBlock, state.lockLoc), 2L);
            return;
        }

        BlockPos targetPos = state.targetPos;
        BlockState blockState = nms.level().getBlockState(targetPos);
        faceTarget(bot, targetPos);

        // Block already gone
        if (blockState.isAir()) {
            state.done = true;
            state.postMinePause = POST_MINE_PAUSE_TICKS;
            return;
        }

        Material currentMaterial = CraftMagicNumbers.getMaterial(blockState.getBlock());
        if (state.countsTowardGoal) {
            if (currentMaterial != job.material) {
                state.done = true;
                state.postMinePause = 0;
                return;
            }
        } else {
            Block currentBlock = bot.getWorld().getBlockAt(targetPos.getX(), targetPos.getY(), targetPos.getZ());
            if (!isMineable(currentBlock)) {
                state.done = true;
                state.postMinePause = 0;
                return;
            }
        }

        if (nms.blockActionRestricted(nms.level(), targetPos, nms.gameMode.getGameModeForPlayer())) {
            return;
        }

        // Auto-equip best tool (delegates to MineCommand's package-private logic via the mine command
        // instance — here we duplicate the minimal version needed)
        equipBestTool(bot, targetPos);

        Direction side = Direction.DOWN;

        if (bot.getGameMode() == GameMode.CREATIVE) {
            if (fireBlockBreakHook(fp, targetPos)) {
                NmsPlayerSpawner.handleBlockBreakAction(
                        nms,
                        targetPos,
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        side,
                        nms.level().getMaxY(),
                        -1);
            }
            nms.swing(InteractionHand.MAIN_HAND);
            state.done = true;
            state.postMinePause = POST_MINE_PAUSE_TICKS;
            return;
        }

        if (state.currentPos == null || !state.currentPos.equals(targetPos)) {
            if (state.currentPos != null) {
                if (fireBlockBreakHook(fp, state.currentPos)) {
                    NmsPlayerSpawner.handleBlockBreakAction(
                            nms,
                            state.currentPos,
                            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                            side,
                            nms.level().getMaxY(),
                            -1);
                }
            }
            if (fireBlockBreakHook(fp, targetPos)) {
                NmsPlayerSpawner.handleBlockBreakAction(
                        nms,
                        targetPos,
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        side,
                        nms.level().getMaxY(),
                        -1);
            }

            if (state.progress == 0f) blockState.attack(nms.level(), targetPos, nms);

            float speed = blockState.getDestroyProgress(nms, nms.level(), targetPos);
            if (speed >= 1.0f) {
                nms.swing(InteractionHand.MAIN_HAND);
                state.done = true;
                state.postMinePause = POST_MINE_PAUSE_TICKS;
                return;
            }
            state.currentPos = targetPos;
            state.progress = 0f;
        } else {
            float speed = blockState.getDestroyProgress(nms, nms.level(), targetPos);
            state.progress += speed;
            if (state.progress >= 1.0f) {
                if (fireBlockBreakHook(fp, targetPos)) {
                    NmsPlayerSpawner.handleBlockBreakAction(
                            nms,
                            targetPos,
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            side,
                            nms.level().getMaxY(),
                            -1);
                }
                nms.swing(InteractionHand.MAIN_HAND);
                state.done = true;
                state.postMinePause = POST_MINE_PAUSE_TICKS;
                return;
            }
            NmsPlayerSpawner.destroyBlockProgress(nms, -1, targetPos, (int) (state.progress * 10));
        }

        nms.swing(InteractionHand.MAIN_HAND);
        nms.resetLastActionTime();
    }

    private boolean isAtLockLocation(Player bot, Location lockLoc) {
        if (bot == null || lockLoc == null) return false;
        if (bot.getWorld() != lockLoc.getWorld()) return false;
        double xz = PathfindingService.xzDist(bot.getLocation(), lockLoc);
        double dy = Math.abs(bot.getLocation().getY() - lockLoc.getY());
        return xz <= Config.pathfindingArrivalDistance() && dy < 1.25;
    }

    private boolean fireBlockBreakHook(FakePlayer fp, BlockPos pos) {
        if (fp == null || pos == null) return false;
        Player bot = fp.getPlayer();
        if (bot == null || bot.getWorld() == null) return false;
        var event = new FppBotBlockBreakEvent(
                new FppBotImpl(fp), bot.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private BlockPos resolveMineTarget(Player bot, BlockPos desiredPos, Material desiredMaterial) {
        Block desired = bot.getWorld().getBlockAt(desiredPos.getX(), desiredPos.getY(), desiredPos.getZ());
        if (desired.getType() != desiredMaterial) return null;

        Location eye = bot.getEyeLocation();
        Location targetCenter = desired.getLocation().add(0.5, 0.5, 0.5);
        Vector dir = targetCenter.toVector().subtract(eye.toVector());
        double dist = dir.length();
        if (dist <= 0.001) return desiredPos;

        RayTraceResult hit = bot.getWorld().rayTraceBlocks(eye, dir.normalize(), dist, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) return desiredPos;

        Block first = hit.getHitBlock();
        BlockPos firstPos = new BlockPos(first.getX(), first.getY(), first.getZ());
        if (firstPos.equals(desiredPos)) return desiredPos;
        if (!isMineable(first)) return null;
        return firstPos;
    }

    private void faceTarget(Player bot, BlockPos targetPos) {
        Location eye = bot.getEyeLocation();
        Location target =
                new Location(bot.getWorld(), targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        Location faced = BotNavUtil.faceToward(eye, target);
        bot.setRotation(faced.getYaw(), faced.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, faced.getYaw());
        manager.updateActionLockRotation(bot.getUniqueId(), faced.getYaw(), faced.getPitch());
    }

    private void collectNearbyDrops(Player bot) {
        for (Entity e : bot.getNearbyEntities(2.25, 1.75, 2.25)) {
            if (!(e instanceof Item item) || item.isDead() || item.getPickupDelay() > 0) continue;
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                item.remove();
                continue;
            }
            Map<Integer, ItemStack> leftovers = bot.getInventory().addItem(stack.clone());
            if (leftovers.isEmpty()) {
                item.remove();
            } else {
                ItemStack remaining = leftovers.values().iterator().next();
                item.setItemStack(remaining);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Block search
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Scan a sphere of {@code radius} blocks around the bot for the nearest block of the given
     * material. Already-visited block positions (by packed key) are skipped.
     */
    private Block findNearestBlock(
            Player bot, Material material, int radius, Set<Long> visited, boolean preferVisible) {
        Location origin = bot.getLocation();
        World world = origin.getWorld();
        if (world == null) return null;

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        Block best = null;
        Block bestVisible = null;
        double bestDist = Double.MAX_VALUE;
        double bestVisibleDist = Double.MAX_VALUE;
        double radiusSq = (double) radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int x = ox + dx;
                    int y = oy + dy;
                    int z = oz + dz;
                    if (y < minY || y > maxY) continue;
                    double distSq = dx * (double) dx + dy * (double) dy + dz * (double) dz;
                    if (distSq > radiusSq) continue;
                    if (visited.contains(packBlockKey(x, y, z))) continue;

                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != material) continue;
                    if (!isMineable(block)) continue;

                    if (preferVisible && isBlockVisible(bot, block)) {
                        if (distSq < bestVisibleDist) {
                            bestVisibleDist = distSq;
                            bestVisible = block;
                        }
                        continue;
                    }

                    if (distSq < bestDist) {
                        bestDist = distSq;
                        best = block;
                    }
                }
            }
        }
        if (preferVisible && bestVisible != null) return bestVisible;
        return best;
    }

    private List<ChunkSnapshot> snapshotChunks(World world, Location origin, int radius) {
        int minChunkX = (origin.getBlockX() - radius) >> 4;
        int maxChunkX = (origin.getBlockX() + radius) >> 4;
        int minChunkZ = (origin.getBlockZ() - radius) >> 4;
        int maxChunkZ = (origin.getBlockZ() + radius) >> 4;
        List<ChunkSnapshot> out = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                out.add(world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
            }
        }
        return out;
    }

    private BlockTarget findNearestBlockAsync(
            Location origin, List<ChunkSnapshot> snapshots, FindJob job, UUID botUuid) {
        World world = origin.getWorld();
        if (world == null) return null;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int radius = job.radius;
        double radiusSq = (double) radius * radius;

        BlockTarget best = null;
        double bestDist = Double.MAX_VALUE;
        for (ChunkSnapshot snapshot : snapshots) {
            int baseX = snapshot.getX() << 4;
            int baseZ = snapshot.getZ() << 4;
            for (int lx = 0; lx < 16; lx++) {
                int x = baseX + lx;
                int dx = x - ox;
                if (Math.abs(dx) > radius) continue;
                for (int lz = 0; lz < 16; lz++) {
                    int z = baseZ + lz;
                    int dz = z - oz;
                    if (Math.abs(dz) > radius) continue;
                    for (int y = Math.max(minY, oy - radius); y <= Math.min(maxY, oy + radius); y++) {
                        int dy = y - oy;
                        double distSq = dx * (double) dx + dy * (double) dy + dz * (double) dz;
                        if (distSq > radiusSq || distSq >= bestDist) continue;
                        long key = packBlockKey(x, y, z);
                        if (job.mined.contains(key) || reservedBlocks.containsKey(reservationKey(world, key))) continue;
                        if (snapshot.getBlockType(lx, y, lz) != job.material) continue;
                        best = new BlockTarget(world.getUID(), x, y, z, key);
                        bestDist = distSq;
                    }
                }
            }
        }
        if (best == null) return null;
        String reservation = reservationKey(world, best.key());
        UUID existing = reservedBlocks.putIfAbsent(reservation, botUuid);
        return existing == null || existing.equals(botUuid) ? best : null;
    }

    private boolean isBlockVisible(Player bot, Block block) {
        Location eye = bot.getEyeLocation();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Vector dir = center.toVector().subtract(eye.toVector());
        double dist = dir.length();
        if (dist <= 0.001) return true;
        RayTraceResult hit = bot.getWorld().rayTraceBlocks(eye, dir.normalize(), dist, FluidCollisionMode.NEVER, true);
        return hit != null
                && hit.getHitBlock() != null
                && hit.getHitBlock().getX() == block.getX()
                && hit.getHitBlock().getY() == block.getY()
                && hit.getHitBlock().getZ() == block.getZ();
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

    // ─────────────────────────────────────────────────────────────────────────────
    //  Tool equip (minimal version — delegates pattern from MineCommand)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Equip the best mining tool for the given block position. Scans the hotbar + main inventory and
     * swaps the best tool into the held slot. This is a condensed version of MineCommand's tool logic.
     */
    private void equipBestTool(Player bot, BlockPos pos) {
        // Handled by the addon auto-equipment tick handler.
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Lifecycle helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Cancels the mining ticker and releases the action lock for this bot.
     */
    private void stopCurrentMine(UUID botUuid) {
        Integer taskId = miningTasks.remove(botUuid);
        if (taskId != null) FppScheduler.cancelTask(taskId);

        miningStates.remove(botUuid);
        manager.unlockAction(botUuid);
        manager.unlockNavigation(botUuid);

        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
                NmsPlayerSpawner.setMovementForward(bot, 0f);
                bot.setSprinting(false);
            }
        }
    }

    /**
     * Fully stops the find job for a bot. Safe to call multiple times.
     */
    public void cleanupBot(UUID botUuid) {
        jobs.remove(botUuid);
        releaseReservations(botUuid);
        pathfinding.cancel(botUuid);
        stopCurrentMine(botUuid);
    }

    /**
     * Stops all active find jobs.
     */
    public void stopAll() {
        for (UUID uuid : new HashSet<>(jobs.keySet())) {
            cleanupBot(uuid);
        }
    }

    public boolean isFinding(UUID botUuid) {
        return jobs.containsKey(botUuid);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Notification helper
    // ─────────────────────────────────────────────────────────────────────────────

    private void notifySender(FindJob job, String langKey, String... args) {
        if (job.starterUuid != null) {
            Player p = Bukkit.getPlayer(job.starterUuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(Lang.get(langKey, args));
                return;
            }
        }
        if (!job.playerStarted) {
            // console sender
            plugin.getLogger().info(Lang.raw(langKey, args));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Block key helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private static long packBlockKey(int x, int y, int z) {
        // Encode x/z into 26 bits each (±33M blocks), y into 12 bits (±2048)
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static long blockKey(Block block) {
        return packBlockKey(block.getX(), block.getY(), block.getZ());
    }

    private String reservationKey(World world, long packedBlockKey) {
        return world.getUID() + ":" + packedBlockKey;
    }

    private void releaseReservation(long packedBlockKey, UUID botUuid) {
        FakePlayer fp = manager.getByUuid(botUuid);
        Player bot = fp != null ? fp.getPlayer() : null;
        if (bot == null || bot.getWorld() == null) return;
        reservedBlocks.remove(reservationKey(bot.getWorld(), packedBlockKey), botUuid);
    }

    private void releaseReservations(UUID botUuid) {
        reservedBlocks.entrySet().removeIf(entry -> entry.getValue().equals(botUuid));
    }

    private static boolean isStop(String arg) {
        return arg.equalsIgnoreCase("--stop");
    }

    private record BlockTarget(UUID worldId, int x, int y, int z, long key) {}

    // ─────────────────────────────────────────────────────────────────────────────
    //  Inner types
    // ─────────────────────────────────────────────────────────────────────────────

    private static final class FindJob {
        final Material material;
        final int radius;
        final int count; // -1 = unlimited
        final boolean preferVisible;
        final UUID starterUuid;
        final boolean playerStarted;

        /**
         * Packed keys of blocks already targeted (so we don't revisit them).
         */
        final Set<Long> mined = new HashSet<>();

        int minedCount = 0;

        FindJob(
                Material material,
                int radius,
                int count,
                boolean preferVisible,
                UUID starterUuid,
                boolean playerStarted) {
            this.material = material;
            this.radius = radius;
            this.count = count;
            this.preferVisible = preferVisible;
            this.starterUuid = starterUuid;
            this.playerStarted = playerStarted;
        }
    }

    private static final class SimpleMiningState {
        final BlockPos targetPos;
        final BlockPos desiredPos;
        final Location lockLoc;
        final boolean countsTowardGoal;
        BlockPos currentPos;
        float progress;
        int freeze;
        boolean done;
        int postMinePause;

        SimpleMiningState(BlockPos targetPos, BlockPos desiredPos, Location lockLoc, boolean countsTowardGoal) {
            this.targetPos = targetPos;
            this.desiredPos = desiredPos;
            this.lockLoc = lockLoc.clone();
            this.countsTowardGoal = countsTowardGoal;
        }
    }
}
