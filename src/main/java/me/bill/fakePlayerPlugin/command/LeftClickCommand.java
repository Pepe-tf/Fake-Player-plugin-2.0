package me.bill.fakePlayerPlugin.command;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppClickMode;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotClickDispatcher;
import me.bill.fakePlayerPlugin.fakeplayer.BotToolSelector;
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

/**
 * Left-click: attacks whatever entity the bot's crosshair is on, else mines blocks, exactly like a real
 * client - packet-faithful destroy-progress prediction, weapon-attack-speed pacing, and (when enabled)
 * auto-equipping the best available tool before a dig starts. Shared task/state/geometry machinery lives
 * in {@link AbstractClickCommand}.
 */
public final class LeftClickCommand extends AbstractClickCommand {

    private static final double CLICK_REACH = 5.0;

    /** Valid range for a bot's configurable left-click interval (server default or per-bot override). */
    public static final int MIN_INTERVAL_TICKS = 1;

    public static final int MAX_INTERVAL_TICKS = 40;

    public LeftClickCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        super(plugin, manager, pathfinding);
    }

    @Override
    protected double clickReach() {
        return CLICK_REACH;
    }

    @Override
    protected PathfindingService.Owner navOwner() {
        return PathfindingService.Owner.MINE;
    }

    @Override
    protected double navArrivalDistance() {
        return 0.5;
    }

    @Override
    protected String taskName() {
        return "left-click";
    }

    @Override
    protected void dbg(String msg) {
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

        FppClickMode mode = FppClickMode.ONCE;
        boolean stop = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            switch (a) {
                case "--once" -> mode = FppClickMode.ONCE;
                case "--repeat" -> mode = FppClickMode.REPEAT;
                case "--hold" -> mode = FppClickMode.HOLD;
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
            target = selfRayTraceTarget(bot);
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

        final FppClickMode finalMode = mode;
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
    public boolean click(FakePlayer fp, FppClickMode mode) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return false;

        cancelAll(fp.getUuid());
        if (mode == FppClickMode.STOP) return true;

        Object target;
        BlockPos blockTarget = null;
        BlockFace targetFace = null;
        org.bukkit.util.Vector aimPoint = null;

        target = selfRayTraceTarget(bot);
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

    private void lockAndStartClicking(
            FakePlayer fp, FppClickMode mode, Object target, BlockPos blockTarget, org.bukkit.util.Vector aim) {
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

        LeftClickState state = new LeftClickState();
        state.target = target;
        state.blockTarget = blockTarget;
        // The COMMANDED target - the tick loop re-aims the head at this every tick, so knockback or a
        // passing entity can't permanently steer the crosshair off what the bot was told to mine.
        state.aimTarget = target;
        state.aimPoint = aim;
        clickStates.put(uuid, state);
        clickModes.put(uuid, mode);

        final int[] cooldown = {0};
        final FppClickMode finalMode = mode;
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
                        if (finalMode == FppClickMode.ONCE) {
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

    private boolean tryBreakBlock(ServerPlayer nms, LeftClickState state) {
        Player bot = nms.getBukkitEntity();
        state.lastActionWasAttack = false;

        // Keep the crosshair ON the commanded target every tick - like a player holding their aim
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

        // Always re-resolve the block under the bot's crosshair - a real client holding left-click
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
            dbg("dig: blocked @" + pos.toShortString() + " (blockActionRestricted) - aborting");
            cancelActiveDestroy(nms, state);
            return false;
        }

        // (Re)target: hand a fresh START_DESTROY_BLOCK to the real packet handler. The server does the
        // reach/permission checks, fires BlockBreakEvent, and - for creative or instant-break blocks -
        // destroys the block on this very tick. For multi-tick mining the server accumulates progress
        // in ServerPlayerGameMode#tick(); we send STOP below once our client-faithful predictor agrees.
        if (state.activeDestroyPos == null || !state.activeDestroyPos.equals(pos)) {
            if (state.activeDestroyPos != null) {
                dbg("dig: retarget - abort " + state.activeDestroyPos.toShortString());
                BotClickDispatcher.abortDestroy(nms, state.activeDestroyPos);
            }
            if (Config.autoToolSwitchEnabled()) {
                BotToolSelector.equipBestTool(bot, pos);
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
    private void cancelActiveDestroy(ServerPlayer nms, LeftClickState state) {
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
     * hit - the combined block+entity ray trace returns whichever the look vector reaches first, so an
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
     * arrow gets the client disconnected with "invalid entity attacked" - a real client never lets you
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

    private boolean tryAttackEntity(ServerPlayer nms, LeftClickState state, Player bot, Entity target) {
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

    @Override
    protected void onBeforeStop(FakePlayer fp, Player bot, @Nullable ClickState state) {
        if (state instanceof LeftClickState left && left.activeDestroyPos != null) {
            // Cancel any dig the server still thinks is in progress so no ghost cracks linger.
            dbg("stop: abort active dig @" + left.activeDestroyPos.toShortString());
            ServerPlayer nms = ((CraftPlayer) bot).getHandle();
            BotClickDispatcher.abortDestroy(nms, left.activeDestroyPos);
            left.activeDestroyPos = null;
        }
    }

    public void resumeClicking(FakePlayer fp) {
        FppClickMode mode = clickModes.get(fp.getUuid());
        if (mode == null) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        LeftClickState state = (LeftClickState) clickStates.get(fp.getUuid());
        Object target = state != null ? state.target : selfRayTraceTarget(bot);
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
            state = new LeftClickState();
            state.target = target;
            state.aimTarget = target;
            if (target instanceof Block b) {
                state.blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
            }
            clickStates.put(fp.getUuid(), state);
        }
        final LeftClickState finalState = state;
        final FppClickMode finalMode = mode;

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
                        if (finalMode == FppClickMode.ONCE) {
                            stopClicking(fp.getUuid());
                            return;
                        }
                        if (finalMode == FppClickMode.REPEAT && !finalState.lastActionWasAttack) {
                            cooldown[0] = fp.resolveLeftClickIntervalTicks();
                        }
                    }
                },
                0L,
                1L);
        clickTasks.put(fp.getUuid(), newTask);
    }

    @Nullable
    private Object selfRayTraceTarget(Player bot) {
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

    /** Left-click's per-bot click-loop state: mining destroy-progress + entity-attack pacing. */
    private static final class LeftClickState extends ClickState {
        BlockPos blockTarget;
        float progress;
        // Ticks until the next full-strength attack (vanilla attack-speed pacing).
        int entityCooldown;
        // True when the last acted pulse was an entity attack - those skip the post-break pause and
        // are paced purely by entityCooldown.
        boolean lastActionWasAttack;
        // The block the server is currently digging (START_DESTROY_BLOCK sent, not yet finished), and
        // the face the dig was started from. Null when no dig is in progress.
        BlockPos activeDestroyPos;
        Direction destroyDir = Direction.DOWN;
    }
}
