package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotClickDispatcher;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class LeftClickCommand implements FppCommand {

    private static final double CLICK_REACH = 5.0;

    /** Valid range for a bot's configurable left-click interval (server default or per-bot override). */
    public static final int MIN_INTERVAL_TICKS = 1;

    public static final int MAX_INTERVAL_TICKS = 40;

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

    public LeftClickCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    private static void dbg(String msg) {
        FppLogger.debug("LEFTCLICK", Config.debugLeftClick(), msg);
    }

    private static void dbgHead(String msg) {
        FppLogger.debug("LEFTCLICK-HEAD", Config.debugLeftClickHead(), msg);
    }

    private static String describePos(Block b) {
        return "@(" + b.getX() + "," + b.getY() + "," + b.getZ() + ")";
    }

    @Override
    public String getName() {
        return "left-click";
    }

    @Override
    public String getUsage() {
        return "<bot> [--once|--repeat|--hold|--stop]  |  --stop";
    }

    @Override
    public String getDescription() {
        return "Bot left-clicks like a real player (attacks what it aims at, else breaks blocks). Default: --once";
    }

    @Override
    public String getPermission() {
        return Perm.LEFT_CLICK;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.hasAny(
                sender,
                Perm.LEFT_CLICK,
                Perm.LEFT_CLICK_START,
                Perm.LEFT_CLICK_ONCE,
                Perm.LEFT_CLICK_REPEAT,
                Perm.LEFT_CLICK_HOLD,
                Perm.LEFT_CLICK_STOP);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("left-click-usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("--stop") && args.length == 1) {
            if (!Perm.has(sender, Perm.LEFT_CLICK_STOP)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
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

        if (sender instanceof Player player && !Perm.has(sender, Perm.ADMIN)) {
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
        boolean stop = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            switch (a) {
                case "--once" -> mode = ClickMode.ONCE;
                case "--repeat" -> mode = ClickMode.REPEAT;
                case "--hold" -> mode = ClickMode.HOLD;
                case "--stop" -> stop = true;
                default -> {
                    sender.sendMessage(Lang.get("left-click-usage"));
                    return true;
                }
            }
        }

        if (stop) {
            if (!Perm.has(sender, Perm.LEFT_CLICK_STOP)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            cleanupBot(fp.getUuid());
            sender.sendMessage(Lang.get("left-click-stopped", "name", fp.getDisplayName()));
            return true;
        }

        String modePerm =
                switch (mode) {
                    case ONCE -> Perm.LEFT_CLICK_ONCE;
                    case REPEAT -> Perm.LEFT_CLICK_REPEAT;
                    case HOLD -> Perm.LEFT_CLICK_HOLD;
                    default -> Perm.LEFT_CLICK;
                };
        if (!Perm.has(sender, modePerm)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        cancelAll(fp.getUuid());

        Object target = null;
        BlockPos blockTarget = null;
        BlockFace targetFace = null;
        // The exact point the sender is looking at on the block (world coords), so the bot aims at
        // precisely that spot rather than the block-face centre.
        org.bukkit.util.Vector aimPoint = null;

        if (sender instanceof Player player) {
            // The sender's crosshair designates the block to mine. Per-tick behaviour is 1:1 with a real
            // client: the bot attacks whatever entity is directly under ITS OWN crosshair, else digs.
            Block playerTarget = player.getTargetBlockExact((int) Math.ceil(CLICK_REACH));
            if (playerTarget != null && !playerTarget.getType().isAir()) {
                blockTarget = new BlockPos(playerTarget.getX(), playerTarget.getY(), playerTarget.getZ());
                target = playerTarget;
                org.bukkit.util.RayTraceResult ray = player.rayTraceBlocks(CLICK_REACH);
                if (ray != null) {
                    targetFace = ray.getHitBlockFace();
                    aimPoint = ray.getHitPosition();
                }
            }
            dbgHead("execute: sender=" + player.getName() + " aim-block="
                    + (playerTarget != null ? playerTarget.getType() + describePos(playerTarget) : "none"));
        }

        if (target == null) {
            target = rayTraceTarget(bot);
            if (target instanceof Block b) {
                blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
                org.bukkit.util.RayTraceResult ray = bot.rayTraceBlocks(CLICK_REACH);
                if (ray != null) {
                    targetFace = ray.getHitBlockFace();
                    aimPoint = ray.getHitPosition();
                }
            }
            dbgHead("execute: no sender block-target; bot self-raytrace="
                    + (target instanceof Block b2 ? b2.getType() + describePos(b2) : "none"));
        }

        dbg("execute: bot=" + fp.getDisplayName() + " mode=" + mode + " target="
                + (target instanceof Block bl ? "block " + bl.getType() : "none"));

        final ClickMode finalMode = mode;
        // Aim at the exact hit point; fall back to the block-face centre when the raytrace returned no
        // precise hit position.
        final org.bukkit.util.Vector finalAim = aimPoint != null ? aimPoint : computeFaceCenter(target, targetFace);
        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null) {
                double dist = bot.getLocation().distance(targetLoc);
                if (dist <= CLICK_REACH) {
                    lockAndStartClicking(fp, finalMode, target, blockTarget, finalAim);
                    String msgKey =
                            switch (finalMode) {
                                case ONCE -> "left-click-started-once";
                                case REPEAT -> "left-click-started-repeat";
                                case HOLD -> "left-click-started-hold";
                                default -> "left-click-started";
                            };
                    sender.sendMessage(Lang.get(msgKey, "name", fp.getDisplayName()));
                    return true;
                } else {
                    Location standLoc = resolveStandLocation(bot.getWorld(), sender, targetLoc);
                    if (standLoc != null) {
                        final Object finalTarget = target;
                        final BlockPos finalBlockTarget = blockTarget;
                        startNavigation(
                                fp,
                                standLoc,
                                () -> lockAndStartClicking(fp, finalMode, finalTarget, finalBlockTarget, finalAim));
                        sender.sendMessage(Lang.get("left-click-walking", "name", fp.getDisplayName()));
                        return true;
                    } else {
                        sender.sendMessage(Lang.get("left-click-no-path", "name", fp.getDisplayName()));
                        return true;
                    }
                }
            }
        }

        lockAndStartClicking(fp, finalMode, null, null, (org.bukkit.util.Vector) null);
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

        if (args.length == 2 && !args[0].equalsIgnoreCase("--stop")) {
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

    public boolean click(FakePlayer fp, ClickMode mode) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return false;

        cancelAll(fp.getUuid());
        if (mode == ClickMode.STOP) return true;

        Object target = null;
        BlockPos blockTarget = null;
        BlockFace targetFace = null;
        org.bukkit.util.Vector aimPoint = null;

        target = rayTraceTarget(bot);
        if (target instanceof Block b) {
            blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
            org.bukkit.util.RayTraceResult ray = bot.rayTraceBlocks(CLICK_REACH);
            if (ray != null) {
                targetFace = ray.getHitBlockFace();
                aimPoint = ray.getHitPosition();
            }
        }
        dbg("click(): bot=" + fp.getDisplayName() + " mode=" + mode + " block="
                + (target instanceof Block cb ? cb.getType() + describePos(cb) : "none"));

        final org.bukkit.util.Vector aim = aimPoint != null ? aimPoint : computeFaceCenter(target, targetFace);
        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null && bot.getLocation().distance(targetLoc) > CLICK_REACH) {
                Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
                if (standLoc == null) return false;
                final Object finalTarget = target;
                final BlockPos finalBlockTarget = blockTarget;
                startNavigation(fp, standLoc, () -> lockAndStartClicking(fp, mode, finalTarget, finalBlockTarget, aim));
                return true;
            }
        }

        lockAndStartClicking(fp, mode, target, blockTarget, aim);
        return true;
    }

    private void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
        BotPathfinder.PathOptions baseOpts = PathfindingService.resolvePathOptions(fp);
        BotPathfinder.PathOptions opts = new BotPathfinder.PathOptions(
                fp.isNavParkour(), true, fp.isNavPlaceBlocks(), baseOpts.avoidWater(), baseOpts.avoidLava());
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
            FakePlayer fp, ClickMode mode, Object target, BlockPos blockTarget, org.bukkit.util.Vector aim) {
        FppApiImpl.fireTaskEvent(fp, "left-click", FppBotTaskEvent.Action.START);
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null) return;

        if (isSelfTarget(bot, target)) {
            target = null;
        }

        dbg("start clicking: bot=" + fp.getDisplayName() + " mode=" + mode + " target="
                + (target instanceof Block sb ? "block " + sb.getType() + describePos(sb) : "self-view"));

        if (target != null) {
            // Aim at the exact point the sender was looking at (falls back to the block centre inside
            // faceTowardTarget when aim is null).
            Location faceLoc = faceTowardTarget(bot.getLocation(), target, aim);
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
        state.mode = mode;
        state.holding = false;
        state.progress = 0;
        // The COMMANDED target — the tick loop re-aims the head at this every tick, so knockback or a
        // passing entity can't permanently steer the crosshair off what the bot was told to mine.
        state.aimTarget = target;
        state.aimPoint = aim;
        clickStates.put(uuid, state);
        clickModes.put(uuid, mode);

        final int[] cooldown = {0};
        Player botPlayer = fp.getPlayer();

        int taskId = FppScheduler.runSyncRepeatingWithId(
                plugin,
                botPlayer,
                () -> {
                    Player b = fp.getPlayer();
                    if (b == null || !b.isOnline()) {
                        stopClicking(uuid);
                        return;
                    }

                    if (fp.isInventoryOpen() || fp.isActionsPaused()) {
                        return;
                    }

                    ServerPlayer nms = ((CraftPlayer) b).getHandle();
                    nms.resetLastActionTime();

                    if (cooldown[0] > 0) {
                        cooldown[0]--;
                        return;
                    }

                    boolean acted = tryBreakBlock(nms, state);

                    if (acted) {
                        if (mode == ClickMode.ONCE) {
                            stopClicking(uuid);
                            return;
                        }
                        // Post-break pause for REPEAT/HOLD mining (vanilla destroyDelay ≈ 5 ticks by
                        // default; per-bot/server configurable). Entity attacks are paced solely by
                        // the weapon's attack cooldown instead.
                        if (!state.lastActionWasAttack) {
                            cooldown[0] = fp.resolveLeftClickIntervalTicks();
                        }
                    }
                },
                0L,
                1L);

        clickTasks.put(uuid, taskId);
    }

    private boolean tryBreakBlock(ServerPlayer nms, ClickState state) {
        Player bot = nms.getBukkitEntity();
        state.lastActionWasAttack = false;

        // Keep the crosshair ON the commanded target every tick — like a player holding their aim
        // steady. A passing entity gets vanilla treatment below; once it's gone, the next tick is
        // back on the commanded block instead of wherever the head drifted.
        refreshAim(bot, state);

        // Vanilla nearest-hit priority: a real client holding left-click attacks whatever entity is
        // directly under the crosshair (closer than any block) and only digs when the pick is a block.
        Entity crosshairEntity = rayTraceCrosshairEntity(bot);
        if (crosshairEntity != null && !isSelfTarget(bot, crosshairEntity)) {
            cancelActiveDestroy(nms, state);
            return tryAttackEntity(nms, state, bot, crosshairEntity);
        }

        BlockPos pos = state.blockTarget;
        Direction dir = state.destroyDir;

        // Always re-resolve the block under the bot's crosshair — a real client holding left-click
        // acts on whatever the pick currently sees. This also lets a click that started with no block
        // (entity in front, resumed task, self-view) pick up the block the moment it becomes visible.
        BlockHitResult hit = rayTraceBlockHit(nms);
        if (hit != null) {
            pos = hit.getBlockPos();
            dir = hit.getDirection();
            state.blockTarget = pos;
            state.destroyDir = dir;
        } else if (pos != null && nms.level().getBlockState(pos).isAir()) {
            pos = null;
        }

        if (pos == null) {
            cancelActiveDestroy(nms, state);
            state.blockTarget = null;
            return false;
        }

        BlockState blockState = nms.level().getBlockState(pos);
        if (blockState.isAir()) {
            // The block already broke (server finished digging, or someone else removed it).
            boolean wasDigging = state.activeDestroyPos != null;
            if (wasDigging) dbg("dig: block broke @" + pos.toShortString());
            cancelActiveDestroy(nms, state);
            state.blockTarget = null;
            return wasDigging;
        }

        if (nms.blockActionRestricted(nms.level(), pos, nms.gameMode.getGameModeForPlayer())) {
            dbg("dig: blocked @" + pos.toShortString() + " (blockActionRestricted) — aborting");
            cancelActiveDestroy(nms, state);
            return false;
        }

        // (Re)target: hand a fresh START_DESTROY_BLOCK to the real packet handler. The server does the
        // reach/permission checks, fires BlockBreakEvent, and — for creative or instant-break blocks —
        // destroys the block on this very tick. For multi-tick mining the server accumulates progress
        // in ServerPlayerGameMode#tick(); we send STOP below once our client-faithful predictor agrees.
        if (state.activeDestroyPos == null || !state.activeDestroyPos.equals(pos)) {
            if (state.activeDestroyPos != null) {
                dbg("dig: retarget — abort " + state.activeDestroyPos.toShortString());
                BotClickDispatcher.abortDestroy(nms, state.activeDestroyPos);
            }
            state.activeDestroyPos = pos;
            state.destroyDir = dir;
            state.progress = 0;
            dbg("dig: START " + blockState.getBlock().getName().getString() + " @" + pos.toShortString() + " face="
                    + dir);
            BotClickDispatcher.startDestroy(nms, pos, dir);
            BotClickDispatcher.swing(nms, InteractionHand.MAIN_HAND);
            if (nms.level().getBlockState(pos).isAir()) {
                // Instant-break / creative: the START tick already destroyed it server-side.
                dbg("dig: instant-break completed @" + pos.toShortString());
                cancelActiveDestroy(nms, state);
                state.blockTarget = null;
                return true;
            }
            return false;
        }

        // Continuing to dig the same block: keep the arm swinging and advance the destroy predictor
        // exactly as a real client does to decide when to send STOP_DESTROY_BLOCK.
        BotClickDispatcher.swing(nms, InteractionHand.MAIN_HAND);
        state.progress += blockState.getDestroyProgress(nms, nms.level(), pos);
        if (state.progress >= 1.0F) {
            dbg("dig: STOP (predictor complete) @" + pos.toShortString());
            BotClickDispatcher.stopDestroy(nms, pos, dir);
            cancelActiveDestroy(nms, state);
            state.blockTarget = null;
            return true;
        }
        if (Config.debugLeftClick()) {
            dbg(String.format("dig: progress %.2f @%s", state.progress, pos.toShortString()));
        }
        return false;
    }

    /** Aborts any in-progress server-side dig and clears the local predictor. */
    private void cancelActiveDestroy(ServerPlayer nms, ClickState state) {
        if (state.activeDestroyPos != null) {
            BotClickDispatcher.abortDestroy(nms, state.activeDestroyPos);
            state.activeDestroyPos = null;
        }
        state.progress = 0;
    }

    @Nullable
    private BlockHitResult rayTraceBlockHit(ServerPlayer nms) {
        try {
            Vec3 eyePos = nms.getEyePosition(1.0F);
            Vec3 lookDir = nms.getLookAngle();
            Vec3 endPos = eyePos.add(lookDir.scale(CLICK_REACH));
            BlockHitResult result = nms.level()
                    .clip(new net.minecraft.world.level.ClipContext(
                            eyePos,
                            endPos,
                            net.minecraft.world.level.ClipContext.Block.OUTLINE,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                            nms));
            if (result.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                return result;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Returns the entity directly under the bot's crosshair, but only when it is the <em>nearest</em>
     * hit — the combined block+entity ray trace returns whichever the look vector reaches first, so an
     * entity behind the aimed block is never picked. This is exactly how a real client resolves a
     * left-click target.
     */
    @Nullable
    private Entity rayTraceCrosshairEntity(Player bot) {
        try {
            org.bukkit.util.RayTraceResult result = bot.getWorld()
                    .rayTrace(
                            bot.getEyeLocation(),
                            bot.getEyeLocation().getDirection(),
                            CLICK_REACH,
                            org.bukkit.FluidCollisionMode.NEVER,
                            false,
                            0.0,
                            e -> e != null
                                    && e.isValid()
                                    && !e.isDead()
                                    && !isSelfTarget(bot, e)
                                    && isValidAttackTarget(e));
            if (result != null) return result.getHitEntity();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Mirrors the server's own {@code handleAttack} guard: attacking a dropped item, an XP orb, or an
     * arrow gets the client disconnected with "invalid entity attacked" — a real client never lets you
     * target these with left-click, but our raw crosshair ray trace otherwise would (most commonly
     * dropped items floating next to the block a bot is mining). Excluding them here keeps left-click
     * from ever sending an attack packet the server would kick the bot over.
     */
    private static boolean isValidAttackTarget(Entity entity) {
        return !(entity instanceof org.bukkit.entity.Item)
                && !(entity instanceof org.bukkit.entity.ExperienceOrb)
                && !(entity instanceof org.bukkit.entity.AbstractArrow);
    }

    /** Ticks between full-strength hits, from the bot's real attack-speed attribute. */
    private static int getWeaponCooldown(Player bot) {
        double speed = 4.0;
        try {
            var attr = bot.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
            if (attr != null) speed = attr.getValue();
        } catch (Exception ignored) {
        }
        if (speed <= 0) speed = 4.0;
        return (int) (20.0 / speed);
    }

    private boolean tryAttackEntity(ServerPlayer nms, ClickState state, Player bot, Entity target) {
        // The weapon's attack speed is the ONLY pacing for attacks (the loop's post-action cooldown is
        // skipped via lastActionWasAttack), so held attacks swing at exact vanilla attack-cooldown pace.
        if (state.entityCooldown > 0) {
            state.entityCooldown--;
            return false;
        }

        // Real left-click attack: swing (broadcast to viewers via handleAnimate), then feed a
        // ServerboundAttackPacket through the bot's own packet handler so the server applies its reach
        // check, the attack-strength cooldown, damage, knockback, sweep, enchantments and durability
        // exactly as for a real player. Every hit lands at full strength.
        dbg("attack: " + target.getType() + " (swing + ServerboundAttackPacket)");
        BotClickDispatcher.swing(nms, InteractionHand.MAIN_HAND);
        BotClickDispatcher.attack(nms, target);

        state.entityCooldown = getWeaponCooldown(bot);
        state.lastActionWasAttack = true;
        return true;
    }

    private static boolean isSelfTarget(Player bot, Object target) {
        return bot != null
                && target instanceof Entity entity
                && entity.getUniqueId().equals(bot.getUniqueId());
    }

    private void cancelAll(UUID botUuid) {
        // Only release the nav slot if left-click's own walk-to-vantage currently owns it — another
        // concurrently running task (move, find, PVE) may hold it instead, and resetting *this* bot's
        // click task must never cancel someone else's navigation.
        if (pathfinding.isNavigating(botUuid, PathfindingService.Owner.MINE)) {
            pathfinding.cancel(botUuid);
        }
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
            dbg("stop: bot=" + fp.getDisplayName() + " clearState=" + clearState);
            FppApiImpl.fireTaskEvent(fp, "left-click", FppBotTaskEvent.Action.STOP);
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
                ServerPlayer nms = ((CraftPlayer) bot).getHandle();
                ClickState state = clickStates.get(botUuid);
                if (state != null && state.activeDestroyPos != null) {
                    // Cancel any dig the server still thinks is in progress so no ghost cracks linger.
                    dbg("stop: abort active dig @" + state.activeDestroyPos.toShortString());
                    BotClickDispatcher.abortDestroy(nms, state.activeDestroyPos);
                    state.activeDestroyPos = null;
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

    /**
     * Snapshot of the bot's active left-click task for persistence, or null when it isn't clicking.
     */
    @Nullable
    public SavedClickTask getSavedTask(UUID botUuid) {
        ClickMode mode = clickModes.get(botUuid);
        if (mode == null || mode == ClickMode.STOP || !clickTasks.containsKey(botUuid)) return null;
        FakePlayer fp = manager.getByUuid(botUuid);
        Player bot = fp != null ? fp.getPlayer() : null;
        if (bot == null) return null;
        ClickState state = clickStates.get(botUuid);
        return new SavedClickTask(mode.name(), bot.getWorld().getName(), state != null ? state.aimPoint : null);
    }

    /**
     * Resumes a persisted left-click task after a restart: re-aims the bot at the saved point (when
     * one was locked) and restarts the click loop via the normal self-view resolution.
     */
    public void resumeSavedTask(FakePlayer fp, String modeName, @Nullable org.bukkit.util.Vector aimPoint) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        ClickMode mode;
        try {
            mode = ClickMode.valueOf(modeName);
        } catch (IllegalArgumentException | NullPointerException e) {
            mode = ClickMode.HOLD;
        }
        if (mode == ClickMode.STOP) return;
        if (aimPoint != null) {
            Location faceLoc = faceTowardTarget(bot.getLocation(), null, aimPoint);
            ((CraftPlayer) bot).getHandle().absSnapRotationTo(faceLoc.getYaw(), faceLoc.getPitch());
            NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
        }
        dbg("resume: bot=" + fp.getDisplayName() + " mode=" + mode + " aim=" + aimPoint);
        click(fp, mode);
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
            Location faceLoc = faceTowardTarget(bot.getLocation(), target, null);
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
            state.aimTarget = target;
            if (target instanceof Block b) {
                state.blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
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

                    if (acted) {
                        if (finalMode == ClickMode.ONCE) {
                            stopClicking(fp.getUuid());
                            return;
                        }
                        if (finalMode == ClickMode.REPEAT && !finalState.lastActionWasAttack) {
                            cooldown[0] = fp.resolveLeftClickIntervalTicks();
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
            org.bukkit.util.RayTraceResult result = bot.getWorld()
                    .rayTraceBlocks(eye, eye.getDirection(), CLICK_REACH, org.bukkit.FluidCollisionMode.NEVER, false);
            if (result != null && result.getHitBlock() != null) {
                return result.getHitBlock();
            }
        } catch (Exception ignored) {
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

    private Location faceTowardTarget(Location botLoc, Object target, org.bukkit.util.Vector faceCenter) {
        double tx, ty, tz;
        if (faceCenter != null) {
            tx = faceCenter.getX();
            ty = faceCenter.getY();
            tz = faceCenter.getZ();
        } else if (target instanceof Block b) {
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

    /**
     * Computes the geometric center of a specific block face.
     */
    private static org.bukkit.util.Vector computeFaceCenter(Object target, BlockFace face) {
        if (!(target instanceof Block b) || face == null) return null;
        double cx = b.getX() + 0.5;
        double cy = b.getY() + 0.5;
        double cz = b.getZ() + 0.5;
        return switch (face) {
            case UP -> new org.bukkit.util.Vector(cx, b.getY() + 1.0, cz);
            case DOWN -> new org.bukkit.util.Vector(cx, b.getY(), cz);
            case NORTH -> new org.bukkit.util.Vector(cx, cy, b.getZ());
            case SOUTH -> new org.bukkit.util.Vector(cx, cy, b.getZ() + 1.0);
            case WEST -> new org.bukkit.util.Vector(b.getX(), cy, cz);
            case EAST -> new org.bukkit.util.Vector(b.getX() + 1.0, cy, cz);
            default -> new org.bukkit.util.Vector(cx, cy, cz);
        };
    }

    @Nullable
    private Location findStandLocationNearTarget(World world, Location targetLoc) {
        int tx = targetLoc.getBlockX(), ty = targetLoc.getBlockY(), tz = targetLoc.getBlockZ();
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) < r && Math.abs(dz) < r) continue;
                    int cx = tx + dx, cz = tz + dz;
                    for (int dy : new int[] {0, -1, 1}) {
                        int cy = ty + dy;
                        if (BotNavUtil.walkable(world, cx, cy, cz)) {
                            Location loc = new Location(world, cx + 0.5, cy, cz + 0.5);
                            double dist = loc.distance(targetLoc);
                            if (dist <= CLICK_REACH - 1.5) {
                                return faceTowardTarget(loc, targetLoc, null);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds a walkable stand spot around {@code center} (e.g. the command sender's own location) from
     * which the target is within reach. Includes the centre block itself (r=0) so the bot can stand
     * exactly where the player is standing.
     */
    @Nullable
    private Location findStandLocationNear(World world, Location center, Location targetLoc) {
        int ox = center.getBlockX(), oy = center.getBlockY(), oz = center.getBlockZ();
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) < r && Math.abs(dz) < r) continue;
                    int cx = ox + dx, cz = oz + dz;
                    for (int dy : new int[] {0, -1, 1}) {
                        int cy = oy + dy;
                        if (BotNavUtil.walkable(world, cx, cy, cz)) {
                            Location loc = new Location(world, cx + 0.5, cy, cz + 0.5);
                            if (loc.distance(targetLoc) <= CLICK_REACH - 0.5) {
                                return faceTowardTarget(loc, targetLoc, null);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves where the bot should stand to reach an out-of-reach target. Prefers the sender's own
     * standing location — a vantage the target is provably aim-able from, since the player just aimed
     * at it from there — and falls back to searching around the target itself.
     */
    @Nullable
    private Location resolveStandLocation(World world, CommandSender sender, Location targetLoc) {
        if (sender instanceof Player player && player.getWorld() == world) {
            Location atPlayer = findStandLocationNear(world, player.getLocation(), targetLoc);
            if (atPlayer != null) return atPlayer;
        }
        return findStandLocationNearTarget(world, targetLoc);
    }

    /**
     * Re-faces the bot toward its commanded target (exact aim point, else target centre). Rotates via
     * NMS {@code absSnapRotationTo} — NOT {@code CraftPlayer#setRotation}, which does a connection
     * teleport that arms {@code awaitingPositionFromClient} and blocks all block interactions until
     * confirmed.
     */
    private void refreshAim(Player bot, ClickState state) {
        if (state.aimTarget == null && state.aimPoint == null) return;
        Location faceLoc = faceTowardTarget(bot.getLocation(), state.aimTarget, state.aimPoint);
        ((CraftPlayer) bot).getHandle().absSnapRotationTo(faceLoc.getYaw(), faceLoc.getPitch());
        NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
    }

    private static final class ClickState {
        Object target;
        BlockPos blockTarget;
        ClickMode mode;
        boolean holding;
        float progress;
        // Ticks until the next full-strength attack (vanilla attack-speed pacing).
        int entityCooldown;
        // True when the last acted pulse was an entity attack — those skip the post-break pause and
        // are paced purely by entityCooldown.
        boolean lastActionWasAttack;
        // The block the server is currently digging (START_DESTROY_BLOCK sent, not yet finished), and
        // the face the dig was started from. Null when no dig is in progress.
        BlockPos activeDestroyPos;
        Direction destroyDir = Direction.DOWN;
        // The commanded target + exact aim point, set once at start and never overwritten by per-tick
        // picks. Used to re-aim the head every tick.
        Object aimTarget;
        org.bukkit.util.Vector aimPoint;
    }
}
