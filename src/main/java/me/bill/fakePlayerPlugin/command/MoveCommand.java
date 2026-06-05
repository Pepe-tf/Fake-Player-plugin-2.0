package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotPathfinder;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public final class MoveCommand implements FppCommand {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final PathfindingService pathfinding;

    private final Map<UUID, Location> roamCenters = new ConcurrentHashMap<>();

    private final Map<UUID, Double> roamRadii = new ConcurrentHashMap<>();

    private final Set<UUID> infiniteRoam = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Integer> roamPauseTasks = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> roamLookTasks = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> roamFailureCounts = new ConcurrentHashMap<>();

    public MoveCommand(FakePlayerPlugin plugin, FakePlayerManager manager, PathfindingService pathfinding) {
        this.plugin = plugin;
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    @Override
    public String getName() {
        return "move";
    }

    @Override
    public String getUsage() {
        return "<bot|all> --to <player>  |  <bot|all> --coords <x> <y> <z> [alias: --pos]  |  <bot|all> --roam [x,y,z] [radius]  |  <bot|all> --stop";
    }

    @Override
    public String getDescription() {
        return "Navigate a bot (or all bots) to a player or roam randomly within an area.";
    }

    @Override
    public String getPermission() {
        return Perm.MOVE;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.MOVE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("--all")) {
            return executeAll(sender, args);
        }

        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Lang.get("move-bot-not-found", "name", args[0]));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("move-bot-not-online", "name", args[0]));
            return true;
        }

        if (args.length >= 2 && args[1].startsWith("--")) {
            String flag = args[1].toLowerCase();

            if (flag.equals("--stop")) {
                boolean wasRoaming = isRoaming(bot.getUniqueId());
                if (!wasRoaming && !pathfinding.isNavigating(bot.getUniqueId(), PathfindingService.Owner.MOVE)) {
                    sender.sendMessage(Lang.get("move-not-navigating", "name", fp.getDisplayName()));
                } else {
                    cancelNavigation(bot.getUniqueId());
                    sender.sendMessage(Lang.get("move-stopped", "name", fp.getDisplayName()));
                }
                return true;
            }

            if (flag.equals("--roam")) {
                return handleRoam(sender, fp, bot, args);
            }

            if (flag.equals("--to")) {
                if (args.length < 3) {
                    sender.sendMessage(Lang.get("move-usage"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(Lang.get("player-not-found", "player", args[2]));
                    return true;
                }
                if (!bot.getWorld().equals(target.getWorld())) {
                    sender.sendMessage(
                            Lang.get("move-different-world", "name", fp.getDisplayName(), "player", target.getName()));
                    return true;
                }
                cancelNavigation(bot.getUniqueId());
                startNavigation(bot, target);
                sender.sendMessage(
                        Lang.get("move-navigating", "name", fp.getDisplayName(), "player", target.getName()));
                return true;
            }

            if (flag.equals("--coords") || flag.equals("--pos")) {
                if (args.length < 5) {
                    sender.sendMessage(Lang.get("move-coords-usage"));
                    return true;
                }
                try {
                    double bx = bot.getLocation().getX();
                    double by = bot.getLocation().getY();
                    double bz = bot.getLocation().getZ();
                    double cx = parseCoord(args[2], bx);
                    double cy = parseCoord(args[3], by);
                    double cz = parseCoord(args[4], bz);
                    Location dest = new Location(bot.getWorld(), cx, cy, cz);
                    cancelNavigation(bot.getUniqueId());
                    startNavigationToLocation(bot, dest);
                    sender.sendMessage(Lang.get(
                            "move-coords-navigating",
                            "name",
                            fp.getDisplayName(),
                            "x",
                            String.valueOf((int) cx),
                            "y",
                            String.valueOf((int) cy),
                            "z",
                            String.valueOf((int) cz)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.get("move-coords-invalid"));
                }
                return true;
            }

            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Lang.get("player-not-found", "player", args[1]));
                return true;
            }

            if (!bot.getWorld().equals(target.getWorld())) {
                sender.sendMessage(
                        Lang.get("move-different-world", "name", fp.getDisplayName(), "player", target.getName()));
                return true;
            }

            cancelNavigation(bot.getUniqueId());
            startNavigation(bot, target);

            sender.sendMessage(Lang.get("move-navigating", "name", fp.getDisplayName(), "player", target.getName()));
            return true;
        }

        sender.sendMessage(Lang.get("move-usage"));
        return true;
    }

    private boolean handleRoam(CommandSender sender, FakePlayer fp, Player bot, String[] args) {
        Location center = bot.getLocation().clone();
        double radius = 20.0;
        boolean infinite = false;

        if (args.length > 2) {
            String token = args[2];
            if (token.equalsIgnoreCase("infinite")
                    || token.equalsIgnoreCase("forever")
                    || token.equalsIgnoreCase("unbounded")) {
                infinite = true;
                if (args.length > 3) {
                    try {
                        radius = Double.parseDouble(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Lang.get("move-roam-invalid-radius"));
                        return true;
                    }
                } else {
                    radius = 80.0;
                }
            } else if (token.contains(",") || token.contains(" ")) {

                String[] parts = token.contains(",") ? token.split(",") : token.split(" ");
                if (parts.length != 3) {
                    sender.sendMessage(Lang.get("move-roam-invalid-coords"));
                    return true;
                }
                try {
                    double cx = parseCoord(
                            parts[0],
                            sender instanceof Player p ? p.getLocation().getX() : 0);
                    double cy = parseCoord(
                            parts[1],
                            sender instanceof Player p ? p.getLocation().getY() : 64);
                    double cz = parseCoord(
                            parts[2],
                            sender instanceof Player p ? p.getLocation().getZ() : 0);
                    center = new Location(bot.getWorld(), cx, cy, cz);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.get("move-roam-invalid-coords"));
                    return true;
                }

                if (args.length > 3) {
                    try {
                        radius = Double.parseDouble(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Lang.get("move-roam-invalid-radius"));
                        return true;
                    }
                }
            } else {

                try {
                    radius = Double.parseDouble(token);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.get("move-roam-usage"));
                    return true;
                }
            }
            if (radius < 3 || radius > 500) {
                sender.sendMessage(Lang.get("move-roam-invalid-radius"));
                return true;
            }
        }

        cancelNavigation(bot.getUniqueId());
        startRoaming(fp, center, radius, infinite);
        sender.sendMessage(Lang.get(
                "move-roam-started",
                "name",
                fp.getDisplayName(),
                "x",
                String.valueOf((int) center.getX()),
                "y",
                String.valueOf((int) center.getY()),
                "z",
                String.valueOf((int) center.getZ()),
                "radius",
                String.valueOf((int) radius)));
        return true;
    }

    private double parseCoord(String raw, double relative) {
        raw = raw.trim();
        if (raw.startsWith("~")) {
            String offset = raw.substring(1);
            if (offset.isEmpty()) return relative;
            return relative + Double.parseDouble(offset);
        }
        return Double.parseDouble(raw);
    }

    private boolean executeAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        String flag = args[1].toLowerCase();

        if (flag.equals("--stop")) {
            int stopped = 0;
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
                    if (!BotAccess.canAdminister(player, fp)) {
                        continue;
                    }
                }
                Player bot = fp.getPlayer();
                if (bot == null) continue;
                UUID uid = bot.getUniqueId();
                if (isRoaming(uid) || pathfinding.isNavigating(uid, PathfindingService.Owner.MOVE)) {
                    cancelNavigation(uid);
                    stopped++;
                }
            }
            sender.sendMessage(Lang.get("move-all-stopped", "count", String.valueOf(stopped)));
            return true;
        }

        if (flag.equals("--roam")) {

            Location center = null;
            double radius = 20.0;
            boolean infinite = false;
            if (args.length > 2) {
                String token = args[2];
                if (token.equalsIgnoreCase("infinite")
                        || token.equalsIgnoreCase("forever")
                        || token.equalsIgnoreCase("unbounded")) {
                    infinite = true;
                    radius = 80.0;
                    if (args.length > 3) {
                        try {
                            radius = Double.parseDouble(args[3]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else if (token.contains(",") || token.contains(" ")) {
                    String[] parts = token.contains(",") ? token.split(",") : token.split(" ");
                    if (parts.length == 3) {
                        try {
                            double cx = sender instanceof Player p
                                    ? parseCoord(parts[0], p.getLocation().getX())
                                    : Double.parseDouble(parts[0]);
                            double cy = sender instanceof Player p
                                    ? parseCoord(parts[1], p.getLocation().getY())
                                    : Double.parseDouble(parts[1]);
                            double cz = sender instanceof Player p
                                    ? parseCoord(parts[2], p.getLocation().getZ())
                                    : Double.parseDouble(parts[2]);
                            if (sender instanceof Player p) {
                                center = new Location(p.getWorld(), cx, cy, cz);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (args.length > 3) {
                        try {
                            radius = Double.parseDouble(args[3]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    try {
                        radius = Double.parseDouble(token);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            radius = Math.max(3, Math.min(500, radius));

            int started = 0, skipped = 0;
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
                    if (!BotAccess.canAdminister(player, fp)) {
                        skipped++;
                        continue;
                    }
                }
                Player bot = fp.getPlayer();
                if (bot == null || !bot.isOnline()) {
                    skipped++;
                    continue;
                }
                cancelNavigation(bot.getUniqueId());
                Location c = center != null ? center.clone() : bot.getLocation().clone();
                if (center != null) c.setWorld(bot.getWorld());
                startRoaming(fp, c, radius, infinite);
                started++;
            }
            sender.sendMessage(Lang.get(
                    "move-all-roam-started",
                    "count",
                    String.valueOf(started),
                    "radius",
                    String.valueOf((int) radius),
                    "skipped",
                    String.valueOf(skipped)));
            return true;
        }

        if (flag.equals("--coords") || flag.equals("--pos")) {
            if (args.length < 5) {
                sender.sendMessage(Lang.get("move-coords-usage"));
                return true;
            }
            Location base = sender instanceof Player p ? p.getLocation() : null;
            try {
                double cx = parseCoord(args[2], base != null ? base.getX() : 0);
                double cy = parseCoord(args[3], base != null ? base.getY() : 64);
                double cz = parseCoord(args[4], base != null ? base.getZ() : 0);
                int started = 0, skipped = 0;
                for (FakePlayer fp : manager.getActivePlayers()) {
                    if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
                        if (!BotAccess.canAdminister(player, fp)) {
                            skipped++;
                            continue;
                        }
                    }
                    Player bot = fp.getPlayer();
                    if (bot == null || !bot.isOnline()) {
                        skipped++;
                        continue;
                    }
                    Location dest = new Location(bot.getWorld(), cx, cy, cz);
                    cancelNavigation(bot.getUniqueId());
                    startNavigationToLocation(bot, dest);
                    started++;
                }
                sender.sendMessage(Lang.get(
                        "move-all-navigating-coords",
                        "count",
                        String.valueOf(started),
                        "skipped",
                        String.valueOf(skipped),
                        "x",
                        String.valueOf((int) cx),
                        "y",
                        String.valueOf((int) cy),
                        "z",
                        String.valueOf((int) cz)));
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.get("move-coords-invalid"));
            }
            return true;
        }

        if (flag.equals("--to")) {
            if (args.length < 3) {
                sender.sendMessage(Lang.get("move-usage"));
                return true;
            }
            return moveAllToPlayer(sender, args[2]);
        }

        return moveAllToPlayer(sender, args[1]);
    }

    private boolean moveAllToPlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(Lang.get("player-not-found", "player", playerName));
            return true;
        }

        int started = 0, skipped = 0;
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (sender instanceof Player player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
                if (!BotAccess.canAdminister(player, fp)) {
                    skipped++;
                    continue;
                }
            }
            Player bot = fp.getPlayer();
            if (bot == null || !bot.isOnline()) {
                skipped++;
                continue;
            }
            if (!bot.getWorld().equals(target.getWorld())) {
                skipped++;
                continue;
            }
            cancelNavigation(bot.getUniqueId());
            startNavigation(bot, target);
            started++;
        }
        sender.sendMessage(Lang.get(
                "move-all-navigating",
                "player",
                target.getName(),
                "count",
                String.valueOf(started),
                "skipped",
                String.valueOf(skipped)));
        return true;
    }

    private void startNavigation(@NotNull Player bot, @NotNull Player target) {
        final UUID botUuid = bot.getUniqueId();
        final UUID targetUuid = target.getUniqueId();
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) return;
        FppApiImpl.fireTaskEvent(fp, "patrol", FppBotTaskEvent.Action.START);
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.MOVE,
                        () -> {
                            Player liveTarget = Bukkit.getPlayer(targetUuid);
                            if (liveTarget == null || !liveTarget.isOnline()) return null;
                            return liveTarget.getLocation();
                        },
                        Config.pathfindingArrivalDistance(),
                        Config.pathfindingFollowRecalcDistance(),
                        Integer.MAX_VALUE,
                        () -> clearMoveState(botUuid),
                        () -> clearMoveState(botUuid),
                        null));
    }

    private void startNavigationToLocation(@NotNull Player bot, @NotNull Location dest) {
        final UUID botUuid = bot.getUniqueId();
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) return;
        FppApiImpl.fireTaskEvent(fp, "move", FppBotTaskEvent.Action.START);
        final Location fixedDest = dest.clone();
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.MOVE,
                        () -> fixedDest,
                        Config.pathfindingArrivalDistance(),
                        0.0,
                        20,
                        () -> clearMoveState(botUuid),
                        () -> clearMoveState(botUuid),
                        null));
    }

    private void startRoaming(@NotNull FakePlayer fp, @NotNull Location center, double radius) {
        startRoaming(fp, center, radius, false);
    }

    private void startRoaming(@NotNull FakePlayer fp, @NotNull Location center, double radius, boolean infinite) {
        UUID uid = fp.getUuid();
        roamCenters.put(uid, center.clone());
        roamRadii.put(uid, radius);
        if (infinite) infiniteRoam.add(uid);
        else infiniteRoam.remove(uid);
        Player bot = fp.getPlayer();
        if (bot == null) {
            clearRoamState(uid);
            return;
        }
        int delay = Math.floorMod(uid.hashCode(), 40);
        int pause = FppScheduler.runAtEntityLaterWithId(
                plugin,
                bot,
                () -> {
                    roamPauseTasks.remove(uid);
                    navigateRoamStep(uid);
                },
                delay);
        roamPauseTasks.put(uid, pause);
    }

    private void navigateRoamStep(@NotNull UUID botUuid) {
        if (!roamCenters.containsKey(botUuid)) return;

        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) {
            clearRoamState(botUuid);
            return;
        }
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            clearRoamState(botUuid);
            return;
        }

        Location center = infiniteRoam.contains(botUuid) ? bot.getLocation().clone() : roamCenters.get(botUuid);
        Double radius = roamRadii.get(botUuid);
        if (center == null || radius == null) {
            clearRoamState(botUuid);
            return;
        }

        Location dest = findRandomWalkableLocation(bot.getWorld(), center, radius);
        if (dest == null) {

            int pause = FppScheduler.runAtEntityLaterWithId(
                    plugin,
                    bot,
                    () -> {
                        roamPauseTasks.remove(botUuid);
                        navigateRoamStep(botUuid);
                    },
                    40L);
            roamPauseTasks.put(botUuid, pause);
            return;
        }

        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.MOVE,
                        () -> dest,
                        Config.pathfindingPatrolArrivalDistance(),
                        0.0,
                        3,
                        () -> onRoamArrival(botUuid),
                        () -> clearRoamState(botUuid),
                        () -> onRoamPathFailure(botUuid)));
    }

    private void onRoamPathFailure(@NotNull UUID botUuid) {
        if (!roamCenters.containsKey(botUuid)) return;
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) {
            clearRoamState(botUuid);
            return;
        }
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            clearRoamState(botUuid);
            return;
        }
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);
        int failures = roamFailureCounts.merge(botUuid, 1, Integer::sum);
        long delay = Math.min(80L, 10L + failures * 10L);
        int pause = FppScheduler.runAtEntityLaterWithId(
                plugin,
                bot,
                () -> {
                    roamPauseTasks.remove(botUuid);
                    navigateRoamStep(botUuid);
                },
                delay);
        roamPauseTasks.put(botUuid, pause);
    }

    private void onRoamArrival(@NotNull UUID botUuid) {
        if (!roamCenters.containsKey(botUuid)) return;

        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp == null) {
            clearRoamState(botUuid);
            return;
        }
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            clearRoamState(botUuid);
            return;
        }

        NmsPlayerSpawner.setMovementForward(bot, 0f);
        bot.setSprinting(false);
        roamFailureCounts.remove(botUuid);

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int pauseTicks = rng.nextInt(30, 121);

        int lookCount = rng.nextInt(1, 4);
        int lookInterval = Math.max(10, pauseTicks / (lookCount + 1));

        int lookTask = FppScheduler.runAtEntityRepeatingWithId(
                plugin,
                bot,
                new Runnable() {
                    int ticks = 0;
                    int looksDone = 0;
                    float baseYaw = bot.getLocation().getYaw();

                    @Override
                    public void run() {
                        ticks++;
                        Player b = fp.getPlayer();
                        if (b == null || !b.isOnline() || !roamCenters.containsKey(botUuid)) return;

                        if (looksDone < lookCount && ticks % lookInterval == 0) {

                            float newYaw = baseYaw + rng.nextFloat(-60f, 61f);
                            float newPitch = rng.nextFloat(-15f, 26f);
                            b.setRotation(newYaw, newPitch);
                            NmsPlayerSpawner.setHeadYaw(b, newYaw);
                            looksDone++;
                        }
                    }
                },
                5L,
                1L);
        roamLookTasks.put(botUuid, lookTask);

        int pause = FppScheduler.runAtEntityLaterWithId(
                plugin,
                bot,
                () -> {
                    cancelRoamPauseTasks(botUuid);
                    navigateRoamStep(botUuid);
                },
                pauseTicks);
        roamPauseTasks.put(botUuid, pause);
    }

    @Nullable
    private Location findRandomWalkableLocation(@NotNull World world, @NotNull Location center, double radius) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int r = (int) radius;

        for (int attempt = 0; attempt < 80; attempt++) {

            double angle = rng.nextDouble(Math.PI * 2);
            double dist = Math.sqrt(rng.nextDouble()) * r;
            int tx = cx + (int) Math.round(Math.cos(angle) * dist);
            int tz = cz + (int) Math.round(Math.sin(angle) * dist);

            int ty = findWalkableY(world, tx, tz, center.getBlockY());
            if (ty >= world.getMinHeight()
                    && BotPathfinder.walkable(world, tx, ty, tz)
                    && hasRoamClearance(world, tx, ty, tz)) {
                return new Location(world, tx + 0.5, ty, tz + 0.5);
            }
        }
        return null;
    }

    private boolean hasRoamClearance(@NotNull World world, int x, int y, int z) {
        int open = 0;
        int blocked = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int nz = z + dir[1];
            boolean clear = BotPathfinder.canPassThrough(world, nx, y, nz)
                    && BotPathfinder.canPassThrough(world, nx, y + 1, nz);
            if (clear) open++;
            else blocked++;
        }
        return open >= 3 && blocked <= 1;
    }

    private int findWalkableY(@NotNull World world, int x, int z, int refY) {
        int minY = Math.max(world.getMinHeight(), refY - 16);
        int maxY = Math.min(world.getMaxHeight() - 2, refY + 16);
        int bestY = world.getMinHeight() - 1;
        int bestDist = Integer.MAX_VALUE;
        for (int y = maxY; y >= minY; y--) {
            if (BotPathfinder.walkable(world, x, y, z)) {
                int d = Math.abs(y - refY);
                if (d < bestDist) {
                    bestDist = d;
                    bestY = y;
                    if (d == 0) break;
                }
            }
        }
        return bestY;
    }

    private void cancelRoamPauseTasks(@NotNull UUID botUuid) {
        Integer look = roamLookTasks.remove(botUuid);
        if (look != null) FppScheduler.cancelTask(look);
        Integer pause = roamPauseTasks.remove(botUuid);
        if (pause != null) FppScheduler.cancelTask(pause);
    }

    private void clearRoamState(@NotNull UUID botUuid) {
        roamCenters.remove(botUuid);
        roamRadii.remove(botUuid);
        infiniteRoam.remove(botUuid);
        roamFailureCounts.remove(botUuid);
        cancelRoamPauseTasks(botUuid);
    }

    private void clearMoveState(@NotNull UUID botUuid) {
        clearRoamState(botUuid);
        FakePlayer fp = manager.getByUuid(botUuid);
        Player bot = fp != null ? fp.getPlayer() : null;
        if (bot != null && bot.isOnline()) {
            NmsPlayerSpawner.setMovementForward(bot, 0f);
            NmsPlayerSpawner.setMovementStrafe(bot, 0f);
            NmsPlayerSpawner.setJumping(bot, false);
            bot.setSprinting(false);
        }
    }

    private void cancelNavigation(@NotNull UUID botUuid) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            FppApiImpl.fireTaskEvent(fp, "move", FppBotTaskEvent.Action.STOP);
        }
        pathfinding.cancel(botUuid);
        clearMoveState(botUuid);
    }

    public void cancelAll() {
        pathfinding.cancelAll(PathfindingService.Owner.MOVE);
        new ArrayList<>(roamCenters.keySet()).forEach(this::clearMoveState);
    }

    public void cleanupBot(@NotNull UUID botUuid) {
        cancelNavigation(botUuid);
    }

    public boolean isRoaming(@NotNull UUID botUuid) {
        return roamCenters.containsKey(botUuid);
    }

    @Nullable
    public Location getRoamCenter(@NotNull UUID botUuid) {
        return roamCenters.get(botUuid);
    }

    @Nullable
    public Double getRoamRadius(@NotNull UUID botUuid) {
        return roamRadii.get(botUuid);
    }

    public void resumeRoaming(@NotNull FakePlayer fp, @NotNull Location center, double radius) {
        UUID uid = fp.getUuid();
        cancelNavigation(uid);
        startRoaming(fp, center, radius);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String in = args[0].toLowerCase();

            if ("--all".startsWith(in)) out.add("--all");
            for (FakePlayer fp : manager.getActivePlayers())
                if (fp.getName().toLowerCase().startsWith(in)) out.add(fp.getName());
        } else if (args.length == 2) {
            String in = args[1].toLowerCase();

            for (String flag : List.of("--to", "--coords", "--pos", "--roam", "--stop"))
                if (flag.startsWith(in)) out.add(flag);

            if (!args[0].equalsIgnoreCase("--all")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (manager.getByName(p.getName()) == null
                            && p.getName().toLowerCase().startsWith(in)) out.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("--to")) {
            String in = args[2].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (manager.getByName(p.getName()) == null
                        && p.getName().toLowerCase().startsWith(in)) out.add(p.getName());
            }
        } else if (args.length >= 3
                && args.length <= 5
                && (args[1].equalsIgnoreCase("--coords") || args[1].equalsIgnoreCase("--pos"))) {
            if (sender instanceof Player p) {
                Location loc = p.getLocation();
                if (args.length == 3) out.add(String.valueOf(loc.getBlockX()));
                else if (args.length == 4) out.add(String.valueOf(loc.getBlockY()));
                else out.add(String.valueOf(loc.getBlockZ()));
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("--roam")) {

            if (sender instanceof Player p) {
                Location loc = p.getLocation();
                out.add(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
            }
            out.add("infinite");
            out.add("10");
            out.add("20");
            out.add("30");
            out.add("50");
        } else if (args.length == 4 && args[1].equalsIgnoreCase("--roam")) {

            out.add("10");
            out.add("20");
            out.add("30");
            out.add("50");
        }
        return out;
    }
}
