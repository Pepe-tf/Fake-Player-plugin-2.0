package me.bill.fakePlayerPlugin.command;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.FppClickMode;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotClickDispatcher;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click: interacts with whatever the bot aims at (or uses the held item), exactly like a real
 * client - real interact/use-item packets drive the server's own event/result logic, main hand tried
 * first and the off hand retried whenever the main hand's action passes (mirrors
 * {@code Minecraft.startUseItem}'s own hand loop, so a shield or food carried in the off hand actually
 * gets used). Shared task/state/geometry machinery lives in {@link AbstractClickCommand}.
 */
public final class RightClickCommand extends AbstractClickCommand {

    private static final double CLICK_REACH = 4.5;
    // The tick loop's own period *is* the interval (server/per-bot configurable, defaulting to 4
    // ticks - the vanilla client's own rightClickDelay), so no extra pulses are skipped after an
    // action (0 = act on every pulse of that period).
    private static final int CLICK_COOLDOWN = 0;

    /** Valid range for a bot's configurable right-click interval (server default or per-bot override). */
    public static final int MIN_INTERVAL_TICKS = 1;

    public static final int MAX_INTERVAL_TICKS = 40;

    public RightClickCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        super(plugin, manager, pathfinding);
    }

    @Override
    protected double clickReach() {
        return CLICK_REACH;
    }

    @Override
    protected PathfindingService.Owner navOwner() {
        return PathfindingService.Owner.USE;
    }

    @Override
    protected double navArrivalDistance() {
        return 0.35;
    }

    @Override
    protected String taskName() {
        return "right-click";
    }

    @Override
    protected void dbg(String msg) {
        FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), msg);
    }

    @Override
    public String getName() {
        return "right-click";
    }

    @Override
    public String getUsage() {
        return "<bot> [--once|--repeat|--hold|--stop]  |  --stop";
    }

    @Override
    public String getDescription() {
        return "Bot right-clicks like a real player (interacts with what it aims at, uses items). Default: --once";
    }

    @Override
    public String getPermission() {
        return Perm.RIGHT_CLICK;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.hasAny(
                sender,
                Perm.RIGHT_CLICK,
                Perm.RIGHT_CLICK_START,
                Perm.RIGHT_CLICK_ONCE,
                Perm.RIGHT_CLICK_REPEAT,
                Perm.RIGHT_CLICK_HOLD,
                Perm.RIGHT_CLICK_STOP);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("right-click-usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("--stop") && args.length == 1) {
            if (!Perm.has(sender, Perm.RIGHT_CLICK_STOP)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            stopAll();
            sender.sendMessage(Lang.get("right-click-stopped-all"));
            return true;
        }

        String botName = args[0];
        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("right-click-not-found", "name", botName));
            return true;
        }

        if (sender instanceof Player player && !Perm.has(sender, Perm.ADMIN)) {
            if (!BotAccess.canAdminister(player, fp)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
        }

        FppClickMode mode = FppClickMode.ONCE;
        if (args.length >= 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            switch (action) {
                case "--once" -> mode = FppClickMode.ONCE;
                case "--repeat" -> mode = FppClickMode.REPEAT;
                case "--hold" -> mode = FppClickMode.HOLD;
                case "--stop" -> {
                    if (!Perm.has(sender, Perm.RIGHT_CLICK_STOP)) {
                        sender.sendMessage(Lang.get("no-permission"));
                        return true;
                    }
                    cleanupBot(fp.getUuid());
                    sender.sendMessage(Lang.get("right-click-stopped", "name", fp.getDisplayName()));
                    return true;
                }
                default -> {
                    sender.sendMessage(Lang.get("right-click-usage"));
                    return true;
                }
            }
        }

        // Always gate on the resolved mode's permission - even a bare "/fpp right-click <bot>" (no
        // flag, defaults to --once) must hold fpp.right-click.once, not just the broad canUse() gate.
        String modePerm =
                switch (mode) {
                    case ONCE -> Perm.RIGHT_CLICK_ONCE;
                    case REPEAT -> Perm.RIGHT_CLICK_REPEAT;
                    case HOLD -> Perm.RIGHT_CLICK_HOLD;
                    default -> Perm.RIGHT_CLICK;
                };
        if (!Perm.has(sender, modePerm)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("right-click-bot-offline", "name", fp.getDisplayName()));
            return true;
        }

        cancelAll(fp.getUuid());

        Object target = null;
        BlockFace targetFace = null;
        // Exact point the sender is looking at on the block, so the bot aims precisely there.
        org.bukkit.util.Vector aimPoint = null;
        if (sender instanceof Player player) {
            target = rayTraceTargetPlayer(player);
            if (isSelfTarget(bot, target)) {
                target = null;
            }
            if (target instanceof Block) {
                org.bukkit.util.RayTraceResult ray = player.rayTraceBlocks(CLICK_REACH);
                if (ray != null) {
                    targetFace = ray.getHitBlockFace();
                    aimPoint = ray.getHitPosition();
                }
            }
            if (Config.debugRightClickHead()) {
                if (target != null) {
                    String tStr = formatTarget(target);
                    FppLogger.debug(
                            "RIGHTCLICK-HEAD",
                            true,
                            bot.getName() + " player target=" + tStr
                                    + (targetFace != null ? " face=" + targetFace.name() : ""));
                } else {
                    FppLogger.debug("RIGHTCLICK-HEAD", true, bot.getName() + " player raytrace=null");
                }
            }
        }
        if (target == null) {
            target = selfRayTraceTarget(bot);
            if (isSelfTarget(bot, target)) {
                target = null;
            }
            if (target instanceof Block) {
                org.bukkit.util.RayTraceResult ray = bot.rayTraceBlocks(CLICK_REACH);
                if (ray != null) {
                    targetFace = ray.getHitBlockFace();
                    aimPoint = ray.getHitPosition();
                }
            }
            if (Config.debugRightClickHead() && target != null) {
                FppLogger.debug(
                        "RIGHTCLICK-HEAD",
                        true,
                        bot.getName() + " bot self-target=" + formatTarget(target)
                                + (targetFace != null ? " face=" + targetFace.name() : ""));
            }
        }

        final FppClickMode finalMode = mode;
        final org.bukkit.util.Vector finalAim = aimPoint != null ? aimPoint : computeFaceCenter(target, targetFace);
        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null) {
                double dist = bot.getLocation().distance(targetLoc);
                if (dist <= CLICK_REACH) {
                    lockAndStartClicking(fp, finalMode, target, finalAim);
                    String msgKey =
                            switch (finalMode) {
                                case ONCE -> "right-click-started-once";
                                case REPEAT -> "right-click-started-repeat";
                                case HOLD -> "right-click-started-hold";
                                default -> "right-click-started";
                            };
                    sender.sendMessage(Lang.get(msgKey, "name", fp.getDisplayName()));
                    return true;
                } else {
                    Location standLoc = resolveStandLocation(bot.getWorld(), sender, targetLoc);
                    if (standLoc != null) {
                        final Object finalTarget = target;
                        startNavigation(fp, standLoc, () -> lockAndStartClicking(fp, finalMode, finalTarget, finalAim));
                        sender.sendMessage(Lang.get("right-click-walking", "name", fp.getDisplayName()));
                        return true;
                    } else {
                        sender.sendMessage(Lang.get("right-click-no-path", "name", fp.getDisplayName()));
                        return true;
                    }
                }
            }
        }

        lockAndStartClicking(fp, finalMode, null, (org.bukkit.util.Vector) null);
        sender.sendMessage(Lang.get("right-click-started", "name", fp.getDisplayName()));
        return true;
    }

    @Override
    public boolean click(FakePlayer fp, FppClickMode mode) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return false;

        cancelAll(fp.getUuid());
        if (mode == FppClickMode.STOP) return true;

        Object target;
        BlockFace targetFace = null;
        org.bukkit.util.Vector aimPoint = null;
        target = selfRayTraceTarget(bot);
        if (target instanceof Block) {
            org.bukkit.util.RayTraceResult ray = bot.rayTraceBlocks(CLICK_REACH);
            if (ray != null) {
                targetFace = ray.getHitBlockFace();
                aimPoint = ray.getHitPosition();
            }
        }

        final org.bukkit.util.Vector aim = aimPoint != null ? aimPoint : computeFaceCenter(target, targetFace);
        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null && bot.getLocation().distance(targetLoc) > CLICK_REACH) {
                Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
                if (standLoc == null) return false;
                final Object finalTarget = target;
                startNavigation(fp, standLoc, () -> lockAndStartClicking(fp, mode, finalTarget, aim));
                return true;
            }
        }

        lockAndStartClicking(fp, mode, target, aim);
        return true;
    }

    private void lockAndStartClicking(FakePlayer fp, FppClickMode mode, Object target, org.bukkit.util.Vector aim) {
        dbg("start clicking: bot=" + fp.getDisplayName() + " mode=" + mode + " target="
                + (target instanceof Block sb ? "block " + sb.getType() : target != null ? "entity" : "self-view"));
        FppApiImpl.fireTaskEvent(fp, "right-click", FppBotTaskEvent.Action.START);
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null) return;

        if (isSelfTarget(bot, target)) {
            target = null;
        }

        float startYaw = bot.getLocation().getYaw();
        float startPitch = bot.getLocation().getPitch();

        if (target != null) {
            // Aim at the exact point the sender was looking at (falls back to entity/block centre
            // inside faceTowardTarget when aim is null).
            Location faceLoc = faceTowardTarget(bot.getLocation(), target, aim);
            bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
            NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
            if (Config.debugRightClickHead()) {
                FppLogger.debug(
                        "RIGHTCLICK-HEAD",
                        true,
                        bot.getName() + " target=" + target.getClass().getSimpleName() + " from yaw="
                                + String.format("%.2f", startYaw) + " pitch=" + String.format("%.2f", startPitch)
                                + " to yaw="
                                + String.format("%.2f", faceLoc.getYaw()) + " pitch="
                                + String.format("%.2f", faceLoc.getPitch()));
            }
        } else {
            if (Config.debugRightClickHead()) {
                FppLogger.debug(
                        "RIGHTCLICK-HEAD",
                        true,
                        bot.getName() + " NO TARGET - yaw=" + String.format("%.2f", startYaw) + " pitch="
                                + String.format("%.2f", startPitch));
            }
        }
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);

        Location actualLoc = bot.getLocation().clone();
        manager.lockForAction(uuid, actualLoc, false);

        RightClickState state = new RightClickState();
        state.target = target;
        // Seed with the sender's exact aim point so the first interaction targets that spot; the tick
        // loop refreshes it from the bot's own raytrace afterwards.
        state.hitPosition = aim;
        // The COMMANDED target - never overwritten by transient per-tick picks. The tick loop re-aims
        // the bot's head at this every pulse, so knockback or a passing entity can't permanently steer
        // the crosshair off what the bot was told to click.
        state.aimTarget = target;
        state.aimPoint = aim;
        clickStates.put(uuid, state);
        clickModes.put(uuid, mode);

        final int[] cooldown = {0};
        final FppClickMode finalMode = mode;
        Player botPlayer = fp.getPlayer();
        long intervalTicks = fp.resolveRightClickIntervalTicks();

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

                    if (nms.isUsingItem()) {
                        if (finalMode == FppClickMode.ONCE) {
                            stopClicking(uuid);
                        }
                        return;
                    }

                    if (cooldown[0] > 0) {
                        cooldown[0]--;
                        return;
                    }

                    boolean acted = performUseAction(b, state);

                    if (acted) {
                        if (finalMode == FppClickMode.ONCE) {
                            stopClicking(uuid);
                            return;
                        }
                        if (finalMode == FppClickMode.REPEAT || finalMode == FppClickMode.HOLD) {
                            cooldown[0] = CLICK_COOLDOWN;
                        }
                    }
                },
                0L,
                intervalTicks);

        clickTasks.put(uuid, taskId);
    }

    /**
     * Resolves the bot's crosshair once, then tries the interaction hand-by-hand - main hand first,
     * off hand retried only if the main hand's attempt passed with no effect - exactly mirroring
     * {@code Minecraft.startUseItem}'s own loop. Each hand attempt runs the vanilla per-hand order:
     * entity-interact (if an entity is the nearest hit), else use-item-on-block, and - if that passed
     * too - use the item in that hand.
     */
    private boolean performUseAction(Player bot, RightClickState state) {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();

        // Keep the crosshair ON the commanded target every pulse - like a player holding their aim
        // steady. A passing entity gets vanilla treatment below; once it's gone, the very next pulse
        // is back on the commanded block instead of wherever the head drifted.
        refreshAim(bot, state);

        // Combined block+entity ray trace - vanilla nearest-hit priority: whichever the look vector
        // reaches first wins, exactly like a real client's pick. An entity behind the aimed block is
        // never chosen; an entity in front of it is interacted with first.
        RayTraceResult hit = bot.getWorld()
                .rayTrace(
                        bot.getEyeLocation(),
                        bot.getEyeLocation().getDirection(),
                        CLICK_REACH,
                        FluidCollisionMode.NEVER,
                        false,
                        0.0,
                        entity -> entity != null && entity.isValid() && !entity.isDead() && !isSelfTarget(bot, entity));

        Entity hitEntity = hit != null ? hit.getHitEntity() : null;
        Block hitBlock = hit != null ? hit.getHitBlock() : null;
        BlockFace face = hit != null ? hit.getHitBlockFace() : null;
        dbg("use: bot=" + bot.getName() + " crosshair="
                + (hitEntity != null
                        ? "entity " + hitEntity.getType()
                        : hitBlock != null ? "block " + hitBlock.getType() + " face=" + face : "air"));

        boolean ridingBefore = bot.isInsideVehicle();

        // Main hand first; off hand is retried only when the main hand's whole attempt passed with no
        // observable effect (a real client never touches the off hand once the main hand did something).
        for (InteractionHand hand : InteractionHand.values()) {
            if (attemptHandUse(nms, bot, state, hand, hitEntity, hitBlock, face, hit, ridingBefore)) {
                return true;
            }
        }
        return false;
    }

    private boolean attemptHandUse(
            ServerPlayer nms,
            Player bot,
            RightClickState state,
            InteractionHand hand,
            @Nullable Entity hitEntity,
            @Nullable Block hitBlock,
            @Nullable BlockFace face,
            @Nullable RayTraceResult hit,
            boolean ridingBefore) {
        boolean usingBefore = nms.isUsingItem();
        ItemStack handItemBefore = nms.getItemInHand(hand).copy();

        // 1. Entity is the nearest hit → real interact packet, exactly like a vanilla client. The
        //    server fires PlayerInteractEntityEvent/AtEntity and runs interactOn (armor-stand equip,
        //    feeding, taming, breeding, shearing, milking, mounting, leashing, trading). If the
        //    interaction PASSes (e.g. non-equippable item on an armless armor stand), fall through to
        //    the in-hand item use - the same order Minecraft.startUseItem follows.
        if (hitEntity != null) {
            org.bukkit.util.Vector hp = hit != null ? hit.getHitPosition() : null;
            Vec3 hitVec = hp != null ? new Vec3(hp.getX(), hp.getY(), hp.getZ()) : null;
            dbg("use: interactEntity " + hitEntity.getType() + " hand=" + hand + " (ServerboundInteractPacket)");
            BotClickDispatcher.interactEntity(nms, hitEntity, hand, hitVec, nms.isShiftKeyDown());
            // The vanilla client hardcodes interactAt on a non-marker armor stand as SUCCESS
            // client-side, so a real player's click is always consumed there - they can never eat/use
            // the held item while aiming at an armor stand, even when the server-side swap does
            // nothing. Mirror that here.
            boolean consumed = hitEntity instanceof org.bukkit.entity.ArmorStand
                    || didConsume(nms, usingBefore, handItemBefore, hand)
                    || bot.isInsideVehicle() != ridingBefore;
            if (consumed) {
                dbg("use: entity interaction consumed (hand=" + hand + ")");
                // A real client swings its own arm on a successful interaction; the server only
                // broadcasts SERVER-sourced swings, so the bot must send the swing itself.
                BotClickDispatcher.swing(nms, hand);
                state.target = hitEntity;
                return true;
            }
            dbg("use: entity interaction passed (hand=" + hand + ") - falling through to in-hand use");
        }

        // 2. Use the item on the block. A ServerboundUseItemOn packet drives the real useItemOn path,
        //    which fires PlayerInteractEvent/BlockPlaceEvent and natively handles block interaction,
        //    placement, seed/sapling planting, bone meal, hoe tilling, bucket use, etc. - no per-item
        //    table, no directly setting blocks in the world. Skipped once an entity was the pick - a
        //    real client never also clicks the block behind it.
        if (hitEntity == null && hitBlock != null && face != null) {
            Direction dir = toDirection(face);
            BlockPos pos = new BlockPos(hitBlock.getX(), hitBlock.getY(), hitBlock.getZ());
            BlockPos placePos = pos.relative(dir);
            // Snapshot the clicked block and the cell a block would be placed into, so we can tell the
            // interaction did something even when the held item count is unchanged (e.g. creative
            // placement, or opening a door/lever).
            net.minecraft.world.level.block.state.BlockState before =
                    nms.level().getBlockState(pos);
            net.minecraft.world.level.block.state.BlockState placeBefore =
                    nms.level().getBlockState(placePos);
            org.bukkit.util.Vector hp = hit != null ? hit.getHitPosition() : null;
            Vec3 hitVec = hp != null
                    ? new Vec3(hp.getX(), hp.getY(), hp.getZ())
                    : new Vec3(hitBlock.getX() + 0.5, hitBlock.getY() + 0.5, hitBlock.getZ() + 0.5);
            BlockHitResult blockHit = new BlockHitResult(hitVec, dir, pos, false);
            state.hitPosition = hp;
            state.target = hitBlock;
            dbg("use: useItemOn " + hitBlock.getType() + " face=" + face + " hand=" + hand
                    + " (ServerboundUseItemOnPacket)");
            BotClickDispatcher.useItemOn(nms, hand, blockHit);
            boolean blockChanged =
                    nms.level().getBlockState(pos) != before || nms.level().getBlockState(placePos) != placeBefore;
            if (blockChanged || didConsume(nms, usingBefore, handItemBefore, hand)) {
                dbg("use: block-use consumed (blockChanged=" + blockChanged + " hand=" + hand + ")");
                // Client-sourced swing on successful block use (buttons, levers, bone meal, placing…).
                BotClickDispatcher.swing(nms, hand);
                return true;
            }
            dbg("use: block-use passed (hand=" + hand + ") - falling through to in-hand use");
        }

        // 3. Nothing acted on a block (or no block was hit): use the item in that hand, exactly like a
        //    real client sending a use-item packet - eat/drink, draw a bow/trident, raise a shield,
        //    throw an egg/ender pearl/potion, use a spyglass/goat horn, etc.
        dbg("use: useItem hand=" + hand + " in-hand=" + nms.getItemInHand(hand).getItem()
                + " (ServerboundUseItemPacket)");
        BotClickDispatcher.useItem(
                nms, hand, bot.getLocation().getYaw(), bot.getLocation().getPitch());
        boolean acted = didConsume(nms, usingBefore, handItemBefore, hand);
        dbg("use: in-hand use (hand=" + hand + ") " + (acted ? "consumed" : "did nothing"));
        // Swing for instant uses (throwing pearls/eggs/snowballs) - but not when a multi-tick use
        // started (eating, drawing a bow, raising a shield), matching the vanilla client.
        if (acted && !nms.isUsingItem()) {
            BotClickDispatcher.swing(nms, hand);
        }
        return acted;
    }

    /**
     * Best-effort "did that right-click actually do something?" check. The real (void) packet handlers
     * don't hand us an {@code InteractionResult}, so we infer it from observable state changes: a
     * multi-tick use started (bow/food), the held item changed (placed/consumed/damaged), or a
     * container/trade screen opened. Block-state changes are checked separately by the caller. Used to
     * know when a {@code --once} click is finished and whether a block-use fell through to an in-hand
     * use.
     */
    private boolean didConsume(ServerPlayer nms, boolean usingBefore, ItemStack itemBefore, InteractionHand hand) {
        if (!usingBefore && nms.isUsingItem()) return true;
        ItemStack now = nms.getItemInHand(hand);
        if (now.getItem() != itemBefore.getItem() || now.getCount() != itemBefore.getCount()) {
            return true;
        }
        if (nms.containerMenu != null && nms.containerMenu != nms.inventoryMenu) {
            closeTransientContainer(nms);
            return true;
        }
        return false;
    }

    /** A bot can't drive a container/trade screen - close anything an interaction opened. */
    private static void closeTransientContainer(ServerPlayer nms) {
        if (nms.containerMenu != null && nms.containerMenu != nms.inventoryMenu) {
            try {
                nms.closeContainer();
            } catch (Throwable ignored) {
            }
        }
    }

    private static Direction toDirection(BlockFace face) {
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

    /**
     * Resolves the BLOCK the commanding player is aiming at (if any). Entity targeting is intentionally
     * NOT driven by the sender's crosshair - the bot interacts only with the entity it is itself
     * precisely aiming at, resolved per-tick in {@link #performUseAction}.
     */
    @Nullable
    private Object rayTraceTargetPlayer(Player player) {
        try {
            Block playerTarget = player.getTargetBlockExact((int) Math.ceil(CLICK_REACH));
            if (playerTarget != null && !playerTarget.getType().isAir()) {
                return playerTarget;
            }
        } catch (Exception ignored) {
        }
        return null;
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
            if (result != null && result.getHitEntity() != null) {
                return result.getHitEntity();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String formatTarget(Object target) {
        if (target == null) return "null";
        if (target instanceof Block b) {
            return b.getType().name() + "@(" + b.getX() + "," + b.getY() + "," + b.getZ() + ")";
        }
        if (target instanceof Entity e) {
            return e.getType().name() + "@"
                    + String.format(
                            "%.1f,%.1f,%.1f",
                            e.getLocation().getX(),
                            e.getLocation().getY(),
                            e.getLocation().getZ());
        }
        return target.getClass().getSimpleName();
    }

    @Override
    protected void onAfterStop(Player bot) {
        ((CraftPlayer) bot).getHandle().releaseUsingItem();
    }

    /**
     * Resumes a bot's right-click loop after a restart/reconnect. Rebuilds a fresh state from the bot's
     * own current aim when none was cached (e.g. after a plugin reload) instead of silently doing
     * nothing, matching {@link LeftClickCommand#resumeClicking}.
     */
    public void resumeClicking(FakePlayer fp) {
        FppClickMode mode = clickModes.get(fp.getUuid());
        if (mode == null) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        Integer taskId = clickTasks.get(fp.getUuid());
        if (taskId != null) return;

        RightClickState state = (RightClickState) clickStates.get(fp.getUuid());
        if (state == null) {
            Object target = selfRayTraceTarget(bot);
            state = new RightClickState();
            state.target = target;
            state.aimTarget = target;
            clickStates.put(fp.getUuid(), state);
        }

        if (state.target != null) {
            Location faceLoc = faceTowardTarget(bot.getLocation(), state.target, state.hitPosition);
            bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
            NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
        }
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);

        Location actualLoc = bot.getLocation().clone();
        manager.lockForAction(fp.getUuid(), actualLoc, false);

        final RightClickState finalState = state;
        final FppClickMode finalMode = mode;
        final int[] cooldown = {0};
        long intervalTicks = fp.resolveRightClickIntervalTicks();

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
                    if (nms.isUsingItem()) {
                        if (finalMode == FppClickMode.ONCE) {
                            stopClicking(fp.getUuid());
                        }
                        return;
                    }
                    if (cooldown[0] > 0) {
                        cooldown[0]--;
                        return;
                    }
                    boolean acted = performUseAction(b, finalState);
                    if (acted) {
                        if (finalMode == FppClickMode.ONCE) {
                            stopClicking(fp.getUuid());
                            return;
                        }
                        if (finalMode == FppClickMode.REPEAT) {
                            cooldown[0] = CLICK_COOLDOWN;
                        }
                    }
                },
                0L,
                intervalTicks);
        clickTasks.put(fp.getUuid(), newTask);
    }

    /** Right-click's per-bot click-loop state: last resolved interaction target/hit-point. */
    private static final class RightClickState extends ClickState {
        org.bukkit.util.Vector hitPosition;
    }
}
