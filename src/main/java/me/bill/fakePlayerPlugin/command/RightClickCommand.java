package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotInteractEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
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

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class RightClickCommand implements FppCommand {

    private static final double CLICK_REACH = 4.5;
    private static final int CLICK_COOLDOWN = 1;

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

    @Override
    public String getName() {
        return "right-click";
    }

    @Override
    public String getUsage() {
        return "<bot> [--once|--repeat|--hold|--stop]";
    }

    @Override
    public String getDescription() {
        return "Bot right-clicks (uses items, interacts with blocks/entities). Default: --once";
    }

    @Override
    public String getPermission() {
        return Perm.RIGHT_CLICK;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.RIGHT_CLICK);
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

        if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
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
        if (sender instanceof Player player) {
            target = rayTraceTargetPlayer(player);
            if (isSelfTarget(bot, target)) {
                target = null;
            }
            if (target instanceof Block) {
                org.bukkit.util.RayTraceResult ray = player.rayTraceBlocks(CLICK_REACH);
                if (ray != null) targetFace = ray.getHitBlockFace();
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
                if (ray != null) targetFace = ray.getHitBlockFace();
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
        final BlockFace finalFace = targetFace;
        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null) {
                double dist = bot.getLocation().distance(targetLoc);
                if (dist <= CLICK_REACH) {
                    lockAndStartClicking(fp, finalMode, target, finalFace);
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
                    Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
                    if (standLoc != null) {
                        final Object finalTarget = target;
                        startNavigation(
                                fp, standLoc, () -> lockAndStartClicking(fp, finalMode, finalTarget, finalFace));
                        sender.sendMessage(Lang.get("right-click-walking", "name", fp.getDisplayName()));
                        return true;
                    } else {
                        sender.sendMessage(Lang.get("right-click-no-path", "name", fp.getDisplayName()));
                        return true;
                    }
                }
            }
        }

        lockAndStartClicking(fp, finalMode, null, null);
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
        target = rayTraceTarget(bot);
        if (target instanceof Block) {
            org.bukkit.util.RayTraceResult ray = bot.rayTraceBlocks(CLICK_REACH);
            if (ray != null) targetFace = ray.getHitBlockFace();
        }

        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null && bot.getLocation().distance(targetLoc) > CLICK_REACH) {
                Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
                if (standLoc == null) return false;
                final Object finalTarget = target;
                final BlockFace finalFace = targetFace;
                startNavigation(fp, standLoc, () -> lockAndStartClicking(fp, mode, finalTarget, finalFace));
                return true;
            }
        }

        lockAndStartClicking(fp, mode, target, targetFace);
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

    private void lockAndStartClicking(FakePlayer fp, ClickMode mode, Object target, BlockFace face) {
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
            org.bukkit.util.Vector faceCenter = computeFaceCenter(target, face);
            Location faceLoc = faceTowardTarget(bot.getLocation(), target, faceCenter);
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
        state.hitPosition = null;
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

                    if (fp.isInventoryOpen()) {
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
                4L);

        clickTasks.put(uuid, taskId);
    }

    private boolean performUseAction(Player bot, ClickState state) {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();
        FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "=== performUseAction for " + bot.getName() + " ===");

        RayTraceResult ray = bot.getWorld()
                .rayTraceBlocks(
                        bot.getEyeLocation(),
                        bot.getEyeLocation().getDirection(),
                        CLICK_REACH,
                        FluidCollisionMode.NEVER,
                        false);

        if (ray != null && ray.getHitBlock() != null && ray.getHitBlockFace() != null) {
            Block hitBlock = ray.getHitBlock();
            BlockFace face = ray.getHitBlockFace();
            FppLogger.debug(
                    "RIGHTCLICK",
                    Config.debugRightClick(),
                    "Ray hit: " + hitBlock.getType().name() + " face=" + face);

            Block actualBlock = checkForAttachedInteractiveBlock(hitBlock, face);
            if (actualBlock != null) {
                FppLogger.debug(
                        "RIGHTCLICK",
                        Config.debugRightClick(),
                        ">>> Attached block: " + actualBlock.getType().name());
                hitBlock = actualBlock;
            }

            state.hitPosition = ray.getHitPosition();
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "Hit position: " + state.hitPosition);

            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "1. Trying NMS block interaction (useItemOn)");
            boolean interacted = triggerBlockInteraction(bot, hitBlock, face, ray);
            if (interacted) {
                FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   SUCCESS: Block interaction consumed");
                state.target = hitBlock;
                return true;
            }
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   FAILED: useItemOn did not consume action");

            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "2. Trying to place block");
            boolean placed = tryPlaceBlock(bot, hitBlock, face);
            if (placed) {
                FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   SUCCESS: Block placed");
                state.target = hitBlock;
                return true;
            }
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   FAILED: Nothing to place or blocked");

            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "3. Trying to plant");
            boolean planted = tryPlantingAction(bot, hitBlock, face);
            if (planted) {
                FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   SUCCESS: Planting done");
                state.target = hitBlock;
                return true;
            }
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   FAILED: No plantable item or wrong soil");

            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "4. Trying bone meal");
            boolean boneMealed = tryBoneMealAction(bot, hitBlock, face);
            if (boneMealed) {
                FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   SUCCESS: Bone meal applied");
                state.target = hitBlock;
                return true;
            }
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   FAILED: No bone meal or not applicable");
        } else {
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "No block hit (ray=null or no hitBlock/face)");
        }

        FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "5. Trying entity interaction");
        boolean acted = tryEntityUse(bot, state);
        if (acted) {
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   SUCCESS: Entity interacted");
            return true;
        }
        FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   FAILED: No entity hit");

        FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "6. Trying item use (eat/drink/potion)");
        acted = tryUseItem(bot, state);
        if (acted) {
            FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   SUCCESS: Item used");
            return true;
        }
        FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "   FAILED: No usable item in hands");

        FppLogger.debug("RIGHTCLICK", Config.debugRightClick(), "=== NOTHING acted ===");
        return false;
    }

    private Block checkForAttachedInteractiveBlock(Block hitBlock, BlockFace face) {
        Block adjacent = hitBlock.getRelative(face);
        Material adjMat = adjacent.getType();
        String adjName = adjMat.name();

        if (adjName.contains("BUTTON") || adjName.contains("LEVER")) {
            return adjacent;
        }

        BlockData adjData = adjacent.getBlockData();
        if (adjData instanceof Switch) {
            return adjacent;
        }

        return null;
    }

    private boolean triggerBlockInteraction(Player bot, Block block, BlockFace face, RayTraceResult ray) {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();
        nms.resetLastActionTime();

        org.bukkit.util.Vector hitPos = ray.getHitPosition();
        BlockFace hitFace = ray.getHitBlockFace() != null ? ray.getHitBlockFace() : face;

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(block.getX(), block.getY(), block.getZ());
        Direction direction =
                switch (hitFace) {
                    case UP -> Direction.UP;
                    case DOWN -> Direction.DOWN;
                    case NORTH -> Direction.NORTH;
                    case SOUTH -> Direction.SOUTH;
                    case EAST -> Direction.EAST;
                    case WEST -> Direction.WEST;
                    default -> Direction.UP;
                };

        net.minecraft.world.phys.Vec3 hitVec =
                new net.minecraft.world.phys.Vec3(hitPos.getX(), hitPos.getY(), hitPos.getZ());
        BlockHitResult blockHit = new BlockHitResult(hitVec, direction, pos, false);

        Object result = NmsPlayerSpawner.useItemOn(nms, InteractionHand.MAIN_HAND, blockHit);
        boolean consumed = NmsPlayerSpawner.consumesAction(result);

        if (consumed) {
            bot.swingMainHand();
            return true;
        }

        return false;
    }

    private boolean tryEntityUse(Player bot, ClickState state) {
        RayTraceResult entityRay = bot.getWorld()
                .rayTrace(
                        bot.getEyeLocation(),
                        bot.getEyeLocation().getDirection(),
                        CLICK_REACH,
                        FluidCollisionMode.NEVER,
                        true,
                        0.1,
                        entity -> entity != null && entity.isValid() && !entity.isDead() && !isSelfTarget(bot, entity));

        if (entityRay != null && entityRay.getHitEntity() != null) {
            Entity entity = entityRay.getHitEntity();
            FakePlayer fp = manager.getByUuid(bot.getUniqueId());
            if (fp != null) {
                var interactEvent = new FppBotInteractEvent(new FppBotImpl(fp), entity, EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(interactEvent);
            }
            bot.swingMainHand();
            state.target = entity;
            return true;
        }
        return false;
    }

    private static boolean isSelfTarget(Player bot, Object target) {
        return bot != null
                && target instanceof Entity entity
                && entity.getUniqueId().equals(bot.getUniqueId());
    }

    private boolean tryUseItem(Player bot, ClickState state) {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();

        for (InteractionHand hand : InteractionHand.values()) {
            net.minecraft.world.item.ItemStack item = nms.getItemInHand(hand);
            if (item.isEmpty()) continue;

            Object useResult = NmsPlayerSpawner.useItem(nms, hand);
            if (NmsPlayerSpawner.consumesAction(useResult)) {
                if (nms.isUsingItem()) {
                    state.holding = true;
                }
                if (hand == InteractionHand.MAIN_HAND) {
                    bot.swingMainHand();
                } else {
                    bot.swingOffHand();
                }
                return true;
            }
        }
        return false;
    }

    private boolean tryPlaceBlock(Player bot, Block block, BlockFace face) {
        ServerPlayer nms = ((CraftPlayer) bot).getHandle();

        for (InteractionHand hand : InteractionHand.values()) {
            net.minecraft.world.item.ItemStack item = nms.getItemInHand(hand);
            if (item.isEmpty()) continue;

            Block targetBlock = block.getRelative(face);
            if (!targetBlock.getType().isAir() && !targetBlock.isLiquid()) {
                continue;
            }

            net.minecraft.core.BlockPos pos =
                    new net.minecraft.core.BlockPos(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
            Direction direction =
                    switch (face) {
                        case UP -> Direction.UP;
                        case DOWN -> Direction.DOWN;
                        case NORTH -> Direction.NORTH;
                        case SOUTH -> Direction.SOUTH;
                        case EAST -> Direction.EAST;
                        case WEST -> Direction.WEST;
                        default -> Direction.UP;
                    };

            net.minecraft.world.phys.Vec3 hitVec = new net.minecraft.world.phys.Vec3(
                    targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);

            BlockHitResult hit = new BlockHitResult(hitVec, direction, pos, false);
            var result = NmsPlayerSpawner.useItemOn(nms, hand, hit);

            if (NmsPlayerSpawner.consumesAction(result)) {
                nms.swing(hand);
                return true;
            }
        }

        return false;
    }

    private boolean tryPlantingAction(Player bot, Block block, BlockFace face) {
        if (face != BlockFace.UP) return false;

        ItemStack mItem = bot.getInventory().getItemInMainHand();
        ItemStack oItem = bot.getInventory().getItemInOffHand();
        ItemStack plantItem = null;
        boolean isOffhand = false;

        if (isPlantable(mItem)) {
            plantItem = mItem;
        } else if (isPlantable(oItem)) {
            plantItem = oItem;
            isOffhand = true;
        }

        if (plantItem == null) return false;

        String matName = plantItem.getType().name();
        String plantBlockName = null;

        if (matName.equals("WHEAT_SEEDS")) plantBlockName = "WHEAT";
        else if (matName.equals("CARROT") || matName.equals("CARROTS")) plantBlockName = "CARROTS";
        else if (matName.equals("POTATO") || matName.equals("POTATOES")) plantBlockName = "POTATOES";
        else if (matName.equals("BEETROOT_SEEDS")) plantBlockName = "BEETROOTS";
        else if (matName.equals("MELON_SEEDS")) plantBlockName = "MELON_STEM";
        else if (matName.equals("PUMPKIN_SEEDS")) plantBlockName = "PUMPKIN_STEM";
        else if (matName.equals("NETHER_WART")) plantBlockName = "NETHER_WART";
        else if (matName.equals("SWEET_BERRIES")) plantBlockName = "SWEET_BERRY_BUSH";
        else if (matName.contains("SAPLING") || matName.equals("MANGROVE_PROPAGULE")) plantBlockName = matName;

        if (plantBlockName == null) return false;

        Block targetBlock = block.getRelative(BlockFace.UP);
        if (targetBlock.getType().isAir() || targetBlock.isLiquid()) {
            try {
                Material plantMat = Material.valueOf(plantBlockName);

                if (!canPlaceAt(bot, targetBlock, plantMat)) return false;

                targetBlock.setType(plantMat, true);
                targetBlock
                        .getWorld()
                        .playSound(
                                targetBlock.getLocation(),
                                targetBlock.getBlockData().getSoundGroup().getPlaceSound(),
                                1.0f,
                                1.0f);

                if (isOffhand) bot.swingOffHand();
                else bot.swingMainHand();

                if (bot.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    plantItem.setAmount(plantItem.getAmount() - 1);
                    if (plantItem.getAmount() <= 0) {
                        if (isOffhand) bot.getInventory().setItemInOffHand(null);
                        else bot.getInventory().setItemInMainHand(null);
                    }
                }
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }

    private boolean canPlaceAt(Player bot, Block targetBlock, Material material) {
        Block feet = bot.getLocation().getBlock();
        Block head = bot.getEyeLocation().getBlock();
        if (targetBlock.equals(feet) || targetBlock.equals(head)) {
            return false;
        }

        if (isPlantItemOrBlock(material)) {
            Block soilBlock = targetBlock.getRelative(BlockFace.DOWN);
            if (!isValidSoilFor(material, soilBlock.getType())) {
                return false;
            }
        }
        return true;
    }

    private boolean isPlantItemOrBlock(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.contains("SAPLING")
                || name.contains("SEED")
                || name.equals("CARROT")
                || name.equals("CARROTS")
                || name.equals("POTATO")
                || name.equals("POTATOES")
                || name.equals("NETHER_WART")
                || name.equals("SWEET_BERRIES")
                || name.equals("SWEET_BERRY_BUSH")
                || name.equals("MANGROVE_PROPAGULE")
                || name.equals("WHEAT");
    }

    private boolean isValidSoilFor(Material mat, Material soil) {
        String matName = mat.name();
        String soilName = soil.name();

        if (matName.contains("WHEAT")
                || matName.contains("CARROT")
                || matName.contains("POTATO")
                || matName.contains("BEETROOT")
                || matName.contains("SEED")) {
            return soilName.equals("FARMLAND");
        }
        if (matName.equals("NETHER_WART")) {
            return soilName.equals("SOUL_SAND");
        }
        if (matName.equals("SWEET_BERRIES") || matName.equals("SWEET_BERRY_BUSH")) {
            return soilName.equals("GRASS_BLOCK")
                    || soilName.equals("DIRT")
                    || soilName.equals("PODZOL")
                    || soilName.equals("COARSE_DIRT");
        }
        if (matName.contains("SAPLING") || matName.equals("MANGROVE_PROPAGULE")) {
            return soilName.equals("GRASS_BLOCK")
                    || soilName.equals("DIRT")
                    || soilName.equals("PODZOL")
                    || soilName.equals("COARSE_DIRT")
                    || soilName.equals("MOSS_BLOCK")
                    || soilName.equals("ROOTED_DIRT");
        }
        return false;
    }

    private boolean isPlantable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.contains("SEED")
                || name.equals("CARROT")
                || name.equals("CARROTS")
                || name.equals("POTATO")
                || name.equals("POTATOES")
                || name.contains("SAPLING")
                || name.equals("NETHER_WART")
                || name.equals("SWEET_BERRIES")
                || name.equals("MANGROVE_PROPAGULE");
    }

    private boolean tryBoneMealAction(Player bot, Block block, BlockFace face) {
        ItemStack mItem = bot.getInventory().getItemInMainHand();
        ItemStack oItem = bot.getInventory().getItemInOffHand();
        ItemStack boneMealItem = null;
        boolean isOffhand = false;

        if (mItem != null && mItem.getType().name().equals("BONE_MEAL")) {
            boneMealItem = mItem;
        } else if (oItem != null && oItem.getType().name().equals("BONE_MEAL")) {
            boneMealItem = oItem;
            isOffhand = true;
        }

        if (boneMealItem == null) return false;

        Block targetBlock = block.getRelative(BlockFace.UP);
        boolean success = false;

        try {
            success = targetBlock.applyBoneMeal(face);
            if (!success) {
                success = block.applyBoneMeal(face);
            }
        } catch (Throwable t) {
            return false;
        }

        if (success) {
            block.getWorld().playEffect(block.getLocation(), org.bukkit.Effect.BONE_MEAL_USE, 0);
            if (isOffhand) bot.swingOffHand();
            else bot.swingMainHand();

            if (bot.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                boneMealItem.setAmount(boneMealItem.getAmount() - 1);
                if (boneMealItem.getAmount() <= 0) {
                    if (isOffhand) bot.getInventory().setItemInOffHand(null);
                    else bot.getInventory().setItemInMainHand(null);
                }
            }
            return true;
        }
        return false;
    }

    @Nullable
    private Object rayTraceTargetPlayer(Player player) {
        try {
            Block playerTarget = player.getTargetBlockExact((int) Math.ceil(CLICK_REACH));
            if (playerTarget != null && !playerTarget.getType().isAir()) {
                return playerTarget;
            }
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

    private org.bukkit.util.Vector getExactHitPosition(Player bot, Object target) {
        if (!(target instanceof Block)) return null;

        RayTraceResult ray = bot.getWorld()
                .rayTraceBlocks(
                        bot.getEyeLocation(),
                        bot.getEyeLocation().getDirection(),
                        CLICK_REACH,
                        FluidCollisionMode.NEVER,
                        true);

        return ray != null ? ray.getHitPosition() : null;
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
                4L);
        clickTasks.put(fp.getUuid(), newTask);
    }

    private static final class ClickState {
        Object target;
        ClickMode mode;
        boolean holding;
        boolean dynamicTarget;
        org.bukkit.util.Vector hitPosition;
    }
}
