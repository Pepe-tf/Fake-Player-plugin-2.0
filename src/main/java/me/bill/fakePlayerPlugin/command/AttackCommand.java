package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotAttackEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class AttackCommand implements FppCommand {

    private static final Map<Material, Integer> WEAPON_COOLDOWN = Map.ofEntries(
            Map.entry(Material.NETHERITE_SWORD, 12),
            Map.entry(Material.DIAMOND_SWORD, 12),
            Map.entry(Material.IRON_SWORD, 12),
            Map.entry(Material.GOLDEN_SWORD, 12),
            Map.entry(Material.STONE_SWORD, 12),
            Map.entry(Material.WOODEN_SWORD, 12),
            Map.entry(Material.NETHERITE_AXE, 20),
            Map.entry(Material.DIAMOND_AXE, 20),
            Map.entry(Material.IRON_AXE, 22),
            Map.entry(Material.GOLDEN_AXE, 20),
            Map.entry(Material.STONE_AXE, 25),
            Map.entry(Material.WOODEN_AXE, 25),
            Map.entry(Material.TRIDENT, 22),
            Map.entry(Material.NETHERITE_PICKAXE, 16),
            Map.entry(Material.DIAMOND_PICKAXE, 16),
            Map.entry(Material.IRON_PICKAXE, 16),
            Map.entry(Material.GOLDEN_PICKAXE, 16),
            Map.entry(Material.STONE_PICKAXE, 16),
            Map.entry(Material.WOODEN_PICKAXE, 16),
            Map.entry(Material.NETHERITE_SHOVEL, 20),
            Map.entry(Material.DIAMOND_SHOVEL, 20),
            Map.entry(Material.IRON_SHOVEL, 20),
            Map.entry(Material.GOLDEN_SHOVEL, 20),
            Map.entry(Material.STONE_SHOVEL, 20),
            Map.entry(Material.WOODEN_SHOVEL, 20),
            Map.entry(Material.NETHERITE_HOE, 5),
            Map.entry(Material.DIAMOND_HOE, 5),
            Map.entry(Material.IRON_HOE, 7),
            Map.entry(Material.GOLDEN_HOE, 20),
            Map.entry(Material.STONE_HOE, 10),
            Map.entry(Material.WOODEN_HOE, 20),
            Map.entry(Material.MACE, 33));

    private static final int DEFAULT_COOLDOWN = 5;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final PathfindingService pathfinding;

    private final Map<UUID, AttackState> attackStates = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> attackTasks = new ConcurrentHashMap<>();

    /**
     * Separate repeating-scan task IDs for --hunt mode (20-tick navigation-to-next-target loop).
     */
    private final Map<UUID, Integer> huntScanTasks = new ConcurrentHashMap<>();

    private final Map<UUID, Location> activeAttackLocations = new ConcurrentHashMap<>();

    private final Map<UUID, Boolean> activeAttackOnceFlags = new ConcurrentHashMap<>();

    public AttackCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    private static final class AttackState {
        int cooldownTicks = 0;

        boolean mobMode = false;
        double range = 8.0;
        String priority = "nearest";
        Set<EntityType> filterTypes = new HashSet<>();

        FakePlayer.PveSmartAttackMode smartAttackMode = FakePlayer.PveSmartAttackMode.OFF;
        boolean moveToTarget = false;

        /**
         * True when in --hunt mode (PathfindingService drives movement, not raw input).
         */
        boolean huntMode = false;

        @Nullable
        UUID currentTargetUuid = null;

        int retargetCountdown = 0;

        float currentYaw = 0f;
        float currentPitch = 0f;
    }

    private record MobFlags(
            double range,
            String priority,
            Set<EntityType> filterTypes,
            FakePlayer.PveSmartAttackMode smartAttackMode,
            boolean huntMode) {
        /**
         * Backward-compat 4-arg constructor used by persistence-resume and settings paths.
         */
        MobFlags(double range, String priority, Set<EntityType> filterTypes, boolean moveToTarget) {
            this(
                    range,
                    priority,
                    filterTypes,
                    moveToTarget ? FakePlayer.PveSmartAttackMode.ON_MOVE : FakePlayer.PveSmartAttackMode.ON_NO_MOVE,
                    false);
        }
    }

    @Override
    public String getName() {
        return "attack";
    }

    @Override
    public String getUsage() {
        return "<bot|all> [--once|--stop|--mob [--range <n>] [--type <mob>] [--priority <mode>] [--move]"
                + "  |  --hunt [<mob>] [--range <n>] [--priority <mode>]]  |  --stop";
    }

    @Override
    public String getDescription() {
        return "Attack entities, auto-target mobs (--mob), or roam and hunt mobs (--hunt).";
    }

    @Override
    public String getPermission() {
        return Perm.ATTACK;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.ATTACK);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("attack-usage"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("--stop")) {
            stopAll();
            sender.sendMessage(Lang.get("attack-stopped-all"));
            return true;
        }

        String botName = args[0];

        boolean once = false;
        boolean stop = false;
        boolean mobMode = false;
        boolean moveToTarget = false;
        boolean huntMode = false;
        double range = Config.attackMobDefaultRange();
        String priority = Config.attackMobDefaultPriority();
        EntityType filterType = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase();
            switch (a) {
                case "--once" -> once = true;
                case "--stop" -> stop = true;
                case "--move" -> moveToTarget = true;
                case "--mob" -> {
                    mobMode = true;

                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        String next = args[i + 1].toUpperCase();
                        try {
                            EntityType candidate = EntityType.valueOf(next);
                            if (candidate.isAlive() && candidate != EntityType.PLAYER) {
                                filterType = candidate;
                                i++;
                            }
                        } catch (IllegalArgumentException ignored) {

                        }
                    }
                }
                case "--hunt" -> {
                    huntMode = true;
                    mobMode = true;
                    // Default to a larger scan range for hunting
                    if (range == Config.attackMobDefaultRange()) range = 32.0;

                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        String next = args[i + 1].toUpperCase();
                        try {
                            EntityType candidate = EntityType.valueOf(next);
                            if (candidate.isAlive() && candidate != EntityType.PLAYER) {
                                filterType = candidate;
                                i++;
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "--range" -> {
                    if (i + 1 < args.length) {
                        try {
                            range = Double.parseDouble(args[++i]);
                            if (range < 1 || range > 64) {
                                sender.sendMessage(Lang.get("attack-mob-invalid-range"));
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Lang.get("attack-mob-invalid-range"));
                            return true;
                        }
                    }
                }
                case "--type" -> {
                    if (i + 1 < args.length) {
                        String typeName = args[++i].toUpperCase();
                        try {
                            filterType = EntityType.valueOf(typeName);
                        } catch (IllegalArgumentException e) {
                            sender.sendMessage(Lang.get("attack-mob-invalid-type", "type", args[i]));
                            return true;
                        }
                    }
                }
                case "--priority" -> {
                    if (i + 1 < args.length) {
                        priority = args[++i].toLowerCase();
                        if (!priority.equals("nearest") && !priority.equals("lowest-health")) {
                            priority = "nearest";
                        }
                    }
                }
            }
        }

        MobFlags mobFlags = mobMode
                ? new MobFlags(
                        range,
                        priority,
                        filterType != null ? Set.of(filterType) : Set.of(),
                        moveToTarget ? FakePlayer.PveSmartAttackMode.ON_MOVE : FakePlayer.PveSmartAttackMode.ON_NO_MOVE,
                        huntMode)
                : null;

        if (botName.equalsIgnoreCase("--all")) {
            if (stop) {
                if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
                    sender.sendMessage(Lang.get("no-permission"));
                    return true;
                }
                stopAll();
                sender.sendMessage(Lang.get("attack-stopped-all"));
                return true;
            }
            int count = 0;
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
                    if (!BotAccess.canAdminister(player, fp)) {
                        continue;
                    }
                }
                Player bot = fp.getPlayer();
                if (bot == null || !bot.isOnline()) continue;
                startForBot(sender, fp, once, mobFlags);
                count++;
            }
            if (count == 0) sender.sendMessage(Lang.get("attack-no-bots"));
            else if (mobFlags != null && mobFlags.huntMode())
                sender.sendMessage(Lang.get("attack-hunt-started-all", "count", String.valueOf(count)));
            return true;
        }

        FakePlayer fp = manager.getByName(botName);
        if (fp == null) {
            sender.sendMessage(Lang.get("attack-not-found", "name", botName));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("attack-bot-offline", "name", fp.getDisplayName()));
            return true;
        }

        if (stop) {
            cancelAll(fp.getUuid());
            sender.sendMessage(Lang.get("attack-stopped", "name", fp.getDisplayName()));
            return true;
        }

        startForBot(sender, fp, once, mobFlags);

        if (mobFlags != null) {
            if (mobFlags.huntMode()) {
                // Hunt mode: no navigation to sender; bot roams freely
                sender.sendMessage(Lang.get(
                        "attack-hunt-started",
                        "name",
                        fp.getDisplayName(),
                        "range",
                        String.valueOf((int) mobFlags.range()),
                        "priority",
                        mobFlags.priority()));
            } else {
                if (sender instanceof Player sp) {
                    double xzDist = PathfindingService.xzDist(bot.getLocation(), sp.getLocation());
                    if (xzDist > Config.pathfindingArrivalDistance()) {
                        sender.sendMessage(Lang.get("attack-walking", "name", fp.getDisplayName()));
                        return true;
                    }
                }
                sender.sendMessage(Lang.get(
                        "attack-mob-started",
                        "name",
                        fp.getDisplayName(),
                        "range",
                        String.valueOf((int) mobFlags.range()),
                        "priority",
                        mobFlags.priority()));
            }
            if (!mobFlags.filterTypes().isEmpty()) {
                String typeNames = mobFlags.filterTypes().stream()
                        .map(t -> t.name().toLowerCase())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("all hostile");
                sender.sendMessage(Lang.get("attack-mob-type", "name", fp.getDisplayName(), "type", typeNames));
            }
        } else if (sender instanceof Player sp) {
            double xzDist = PathfindingService.xzDist(bot.getLocation(), sp.getLocation());
            if (xzDist <= Config.pathfindingArrivalDistance()) {
                sender.sendMessage(
                        once
                                ? Lang.get("attack-started-once", "name", fp.getDisplayName())
                                : Lang.get("attack-started", "name", fp.getDisplayName()));
            } else {
                sender.sendMessage(Lang.get("attack-walking", "name", fp.getDisplayName()));
            }
        } else {
            sender.sendMessage(
                    once
                            ? Lang.get("attack-started-once", "name", fp.getDisplayName())
                            : Lang.get("attack-started", "name", fp.getDisplayName()));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : List.of("--stop", "stop", "--all")) if (s.startsWith(prefix)) out.add(s);
            for (FakePlayer fp : manager.getActivePlayers())
                if (fp.getName().toLowerCase().startsWith(prefix)) out.add(fp.getName());
            return out;
        }

        String prefix = args[args.length - 1].toLowerCase();

        if (args.length >= 3) {
            String prev = args[args.length - 2].toLowerCase();

            if (prev.equals("--mob") || prev.equals("--hunt")) {
                List<String> out = new ArrayList<>();
                for (EntityType et : EntityType.values()) {
                    if (et.isAlive()
                            && et != EntityType.PLAYER
                            && et.name().toLowerCase().startsWith(prefix))
                        out.add(et.name().toLowerCase());
                }
                return out;
            }

            if (prev.equals("--type")) {
                List<String> out = new ArrayList<>();
                for (EntityType et : EntityType.values()) {
                    if (et.isAlive()
                            && et != EntityType.PLAYER
                            && et.name().toLowerCase().startsWith(prefix))
                        out.add(et.name().toLowerCase());
                }
                return out;
            }

            if (prev.equals("--priority")) {
                List<String> out = new ArrayList<>();
                for (String s : List.of("nearest", "lowest-health")) if (s.startsWith(prefix)) out.add(s);
                return out;
            }

            if (prev.equals("--range")) {
                List<String> out = new ArrayList<>();
                for (String s : List.of("4", "6", "8", "10", "16")) if (s.startsWith(prefix)) out.add(s);
                return out;
            }
        }

        Set<String> used = new HashSet<>();
        boolean mobNameProvided = false;
        boolean huntNameProvided = false;
        for (int i = 1; i < args.length - 1; i++) {
            String a = args[i].toLowerCase();
            used.add(a);
            if (a.equals("--mob")) {
                if (i + 1 < args.length - 1 && !args[i + 1].startsWith("-")) {
                    mobNameProvided = true;
                    used.add(args[i + 1].toLowerCase());
                    i++;
                }
            }
            if (a.equals("--hunt")) {
                if (i + 1 < args.length - 1 && !args[i + 1].startsWith("-")) {
                    huntNameProvided = true;
                    used.add(args[i + 1].toLowerCase());
                    i++;
                }
            }
        }

        List<String> out = new ArrayList<>();

        List<String> flags = new ArrayList<>(
                List.of("--once", "--stop", "--mob", "--hunt", "--range", "--type", "--priority", "--move"));
        if (mobNameProvided || huntNameProvided) flags.remove("--type");

        for (String flag : flags) if (!used.contains(flag) && flag.startsWith(prefix)) out.add(flag);

        return out;
    }

    private void startForBot(CommandSender sender, FakePlayer fp, boolean once, @Nullable MobFlags mobFlags) {
        Player bot = fp.getPlayer();
        if (bot == null) return;

        cancelAll(fp.getUuid());

        Location dest = (sender instanceof Player sp)
                ? sp.getLocation().clone()
                : bot.getLocation().clone();
        if (sender instanceof Player sp) {
            dest.setYaw(sp.getLocation().getYaw());
            dest.setPitch(sp.getLocation().getPitch());
        }

        double xzDist = PathfindingService.xzDist(bot.getLocation(), dest);
        boolean alreadyClose = xzDist <= Config.pathfindingArrivalDistance();

        if (mobFlags != null) {

            if (alreadyClose) {
                startMobMode(fp, mobFlags, dest);
            } else {
                navigateThenMob(fp, mobFlags, dest);
            }
        } else {

            if (alreadyClose) {
                lockAndStartClassic(fp, once, dest);
            } else {
                startNavigation(fp, once, dest);
            }
        }
    }

    private void startNavigation(FakePlayer fp, boolean once, Location dest) {
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.ATTACK,
                        () -> dest,
                        Config.pathfindingArrivalDistance(),
                        0.0,
                        Integer.MAX_VALUE,
                        () -> lockAndStartClassic(fp, once, dest),
                        null,
                        null));
    }

    private void lockAndStartClassic(FakePlayer fp, boolean once, Location lockLoc) {
        FppApiImpl.fireTaskEvent(fp, "attack", FppBotTaskEvent.Action.START);
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null) return;

        FppScheduler.teleportAsync(bot, lockLoc);
        manager.lockForAction(uuid, lockLoc);

        activeAttackLocations.put(uuid, lockLoc.clone());
        activeAttackOnceFlags.put(uuid, once);

        AttackState state = new AttackState();
        state.mobMode = false;
        attackStates.put(uuid, state);

        int taskId =
                FppScheduler.runAtEntityRepeatingWithId(plugin, bot, () -> tickClassicAttack(fp, uuid, once), 0L, 1L);
        attackTasks.put(uuid, taskId);
    }

    private void navigateThenMob(FakePlayer fp, MobFlags flags, Location dest) {
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.ATTACK,
                        () -> dest,
                        Config.pathfindingArrivalDistance(),
                        0.0,
                        Integer.MAX_VALUE,
                        () -> startMobMode(fp, flags, dest),
                        null,
                        null));
    }

    private void startMobMode(FakePlayer fp, MobFlags flags, Location lockLoc) {
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null) return;

        if (flags.huntMode()) {
            startHuntMode(fp, flags, lockLoc);
            return;
        }

        if (!flags.smartAttackMode().movesWhileAttacking()) {
            FppScheduler.teleportAsync(bot, lockLoc);
            manager.lockForAction(uuid, lockLoc);
        }

        activeAttackLocations.put(uuid, lockLoc.clone());
        activeAttackOnceFlags.put(uuid, false);

        AttackState state = new AttackState();
        state.mobMode = true;
        state.range = flags.range();
        state.priority = flags.priority();
        state.filterTypes = flags.filterTypes() != null ? new HashSet<>(flags.filterTypes()) : new HashSet<>();
        state.smartAttackMode = flags.smartAttackMode();
        state.moveToTarget = flags.smartAttackMode().movesWhileAttacking();
        state.currentYaw = lockLoc.getYaw();
        state.currentPitch = lockLoc.getPitch();
        attackStates.put(uuid, state);

        int taskId = FppScheduler.runAtEntityRepeatingWithId(plugin, bot, () -> tickMobAttack(fp, uuid), 0L, 1L);
        attackTasks.put(uuid, taskId);
    }

    private void navigateToMobTarget(FakePlayer fp, UUID uuid, AttackState state, LivingEntity target, double reach) {
        if (target == null) return;
        if (pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK)) return;
        if (fp.getPlayer() == null || !fp.getPlayer().isOnline()) return;

        final UUID targetUuid = target.getUniqueId();
        double arrivalDistance = Math.max(2.5, reach - 0.5);
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.ATTACK,
                        () -> {
                            UUID liveTargetUuid =
                                    state.currentTargetUuid != null ? state.currentTargetUuid : targetUuid;
                            Entity live = Bukkit.getEntity(liveTargetUuid);
                            if (!(live instanceof LivingEntity le) || !le.isValid() || le.isDead()) return null;
                            Player bot = fp.getPlayer();
                            if (bot == null || !bot.isOnline() || le.getWorld() != bot.getWorld()) return null;
                            return le.getLocation();
                        },
                        arrivalDistance,
                        3.5,
                        3,
                        null,
                        null,
                        null));
    }

    private void startHuntMode(FakePlayer fp, MobFlags flags, Location startLoc) {
        UUID uuid = fp.getUuid();
        Player bot = fp.getPlayer();
        if (bot == null) return;

        activeAttackLocations.put(uuid, startLoc.clone());
        activeAttackOnceFlags.put(uuid, false);

        AttackState state = new AttackState();
        state.mobMode = true;
        state.huntMode = true;
        state.range = flags.range();
        state.priority = flags.priority();
        state.filterTypes = flags.filterTypes() != null ? new HashSet<>(flags.filterTypes()) : new HashSet<>();
        state.moveToTarget = false;
        state.currentYaw = bot.getLocation().getYaw();
        state.currentPitch = bot.getLocation().getPitch();
        attackStates.put(uuid, state);

        // 1-tick task: rotation + melee when close
        int attackTaskId = FppScheduler.runAtEntityRepeatingWithId(plugin, bot, () -> tickMobAttack(fp, uuid), 0L, 1L);
        attackTasks.put(uuid, attackTaskId);

        // 20-tick task: find next target and navigate to it
        int scanTaskId = FppScheduler.runAtEntityRepeatingWithId(plugin, bot, () -> tickHuntScan(fp, uuid), 0L, 20L);
        huntScanTasks.put(uuid, scanTaskId);
    }

    private void tickHuntScan(FakePlayer fp, UUID uuid) {
        Player b = fp.getPlayer();
        if (b == null || !b.isOnline()) {
            stopAttacking(uuid);
            return;
        }

        AttackState state = attackStates.get(uuid);
        if (state == null) {
            stopAttacking(uuid);
            return;
        }

        // Let any in-flight navigation finish before picking a new target
        if (pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK)) return;

        LivingEntity target = findBestTarget(b, state);
        if (target == null) return;

        double dist = b.getLocation().distance(target.getLocation());
        double reach = ((CraftPlayer) b).getHandle().gameMode.isCreative() ? 5.0 : 3.5;
        if (dist <= reach) return; // already in melee range — combat tick handles it

        final UUID targetUuid = target.getUniqueId();
        PathfindingService.NavigationRequest req = new PathfindingService.NavigationRequest(
                PathfindingService.Owner.ATTACK,
                () -> {
                    Entity t = Bukkit.getEntity(targetUuid);
                    return (t != null && t.isValid() && !((LivingEntity) t).isDead()) ? t.getLocation() : null;
                },
                2.2, // arrival distance — close enough to melee
                3.5, // recalc when target moves this far
                3, // max null-path recalculations before giving up
                () -> {}, // onArrive — combat tick handles the attack
                () -> {}, // onCancel
                () -> {} // onPathFailure
                );
        pathfinding.navigate(fp, req);
    }

    private void tickClassicAttack(FakePlayer fp, UUID uuid, boolean once) {
        Player b = fp.getPlayer();
        if (b == null || !b.isOnline()) {
            stopAttacking(uuid);
            return;
        }

        if (fp.isInventoryOpen()) {
            return;
        }

        AttackState state = attackStates.get(uuid);
        if (state == null) {
            stopAttacking(uuid);
            return;
        }

        if (state.cooldownTicks > 0) {
            state.cooldownTicks--;
            return;
        }

        ItemStack mainHand = b.getInventory().getItemInMainHand();
        Material weapon = (mainHand != null && mainHand.getType() != Material.AIR) ? mainHand.getType() : null;

        ServerPlayer nms = ((CraftPlayer) b).getHandle();

        var nmsMainHand = nms.getItemInHand(InteractionHand.MAIN_HAND);
        if (nms.getCooldowns().isOnCooldown(nmsMainHand)) return;

        EntityHitResult entityHit = rayTraceForEntity(nms);
        if (entityHit == null) return;

        var nmsEntity = entityHit.getEntity();
        Entity bukkit = nmsEntity.getBukkitEntity();
        if (manager.getByUuid(bukkit.getUniqueId()) != null) return;

        b.swingMainHand();

        var atkEvt = new FppBotAttackEvent(new FppBotImpl(fp), bukkit, 1.0);
        Bukkit.getPluginManager().callEvent(atkEvt);
        if (atkEvt.isCancelled()) return;

        NmsPlayerSpawner.performAttack(b, bukkit, 1.0);

        state.cooldownTicks = getWeaponCooldown(weapon);

        if (once) stopAttacking(uuid);
    }

    private void tickMobAttack(FakePlayer fp, UUID uuid) {
        Player b = fp.getPlayer();
        if (b == null || !b.isOnline()) {
            stopAttacking(uuid);
            return;
        }

        if (fp.isInventoryOpen()) {
            return;
        }

        AttackState state = attackStates.get(uuid);
        if (state == null) {
            stopAttacking(uuid);
            return;
        }

        LivingEntity currentTarget = resolveTarget(state);
        if (currentTarget != null && !isValidTarget(currentTarget, b, state)) {
            state.currentTargetUuid = null;
            currentTarget = null;
        }

        if (currentTarget == null || state.retargetCountdown <= 0) {
            LivingEntity newTarget = findBestTarget(b, state);
            if (newTarget != null) {
                state.currentTargetUuid = newTarget.getUniqueId();
                state.retargetCountdown = Config.attackMobRetargetInterval();
            } else {
                state.currentTargetUuid = null;
            }
            currentTarget = newTarget;
        }
        state.retargetCountdown--;

        if (currentTarget == null) return;

        Location botEye = b.getEyeLocation();
        Location targetEye = currentTarget.getEyeLocation();
        double dx = targetEye.getX() - botEye.getX();
        double dy = targetEye.getY() - botEye.getY();
        double dz = targetEye.getZ() - botEye.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float desiredPitch = (float) Math.toDegrees(-Math.atan2(dy, horizDist));

        float speed = (float) Config.attackMobSmoothRotationSpeed();
        state.currentYaw = smoothAngle(state.currentYaw, desiredYaw, speed);
        state.currentPitch = smoothAngle(state.currentPitch, desiredPitch, speed * 0.8f);

        ServerPlayer nms = ((CraftPlayer) b).getHandle();
        nms.setYRot(state.currentYaw);
        nms.setXRot(state.currentPitch);
        nms.setYHeadRot(state.currentYaw);

        // --move mode: chase the target when out of reach; stop when in melee range.
        double dist = b.getLocation().distance(currentTarget.getLocation());
        double reach = nms.gameMode.isCreative() ? 5.0 : 3.5;
        if (state.moveToTarget) {
            if (dist > reach) {
                navigateToMobTarget(fp, uuid, state, currentTarget, reach);
                return;
            }
            if (pathfinding.isNavigating(uuid, PathfindingService.Owner.ATTACK)) {
                pathfinding.cancel(uuid);
            }
        } else if (!state.huntMode) {
            // stationary mob mode: keep bot frozen at lock location
            manager.updateActionLockRotation(uuid, state.currentYaw, state.currentPitch);
        }
        // hunt mode: PathfindingService drives movement; rotation already applied above

        if (state.cooldownTicks > 0) {
            state.cooldownTicks--;
            return;
        }

        if (dist > reach) return;

        var nmsMainHand = nms.getItemInHand(InteractionHand.MAIN_HAND);
        if (nms.getCooldowns().isOnCooldown(nmsMainHand)) return;

        net.minecraft.world.entity.Entity nmsTarget = nms.level().getEntity(currentTarget.getEntityId());
        if (nmsTarget == null) return;

        b.swingMainHand();

        var atkEvt2 = new FppBotAttackEvent(new FppBotImpl(fp), currentTarget, 1.0);
        Bukkit.getPluginManager().callEvent(atkEvt2);
        if (atkEvt2.isCancelled()) return;
        NmsPlayerSpawner.performAttack(b, currentTarget, 1.0);

        ItemStack mainHand = b.getInventory().getItemInMainHand();
        Material weapon = (mainHand != null && mainHand.getType() != Material.AIR) ? mainHand.getType() : null;
        state.cooldownTicks = getWeaponCooldown(weapon);
    }

    @Nullable
    private static LivingEntity resolveTarget(AttackState state) {
        if (state.currentTargetUuid == null) return null;
        Entity e = Bukkit.getEntity(state.currentTargetUuid);
        if (e instanceof LivingEntity le && le.isValid() && !le.isDead()) return le;
        return null;
    }

    private static boolean isValidTarget(LivingEntity target, Player bot, AttackState state) {
        if (target.isDead() || !target.isValid()) return false;
        if (target.getWorld() != bot.getWorld()) return false;
        if (target.getLocation().distance(bot.getLocation()) > state.range) return false;
        if (Config.attackMobLineOfSight() && !bot.hasLineOfSight(target)) return false;
        return true;
    }

    @Nullable
    private LivingEntity findBestTarget(Player bot, AttackState state) {
        Location botLoc = bot.getLocation();
        Collection<Entity> nearby = bot.getWorld().getNearbyEntities(botLoc, state.range, state.range, state.range);

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le instanceof Player) continue;
            if (le.isDead() || !le.isValid()) continue;

            if (!state.filterTypes.isEmpty()) {
                if (!state.filterTypes.contains(le.getType())) continue;
            } else {

                if (!(le instanceof Monster)
                        && !(le instanceof Slime)
                        && !(le instanceof Shulker)
                        && !(le instanceof Phantom)
                        && !(le instanceof EnderDragon)
                        && !(le instanceof Ghast)
                        && !(le instanceof Hoglin)) continue;
            }

            double dist = le.getLocation().distance(botLoc);
            if (dist > state.range) continue;

            if (Config.attackMobLineOfSight() && !bot.hasLineOfSight(le)) continue;

            double score = "lowest-health".equals(state.priority) ? le.getHealth() + (dist * 0.01) : dist;

            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }

        return best;
    }

    private static float smoothAngle(float current, float target, float maxStep) {
        float diff = wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    private static int getWeaponCooldown(@Nullable Material weapon) {
        if (weapon == null) return DEFAULT_COOLDOWN;
        return WEAPON_COOLDOWN.getOrDefault(weapon, DEFAULT_COOLDOWN);
    }

    private void cancelAll(UUID botUuid) {
        pathfinding.cancel(botUuid);
        stopAttacking(botUuid);

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

    public void startMobModeFromSettings(FakePlayer fp) {
        if (fp == null) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;

        stopAttacking(fp.getUuid());

        Set<EntityType> filterTypes = new HashSet<>();
        for (String mobTypeName : fp.getPveMobTypes()) {
            try {
                filterTypes.add(EntityType.valueOf(mobTypeName));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (!fp.isPveEnabled()) return;
        MobFlags flags =
                new MobFlags(fp.getPveRange(), fp.getPvePriority(), filterTypes, fp.getPveSmartAttackMode(), false);
        startMobMode(fp, flags, bot.getLocation());
    }

    public void stopAttacking(UUID botUuid) {
        pathfinding.cancel(botUuid);

        Integer taskId = attackTasks.remove(botUuid);
        if (taskId != null) FppScheduler.cancelTask(taskId);

        Integer scanTaskId = huntScanTasks.remove(botUuid);
        if (scanTaskId != null) FppScheduler.cancelTask(scanTaskId);

        AttackState state = attackStates.remove(botUuid);

        // If the bot was chasing (--move mode), stop its movement.
        if (state != null && state.moveToTarget) {
            FakePlayer fp = manager.getByUuid(botUuid);
            if (fp != null) {
                Player bot = fp.getPlayer();
                if (bot != null) {
                    bot.setSprinting(false);
                    NmsPlayerSpawner.setMovementForward(bot, 0f);
                }
            }
        } else if (state == null || !state.huntMode) {
            manager.unlockAction(botUuid);
        }
        // hunt mode: no action-lock was held; PathfindingService cancellation is handled by cancelAll()

        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            FppApiImpl.fireTaskEvent(fp, "attack", FppBotTaskEvent.Action.STOP);
        }

        activeAttackLocations.remove(botUuid);
        activeAttackOnceFlags.remove(botUuid);
    }

    public void stopAll() {
        pathfinding.cancelAll(PathfindingService.Owner.ATTACK);
        new HashSet<>(huntScanTasks.keySet()).forEach(uuid -> {
            Integer id = huntScanTasks.remove(uuid);
            if (id != null) FppScheduler.cancelTask(id);
        });
        new HashSet<>(attackTasks.keySet()).forEach(this::cancelAll);
    }

    public boolean isAttacking(UUID botUuid) {
        return attackTasks.containsKey(botUuid);
    }

    public boolean isNavigating(UUID botUuid) {
        return pathfinding.isNavigating(botUuid, PathfindingService.Owner.ATTACK);
    }

    @Nullable
    public Location getActiveAttackLocation(UUID botUuid) {
        return activeAttackLocations.get(botUuid);
    }

    public boolean isActiveAttackOnce(UUID botUuid) {
        Boolean v = activeAttackOnceFlags.get(botUuid);
        return v != null && v;
    }

    public boolean isMobMode(UUID botUuid) {
        AttackState state = attackStates.get(botUuid);
        return state != null && state.mobMode;
    }

    public boolean isHuntMode(UUID botUuid) {
        AttackState state = attackStates.get(botUuid);
        return state != null && state.huntMode;
    }

    public String getAttackMode(UUID botUuid) {
        AttackState state = attackStates.get(botUuid);
        if (state == null) return null;
        if (state.huntMode) return "hunt";
        if (state.mobMode) return "mob";
        return "classic";
    }

    public double getAttackRange(UUID botUuid) {
        AttackState state = attackStates.get(botUuid);
        return state != null ? state.range : Config.attackMobDefaultRange();
    }

    public String getAttackPriority(UUID botUuid) {
        AttackState state = attackStates.get(botUuid);
        return state != null ? state.priority : Config.attackMobDefaultPriority();
    }

    public Set<EntityType> getAttackFilterTypes(UUID botUuid) {
        AttackState state = attackStates.get(botUuid);
        return state != null ? Collections.unmodifiableSet(state.filterTypes) : Collections.emptySet();
    }

    public FakePlayer.PveSmartAttackMode getAttackSmartMode(UUID botUuid) {
        AttackState state = attackStates.get(botUuid);
        return state != null ? state.smartAttackMode : FakePlayer.PveSmartAttackMode.OFF;
    }

    public void resumeAttacking(FakePlayer fp) {
        UUID uuid = fp.getUuid();
        Location attackLoc = getActiveAttackLocation(uuid);
        boolean once = isActiveAttackOnce(uuid);
        if (attackLoc != null) {
            resumeAttacking(fp, once, attackLoc);
        }
    }

    public void resumeAttacking(FakePlayer fp, boolean once, Location loc) {
        if (fp == null || loc == null) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        cancelAll(fp.getUuid());
        if (PathfindingService.xzDist(bot.getLocation(), loc) <= Config.pathfindingArrivalDistance()) {
            lockAndStartClassic(fp, once, loc);
        } else {
            startNavigation(fp, once, loc);
        }
    }

    public void resumeMobAttacking(
            FakePlayer fp,
            double range,
            String priority,
            Set<EntityType> filterTypes,
            FakePlayer.PveSmartAttackMode smartMode,
            Location loc) {
        if (fp == null || loc == null) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        cancelAll(fp.getUuid());
        MobFlags flags = new MobFlags(range, priority, filterTypes, smartMode, false);
        if (PathfindingService.xzDist(bot.getLocation(), loc) <= Config.pathfindingArrivalDistance()) {
            startMobMode(fp, flags, loc);
        } else {
            navigateThenMob(fp, flags, loc);
        }
    }

    public void resumeHuntAttacking(
            FakePlayer fp, double range, String priority, Set<EntityType> filterTypes, Location loc) {
        if (fp == null || loc == null) return;
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;
        cancelAll(fp.getUuid());
        MobFlags flags = new MobFlags(range, priority, filterTypes, FakePlayer.PveSmartAttackMode.ON_MOVE, true);
        startHuntMode(fp, flags, loc);
    }

    @SuppressWarnings("resource")
    @Nullable
    private static EntityHitResult rayTraceForEntity(ServerPlayer player) {
        double reach = player.gameMode.isCreative() ? 5.0 : 4.5;
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 viewVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(viewVec.x * reach, viewVec.y * reach, viewVec.z * reach);

        AABB searchBox =
                player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0);

        EntityHitResult best = null;
        double bestSq = reach * reach;

        for (var entity : player.level().getEntities(player, searchBox, e -> !e.isSpectator() && e.isAttackable())) {

            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            var hitOpt = box.clip(eyePos, endPos);

            if (box.contains(eyePos)) {
                if (bestSq >= 0) {
                    best = new EntityHitResult(entity, hitOpt.orElse(eyePos));
                    bestSq = 0;
                }
            } else if (hitOpt.isPresent()) {
                double d = eyePos.distanceToSqr(hitOpt.get());
                if (d < bestSq) {
                    best = new EntityHitResult(entity, hitOpt.get());
                    bestSq = d;
                }
            }
        }

        return best;
    }
}
