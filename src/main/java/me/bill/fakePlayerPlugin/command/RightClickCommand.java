package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class RightClickCommand implements FppCommand {

    private static final double CLICK_REACH = 4.5;
    // The tick loop's own period *is* the interval (server/per-bot configurable, defaulting to 4
    // ticks — the vanilla client's own rightClickDelay), so no extra pulses are skipped after an
    // action (0 = act on every pulse of that period).
    private static final int CLICK_COOLDOWN = 0;

    /** Valid range for a bot's configurable right-click interval (server default or per-bot override). */
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

    public RightClickCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    private static void dbg(String msg) {
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

        ClickMode mode = ClickMode.ONCE;
        if (args.length >= 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            switch (action) {
                case "--once" -> mode = ClickMode.ONCE;
                case "--repeat" -> mode = ClickMode.REPEAT;
                case "--hold" -> mode = ClickMode.HOLD;
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
            target = rayTraceTarget(bot);
            if (isSelfTarget(bot, target)) {
                target = null;
            }
            if (target instanceof Block && bot instanceof Player) {
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

        final ClickMode finalMode = mode;
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
        BlockFace targetFace = null;
        org.bukkit.util.Vector aimPoint = null;
        target = rayTraceTarget(bot);
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

    private void startNavigation(FakePlayer fp, Location dest, Runnable onArrive) {
        BotPathfinder.PathOptions baseOpts = PathfindingService.resolvePathOptions(fp);
        BotPathfinder.PathOptions opts = new BotPathfinder.PathOptions(
                fp.isNavParkour(), true, fp.isNavPlaceBlocks(), baseOpts.avoidWater(), baseOpts.avoidLava());
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.USE,
                        () -> dest,
                        0.35,
                        0.0,
                        Integer.MAX_VALUE,
                        onArrive,
                        null,
                        null,
                        null,
                        opts));
    }

    private void lockAndStartClicking(FakePlayer fp, ClickMode mode, Object target, org.bukkit.util.Vector aim) {
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
                        bot.getName() + " NO TARGET — yaw=" + String.format("%.2f", startYaw) + " pitch="
                                + String.format("%.2f", startPitch));
            }
        }
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);

        Location actualLoc = bot.getLocation().clone();
        manager.lockForAction(uuid, actualLoc, false);

        ClickState state = new ClickState();
        state.target = target;
        state.mode = mode;
        state.holding = false;
        state.dynamicTarget = (target != null);
        // Seed with the sender's exact aim point so the first interaction targets that spot; the tick
        // loop refreshes it from the bot's own raytrace afterwards.
        state.hitPosition = aim;
        // The COMMANDED target — never overwritten by transient per-tick picks. The tick loop re-aims
        // the bot's head at this every pulse, so knockback or a passing entity can't permanently steer
        // the crosshair off what the bot was told to click.
        state.aimTarget = target;
        state.aimPoint = aim;
        clickStates.put(uuid, state);
        clickModes.put(uuid, mode);

        final int[] cooldown = {0};
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
                        if (mode == ClickMode.ONCE) {
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
                        if (mode == ClickMode.ONCE) {
                            stopClicking(uuid);
                            return;
                        }
                        if (mode == ClickMode.REPEAT || mode == ClickMode.HOLD) {
                            cooldown[0] = CLICK_COOLDOWN;
                        }
                    }
                },
                0L,
                intervalTicks);

        clickTasks.put(uuid, taskId);
    }

    private boolean performUseAction(Player bot, ClickState state) {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();

        // Keep the crosshair ON the commanded target every pulse — like a player holding their aim
        // steady. A passing entity gets vanilla treatment below; once it's gone, the very next pulse
        // is back on the commanded block instead of wherever the head drifted.
        refreshAim(bot, state);

        // Combined block+entity ray trace — vanilla nearest-hit priority: whichever the look vector
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

        boolean usingBefore = nms.isUsingItem();
        boolean ridingBefore = bot.isInsideVehicle();
        net.minecraft.world.item.ItemStack mainBefore = nms.getMainHandItem().copy();

        // 1. Entity is the nearest hit → real interact packet, exactly like a vanilla client. The
        //    server fires PlayerInteractEntityEvent/AtEntity and runs interactOn (armor-stand equip,
        //    feeding, taming, breeding, shearing, milking, mounting, leashing, trading). If the
        //    interaction PASSes (e.g. non-equippable item on an armless armor stand), fall through to
        //    the in-hand item use — the same order Minecraft.startUseItem follows.
        if (hitEntity != null) {
            org.bukkit.util.Vector hp = hit.getHitPosition();
            Vec3 hitVec = hp != null ? new Vec3(hp.getX(), hp.getY(), hp.getZ()) : null;
            dbg("use: interactEntity " + hitEntity.getType() + " (ServerboundInteractPacket)");
            BotClickDispatcher.interactEntity(nms, hitEntity, InteractionHand.MAIN_HAND, hitVec, nms.isShiftKeyDown());
            // The vanilla client hardcodes interactAt on a non-marker armor stand as SUCCESS
            // client-side, so a real player's click is always consumed there — they can never eat/use
            // the held item while aiming at an armor stand, even when the server-side swap does
            // nothing. Mirror that here.
            boolean consumed = hitEntity instanceof org.bukkit.entity.ArmorStand
                    || didConsume(nms, usingBefore, mainBefore)
                    || bot.isInsideVehicle() != ridingBefore;
            if (consumed) {
                dbg("use: entity interaction consumed");
                // A real client swings its own arm on a successful interaction; the server only
                // broadcasts SERVER-sourced swings, so the bot must send the swing itself.
                BotClickDispatcher.swing(nms, InteractionHand.MAIN_HAND);
                state.target = hitEntity;
                return true;
            }
            dbg("use: entity interaction passed — falling through to in-hand use");
            hitBlock = null; // entity was the pick; a real client does not also click the block behind it
            face = null;
        }

        // 2. Use the item on the block. A ServerboundUseItemOn packet drives the real useItemOn path,
        //    which fires PlayerInteractEvent/BlockPlaceEvent and natively handles block interaction,
        //    placement, seed/sapling planting, bone meal, hoe tilling, bucket use, etc. — no per-item
        //    table, no directly setting blocks in the world.
        if (hitBlock != null && face != null) {
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
            org.bukkit.util.Vector hp = hit.getHitPosition();
            Vec3 hitVec = hp != null
                    ? new Vec3(hp.getX(), hp.getY(), hp.getZ())
                    : new Vec3(hitBlock.getX() + 0.5, hitBlock.getY() + 0.5, hitBlock.getZ() + 0.5);
            BlockHitResult blockHit = new BlockHitResult(hitVec, dir, pos, false);
            state.hitPosition = hp;
            state.target = hitBlock;
            dbg("use: useItemOn " + hitBlock.getType() + " face=" + face + " (ServerboundUseItemOnPacket)");
            BotClickDispatcher.useItemOn(nms, InteractionHand.MAIN_HAND, blockHit);
            boolean blockChanged =
                    nms.level().getBlockState(pos) != before || nms.level().getBlockState(placePos) != placeBefore;
            if (blockChanged || didConsume(nms, usingBefore, mainBefore)) {
                dbg("use: block-use consumed (blockChanged=" + blockChanged + ")");
                // Client-sourced swing on successful block use (buttons, levers, bone meal, placing…).
                BotClickDispatcher.swing(nms, InteractionHand.MAIN_HAND);
                return true;
            }
            dbg("use: block-use passed — falling through to in-hand use");
        }

        // 3. Nothing acted on a block (or no block was hit): use the item in hand, exactly like a real
        //    client sending a use-item packet — eat/drink, draw a bow/trident, raise a shield, throw an
        //    egg/ender pearl/potion, use a spyglass/goat horn, etc.
        dbg("use: useItem in-hand=" + nms.getMainHandItem().getItem() + " (ServerboundUseItemPacket)");
        BotClickDispatcher.useItem(
                nms,
                InteractionHand.MAIN_HAND,
                bot.getLocation().getYaw(),
                bot.getLocation().getPitch());
        boolean acted = didConsume(nms, usingBefore, mainBefore);
        dbg("use: in-hand use " + (acted ? "consumed" : "did nothing"));
        // Swing for instant uses (throwing pearls/eggs/snowballs) — but not when a multi-tick use
        // started (eating, drawing a bow, raising a shield), matching the vanilla client.
        if (acted && !nms.isUsingItem()) {
            BotClickDispatcher.swing(nms, InteractionHand.MAIN_HAND);
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
    private boolean didConsume(ServerPlayer nms, boolean usingBefore, net.minecraft.world.item.ItemStack mainBefore) {
        if (!usingBefore && nms.isUsingItem()) return true;
        net.minecraft.world.item.ItemStack now = nms.getMainHandItem();
        if (now.getItem() != mainBefore.getItem() || now.getCount() != mainBefore.getCount()) {
            return true;
        }
        if (nms.containerMenu != null && nms.containerMenu != nms.inventoryMenu) {
            closeTransientContainer(nms);
            return true;
        }
        return false;
    }

    /** A bot can't drive a container/trade screen — close anything an interaction opened. */
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

    private static boolean isSelfTarget(Player bot, Object target) {
        return bot != null
                && target instanceof Entity entity
                && entity.getUniqueId().equals(bot.getUniqueId());
    }

    /**
     * Resolves the BLOCK the commanding player is aiming at (if any). Entity targeting is intentionally
     * NOT driven by the sender's crosshair — the bot interacts only with the entity it is itself
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
    private Object rayTraceTarget(Player bot) {
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

    @Nullable
    private Location getTargetLocation(Player bot, Object target) {
        if (target instanceof Block b) {
            return new Location(bot.getWorld(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
        } else if (target instanceof org.bukkit.entity.Entity e) {
            return e.getLocation().clone();
        }
        return null;
    }

    private static String formatTarget(Object target) {
        if (target == null) return "null";
        if (target instanceof Block b) {
            return b.getType().name() + "@(" + b.getX() + "," + b.getY() + "," + b.getZ() + ")";
        }
        if (target instanceof org.bukkit.entity.Entity e) {
            return e.getType().name() + "@"
                    + String.format(
                            "%.1f,%.1f,%.1f",
                            e.getLocation().getX(),
                            e.getLocation().getY(),
                            e.getLocation().getZ());
        }
        return target.getClass().getSimpleName();
    }

    private Location faceTowardTarget(Location botLoc, Object target) {
        return faceTowardTarget(botLoc, target, null);
    }

    private Location faceTowardTarget(Location botLoc, Object target, org.bukkit.util.Vector hitPos) {
        double tx, ty, tz;

        if (hitPos != null) {
            tx = hitPos.getX();
            ty = hitPos.getY();
            tz = hitPos.getZ();
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
     * E.g. for NORTH face of a block at (x,y,z), returns (x+0.5, y+0.5, z).
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
                                return faceTowardTarget(loc, targetLoc);
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
                                return faceTowardTarget(loc, targetLoc);
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

    private void cancelAll(UUID botUuid) {
        // Only release the nav slot if right-click's own walk-to-vantage currently owns it — another
        // concurrently running task (move, find, PVE) may hold it instead, and resetting *this* bot's
        // click task must never cancel someone else's navigation.
        if (pathfinding.isNavigating(botUuid, PathfindingService.Owner.USE)) {
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
            FppApiImpl.fireTaskEvent(fp, "right-click", FppBotTaskEvent.Action.STOP);
        }
        Integer taskId = clickTasks.remove(botUuid);
        if (taskId != null) FppScheduler.cancelTask(taskId);
        manager.unlockAction(botUuid);
        if (clearState) {
            clickStates.remove(botUuid);
            clickModes.remove(botUuid);
        }
        if (fp != null) {
            Player bot = fp.getPlayer();
            if (bot != null && bot.isOnline()) {
                ((CraftPlayer) bot).getHandle().releaseUsingItem();
            }
        }
    }

    public void stopAll() {
        pathfinding.cancelAll(PathfindingService.Owner.USE);
        new java.util.HashSet<>(clickTasks.keySet()).forEach(this::cleanupBot);
    }

    public void cleanupBot(UUID botUuid) {
        cancelAll(botUuid);
    }

    public boolean isClicking(UUID botUuid) {
        return clickTasks.containsKey(botUuid);
    }

    /**
     * Snapshot of the bot's active right-click task for persistence, or null when it isn't clicking.
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
     * Resumes a persisted right-click task after a restart: re-aims the bot at the saved point (when
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
        if (state == null) return;

        if (state.target != null) {
            Location faceLoc = faceTowardTarget(bot.getLocation(), state.target, state.hitPosition);
            bot.setRotation(faceLoc.getYaw(), faceLoc.getPitch());
            NmsPlayerSpawner.setHeadYaw(bot, faceLoc.getYaw());
        }
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);

        Location actualLoc = bot.getLocation().clone();
        manager.lockForAction(fp.getUuid(), actualLoc, false);

        final ClickState finalState = state;
        final ClickMode finalMode = mode;
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
                        if (finalMode == ClickMode.ONCE) {
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
                        if (finalMode == ClickMode.ONCE) {
                            stopClicking(fp.getUuid());
                            return;
                        }
                        if (finalMode == ClickMode.REPEAT) {
                            cooldown[0] = CLICK_COOLDOWN;
                        }
                    }
                },
                0L,
                intervalTicks);
        clickTasks.put(fp.getUuid(), newTask);
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
        ClickMode mode;
        boolean holding;
        boolean dynamicTarget;
        org.bukkit.util.Vector hitPosition;
        // The commanded target + exact aim point, set once at start and never overwritten by per-tick
        // picks. Used to re-aim the head every pulse.
        Object aimTarget;
        org.bukkit.util.Vector aimPoint;
    }
}
