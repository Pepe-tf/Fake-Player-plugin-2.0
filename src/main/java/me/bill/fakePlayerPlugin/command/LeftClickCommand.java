package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.Collection;
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
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.fakeplayer.BotNavUtil;
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
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

    private static final int DEFAULT_ATTACK_COOLDOWN = 5;
    private static final Map<org.bukkit.Material, Integer> WEAPON_COOLDOWN = Map.ofEntries(
            Map.entry(org.bukkit.Material.NETHERITE_SWORD, 12),
            Map.entry(org.bukkit.Material.DIAMOND_SWORD, 12),
            Map.entry(org.bukkit.Material.IRON_SWORD, 12),
            Map.entry(org.bukkit.Material.GOLDEN_SWORD, 12),
            Map.entry(org.bukkit.Material.STONE_SWORD, 12),
            Map.entry(org.bukkit.Material.WOODEN_SWORD, 12),
            Map.entry(org.bukkit.Material.NETHERITE_AXE, 20),
            Map.entry(org.bukkit.Material.DIAMOND_AXE, 20),
            Map.entry(org.bukkit.Material.IRON_AXE, 22),
            Map.entry(org.bukkit.Material.GOLDEN_AXE, 20),
            Map.entry(org.bukkit.Material.STONE_AXE, 25),
            Map.entry(org.bukkit.Material.WOODEN_AXE, 25),
            Map.entry(org.bukkit.Material.TRIDENT, 22),
            Map.entry(org.bukkit.Material.NETHERITE_PICKAXE, 16),
            Map.entry(org.bukkit.Material.DIAMOND_PICKAXE, 16),
            Map.entry(org.bukkit.Material.IRON_PICKAXE, 16),
            Map.entry(org.bukkit.Material.GOLDEN_PICKAXE, 16),
            Map.entry(org.bukkit.Material.STONE_PICKAXE, 16),
            Map.entry(org.bukkit.Material.WOODEN_PICKAXE, 16),
            Map.entry(org.bukkit.Material.NETHERITE_SHOVEL, 20),
            Map.entry(org.bukkit.Material.DIAMOND_SHOVEL, 20),
            Map.entry(org.bukkit.Material.IRON_SHOVEL, 20),
            Map.entry(org.bukkit.Material.GOLDEN_SHOVEL, 20),
            Map.entry(org.bukkit.Material.STONE_SHOVEL, 20),
            Map.entry(org.bukkit.Material.WOODEN_SHOVEL, 20),
            Map.entry(org.bukkit.Material.NETHERITE_HOE, 5),
            Map.entry(org.bukkit.Material.DIAMOND_HOE, 5),
            Map.entry(org.bukkit.Material.IRON_HOE, 7),
            Map.entry(org.bukkit.Material.GOLDEN_HOE, 20),
            Map.entry(org.bukkit.Material.STONE_HOE, 10),
            Map.entry(org.bukkit.Material.WOODEN_HOE, 20),
            Map.entry(org.bukkit.Material.MACE, 33));

    public LeftClickCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
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
        Entity entityTarget = null;
        BlockFace targetFace = null;

        if (sender instanceof Player player) {
            LivingEntity hostileTarget = rayTraceHostileEntity(player);
            if (hostileTarget != null) {
                entityTarget = hostileTarget;
                target = hostileTarget;
            } else {
                Block playerTarget = player.getTargetBlockExact((int) Math.ceil(CLICK_REACH));
                if (playerTarget != null && !playerTarget.getType().isAir()) {
                    blockTarget = new BlockPos(playerTarget.getX(), playerTarget.getY(), playerTarget.getZ());
                    target = playerTarget;
                    org.bukkit.util.RayTraceResult ray = player.rayTraceBlocks(CLICK_REACH);
                    if (ray != null) targetFace = ray.getHitBlockFace();
                } else {
                    entityTarget = rayTraceEntity(player);
                    if (isSelfTarget(bot, entityTarget)) {
                        entityTarget = null;
                    }
                    if (entityTarget != null) {
                        target = entityTarget;
                    }
                }
            }
        }

        if (target == null) {
            target = rayTraceTarget(bot);
            if (target instanceof Block b) {
                blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
                org.bukkit.util.RayTraceResult ray = bot.rayTraceBlocks(CLICK_REACH);
                if (ray != null) targetFace = ray.getHitBlockFace();
            }
        }

        final ClickMode finalMode = mode;
        final BlockFace finalFace = targetFace;
        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null) {
                double dist = bot.getLocation().distance(targetLoc);
                if (dist <= CLICK_REACH) {
                    lockAndStartClicking(fp, finalMode, target, blockTarget, entityTarget, finalFace);
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
                    Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
                    if (standLoc != null) {
                        final Object finalTarget = target;
                        final BlockPos finalBlockTarget = blockTarget;
                        final Entity finalEntityTarget = entityTarget;
                        startNavigation(
                                fp,
                                standLoc,
                                () -> lockAndStartClicking(
                                        fp, finalMode, finalTarget, finalBlockTarget, finalEntityTarget, finalFace));
                        sender.sendMessage(Lang.get("left-click-walking", "name", fp.getDisplayName()));
                        return true;
                    } else {
                        sender.sendMessage(Lang.get("left-click-no-path", "name", fp.getDisplayName()));
                        return true;
                    }
                }
            }
        }

        lockAndStartClicking(fp, finalMode, null, null, null, null);
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
        Entity finalEntityTarget = null;
        BlockFace targetFace = null;

        target = rayTraceTarget(bot);
        if (target instanceof Block b) {
            blockTarget = new BlockPos(b.getX(), b.getY(), b.getZ());
            org.bukkit.util.RayTraceResult ray = bot.rayTraceBlocks(CLICK_REACH);
            if (ray != null) targetFace = ray.getHitBlockFace();
        } else {
            finalEntityTarget = rayTraceEntity(bot);
            if (finalEntityTarget != null && !isSelfTarget(bot, finalEntityTarget)) target = finalEntityTarget;
        }

        if (target != null) {
            Location targetLoc = getTargetLocation(bot, target);
            if (targetLoc != null && bot.getLocation().distance(targetLoc) > CLICK_REACH) {
                Location standLoc = findStandLocationNearTarget(bot.getWorld(), targetLoc);
                if (standLoc == null) return false;
                final Object finalTarget = target;
                final BlockPos finalBlockTarget = blockTarget;
                final Entity finalEntity = finalEntityTarget;
                final BlockFace finalFace = targetFace;
                startNavigation(
                        fp,
                        standLoc,
                        () -> lockAndStartClicking(fp, mode, finalTarget, finalBlockTarget, finalEntity, finalFace));
                return true;
            }
        }

        lockAndStartClicking(fp, mode, target, blockTarget, finalEntityTarget, targetFace);
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
            FakePlayer fp, ClickMode mode, Object target, BlockPos blockTarget, Entity entityTarget, BlockFace face) {
        FppApiImpl.fireTaskEvent(fp, "left-click", FppBotTaskEvent.Action.START);
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null) return;

        if (isSelfTarget(bot, target) || isSelfTarget(bot, entityTarget)) {
            target = null;
            entityTarget = null;
        }

        if (target != null) {
            org.bukkit.util.Vector faceCenter = computeFaceCenter(target, face);
            Location faceLoc = faceTowardTarget(bot.getLocation(), target, faceCenter);
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
        state.entityCooldown = 0;
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
                        // Apply cooldown for REPEAT and HOLD modes
                        cooldown[0] = CLICK_COOLDOWN;
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
        Player bot = nms.getBukkitEntity();

        // Clear stale entity target (out of range, dead, or invalid)
        if (state.entityTarget != null
                && (!state.entityTarget.isValid()
                        || state.entityTarget.isDead()
                        || isSelfTarget(bot, state.entityTarget)
                        || state.entityTarget.getLocation().distance(bot.getLocation()) > CLICK_REACH)) {
            state.entityTarget = null;
        }

        // --- Auto-detect hostile mobs in forward cone ---
        // If no entity target yet (or it went stale), try finding a new one
        if (state.entityTarget == null) {
            LivingEntity bestMob = findBestMobTarget(bot, state);
            if (bestMob != null) {
                state.entityTarget = bestMob;
                Location aimLoc = computeAimingVector(bot, bestMob);
                if (aimLoc != null) {
                    bot.setRotation(aimLoc.getYaw(), aimLoc.getPitch());
                    NmsPlayerSpawner.setHeadYaw(bot, aimLoc.getYaw());
                }
                return tryAttackEntity(nms, state, bot);
            }
        }

        if (state.entityTarget != null) {
            // Keep aiming at the current entity target
            Location aimLoc = computeAimingVector(bot, state.entityTarget);
            if (aimLoc != null) {
                bot.setRotation(aimLoc.getYaw(), aimLoc.getPitch());
                NmsPlayerSpawner.setHeadYaw(bot, aimLoc.getYaw());
            }
            return tryAttackEntity(nms, state, bot);
        }

        BlockPos pos = state.blockTarget;

        if (state.dynamicTarget) {
            BlockPos currentTarget = rayTraceBlockTarget(nms);
            if (currentTarget != null) {
                // Check if an entity is blocking the way to the target block
                Entity blockingEntity = findEntityInRange(nms);
                if (blockingEntity != null && !isSelfTarget(bot, blockingEntity)) {
                    state.entityTarget = blockingEntity;
                    return tryAttackEntity(nms, state, bot);
                } else {
                    state.blockTarget = currentTarget;
                    pos = currentTarget;
                }
            } else if (pos != null) {
                BlockState blockState = nms.level().getBlockState(pos);
                if (blockState.isAir()) {
                    state.progress = 0;
                    state.blockTarget = null;
                    pos = null;
                }
            }
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
            NmsPlayerSpawner.handleBlockBreakAction(
                    nms,
                    pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    Direction.DOWN,
                    nms.level().getMaxY(),
                    -1);
            NmsPlayerSpawner.destroyBlockProgress(nms, -1, pos, -1);
            nms.gameMode.destroyBlock(pos);
            state.progress = 0;
            state.blockTarget = null;
            return true;
        }

        state.progress += speed;
        if (state.progress >= 1.0F) {
            nms.swing(InteractionHand.MAIN_HAND);
            NmsPlayerSpawner.handleBlockBreakAction(
                    nms,
                    pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    Direction.DOWN,
                    nms.level().getMaxY(),
                    -1);
            NmsPlayerSpawner.destroyBlockProgress(nms, -1, pos, -1);
            nms.gameMode.destroyBlock(pos);
            state.progress = 0;
            state.blockTarget = null;
            return true;
        }

        NmsPlayerSpawner.destroyBlockProgress(nms, -1, pos, (int) (state.progress * 10));
        Direction side = Direction.DOWN;
        NmsPlayerSpawner.handleBlockBreakAction(
                nms,
                pos,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                side,
                nms.level().getMaxY(),
                -1);
        nms.swing(InteractionHand.MAIN_HAND);
        return false;
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
                    eye,
                    dir,
                    CLICK_REACH,
                    org.bukkit.FluidCollisionMode.NEVER,
                    true,
                    0.0,
                    e -> e != null && e.isValid() && !e.isDead() && !isSelfTarget(nms.getBukkitEntity(), e));

            if (result != null && result.getHitEntity() != null) {
                return result.getHitEntity();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void startBreakBlock(ServerPlayer nms, BlockPos pos) {
        Direction side = Direction.DOWN;
        NmsPlayerSpawner.handleBlockBreakAction(
                nms,
                pos,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                side,
                nms.level().getMaxY(),
                -1);
        nms.swing(InteractionHand.MAIN_HAND);
    }

    @Nullable
    private BlockPos rayTraceBlockTarget(ServerPlayer nms) {
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
                return result.getBlockPos();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

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

    private boolean tryAttackEntity(ServerPlayer nms, ClickState state, Player bot) {
        if (isSelfTarget(bot, state.entityTarget)) {
            state.entityTarget = null;
            return false;
        }
        if (state.entityCooldown > 0) {
            state.entityCooldown--;
            return false;
        }

        nms.swing(InteractionHand.MAIN_HAND);
        if (state.entityTarget instanceof CraftPlayer cp) {
            NmsPlayerSpawner.performAttack(bot, cp.getHandle().getBukkitEntity(), 1.0);
        } else if (state.entityTarget instanceof org.bukkit.craftbukkit.entity.CraftEntity ce) {
            NmsPlayerSpawner.performAttack(bot, ce.getHandle().getBukkitEntity(), 1.0);
        }

        state.entityCooldown = getWeaponCooldown(bot);
        return true;
    }

    private static boolean isSelfTarget(Player bot, Object target) {
        return bot != null
                && target instanceof Entity entity
                && entity.getUniqueId().equals(bot.getUniqueId());
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
                    NmsPlayerSpawner.handleBlockBreakAction(
                            nms,
                            state.blockTarget,
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            Direction.DOWN,
                            nms.level().getMaxY(),
                            -1);
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
                        acted = tryAttackEntity(nms, finalState, b);
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

    /**
     * Ray-trace from the player's eyes and return the first hostile living entity
     * the ray intersects. More precise than rayTraceEntity().
     */
    @Nullable
    private LivingEntity rayTraceHostileEntity(Player player) {
        try {
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection();
            org.bukkit.util.RayTraceResult result = player.getWorld()
                    .rayTrace(eye, dir, CLICK_REACH, org.bukkit.FluidCollisionMode.NEVER, true, 0.0, e -> {
                        if (!(e instanceof LivingEntity le)) return false;
                        if (le instanceof Player) return false;
                        if (le.isDead() || !le.isValid()) return false;
                        // Hostile detection matching AttackCommand.findBestTarget
                        if (!(le instanceof Monster)
                                && !(le instanceof Slime)
                                && !(le instanceof Shulker)
                                && !(le instanceof Phantom)
                                && !(le instanceof EnderDragon)
                                && !(le instanceof Ghast)
                                && !(le instanceof Hoglin)) {
                            return false;
                        }
                        return true;
                    });
            if (result != null && result.getHitEntity() instanceof LivingEntity le) {
                return le;
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

    private static final class ClickState {
        Object target;
        BlockPos blockTarget;
        Entity entityTarget;
        BlockFace targetFace;
        ClickMode mode;
        boolean holding;
        float progress;
        boolean dynamicTarget;
        int entityCooldown;
    }

    /**
     * Find the best hostile mob target the bot is actually facing (within a forward cone).
     * Matches the PvE system's hostile-detection but restricts to the bot's look direction.
     */
    @Nullable
    private LivingEntity findBestMobTarget(Player bot, ClickState state) {
        Location botLoc = bot.getLocation();
        Location botEye = bot.getEyeLocation();
        org.bukkit.util.Vector lookDir = botEye.getDirection();
        Collection<Entity> nearby = bot.getWorld().getNearbyEntities(botLoc, CLICK_REACH, CLICK_REACH, CLICK_REACH);

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le instanceof Player) continue;
            if (le.isDead() || !le.isValid()) continue;

            { // Hostile mob detection matching AttackCommand.findBestTarget
                if (!(le instanceof Monster)
                        && !(le instanceof Slime)
                        && !(le instanceof Shulker)
                        && !(le instanceof Phantom)
                        && !(le instanceof EnderDragon)
                        && !(le instanceof Ghast)
                        && !(le instanceof Hoglin)) continue;
            }

            Location entCenter = le.getLocation().clone().add(0, le.getHeight() / 2.0, 0);
            double dist = entCenter.distance(botEye);
            if (dist > CLICK_REACH) continue;

            // Must be within forward view cone (~70 degrees horizontal)
            org.bukkit.util.Vector toEnt =
                    entCenter.toVector().subtract(botEye.toVector()).normalize();
            double angle = lookDir.angle(toEnt);
            if (angle > 1.22) continue; // ~70 degrees

            double score = dist + (angle * 2.0); // prefer closer and more centered
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }

        return best;
    }

    @Nullable
    private Location computeAimingVector(Player bot, Entity target) {
        if (target == null || !target.isValid()) return null;

        Location botEye = bot.getEyeLocation();
        double tx = target.getLocation().getX();
        double tz = target.getLocation().getZ();
        double ty = target.getLocation().getY() + target.getHeight() / 2.0;

        double dx = tx - botEye.getX();
        double dy = ty - botEye.getY();
        double dz = tz - botEye.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));
        return new Location(bot.getWorld(), tx, ty, tz, yaw, pitch);
    }
}
