package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;

/**
 * Pathfinding-only bot movement: walk to another bot/player (following live if they move) or to
 * fixed coordinates. Directional raw-input movement was removed — {@code --to}/{@code --coords}
 * cover every real use case and are simpler to reason about.
 */
public final class MoveCommand implements FppCommand {

    private final FakePlayerManager manager;
    private final PathfindingService pathfinding;

    public MoveCommand(FakePlayerManager manager, PathfindingService pathfinding) {
        this.manager = manager;
        this.pathfinding = pathfinding;
    }

    @Override
    public String getName() {
        return "move";
    }

    @Override
    public String getUsage() {
        return "<bot|--all> --to <bot|player>  |  <bot|--all> --coords <x> <y> <z> [world]  |  <bot|--all> --stop";
    }

    @Override
    public String getDescription() {
        return "Pathfind a bot to another bot/player (follows live) or to fixed coordinates.";
    }

    @Override
    public String getPermission() {
        return Perm.MOVE;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.hasAny(sender, Perm.MOVE, Perm.MOVE_TO, Perm.MOVE_COORDS);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        String target = args[0];
        String flag = args[1].toLowerCase(Locale.ROOT);
        if (!flag.equals("--stop") && !flag.equals("--to") && !flag.equals("--coords")) {
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        if (isAllTarget(target)) {
            return executeAll(sender, flag, args);
        }

        FakePlayer fp = manager.getByName(target);
        if (fp == null) {
            sender.sendMessage(Lang.get("move-bot-not-found", "name", target));
            return true;
        }
        if (sender instanceof Player player && !Perm.has(sender, Perm.ADMIN) && !BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("move-bot-not-online", "name", target));
            return true;
        }

        if (flag.equals("--stop")) {
            stopMovement(fp);
            sender.sendMessage(Lang.get("move-stopped", "name", fp.getDisplayName()));
            return true;
        }

        if (!Perm.has(sender, flag.equals("--to") ? Perm.MOVE_TO : Perm.MOVE_COORDS)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        PathfindTarget dest =
                flag.equals("--to") ? parseToTarget(sender, fp, args) : parseCoordsTarget(sender, bot, args);
        if (dest == null) return true;

        startPathfindMove(fp, dest.locationSupplier(), dest.label(), sender);
        sender.sendMessage(Lang.get("move-pathfind-started", "name", fp.getDisplayName(), "destination", dest.label()));
        return true;
    }

    private boolean executeAll(CommandSender sender, String flag, String[] args) {
        if (sender instanceof Player && !Perm.has(sender, Perm.ADMIN)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (flag.equals("--stop")) {
            int stopped = 0;
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (pathfinding.isNavigating(fp.getUuid(), PathfindingService.Owner.MOVE)) {
                    stopMovement(fp);
                    stopped++;
                }
            }
            pathfinding.cancelAll(PathfindingService.Owner.MOVE);
            sender.sendMessage(Lang.get("move-all-stopped", "count", String.valueOf(stopped)));
            return true;
        }

        if (!Perm.has(sender, flag.equals("--to") ? Perm.MOVE_TO : Perm.MOVE_COORDS)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        // Resolve the destination once, up front — not per bot in the loop, otherwise a "--to"
        // target that happens to be one of the "all" bots would abort the whole batch on its own
        // turn (self-target) instead of just being skipped.
        UUID excludeUuid = null;
        PathfindTarget dest;
        if (flag.equals("--to")) {
            if (args.length < 3) {
                sender.sendMessage(Lang.get("move-usage"));
                return true;
            }
            ResolvedTarget resolved = resolveToTarget(sender, args[2]);
            if (resolved == null) return true;
            dest = resolved.target();
            excludeUuid = resolved.excludeUuid();
        } else {
            Player referenceBot = manager.getActivePlayers().stream()
                    .map(FakePlayer::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .findFirst()
                    .orElse(null);
            if (referenceBot == null) {
                sender.sendMessage(Lang.get("move-coords-invalid"));
                return true;
            }
            dest = parseCoordsTarget(sender, referenceBot, args);
            if (dest == null) return true;
        }

        int started = 0;
        int skipped = 0;
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (excludeUuid != null && fp.getUuid().equals(excludeUuid)) {
                skipped++;
                continue;
            }
            Player bot = fp.getPlayer();
            Location initial = dest.locationSupplier().get();
            if (bot == null || !bot.isOnline() || initial == null || initial.getWorld() != bot.getWorld()) {
                skipped++;
                continue;
            }
            startPathfindMove(fp, dest.locationSupplier(), dest.label(), sender);
            started++;
        }
        sender.sendMessage(Lang.get(
                "move-all-pathfind-started",
                "count",
                String.valueOf(started),
                "destination",
                dest.label(),
                "skipped",
                String.valueOf(skipped)));
        return true;
    }

    private boolean isAllTarget(String target) {
        return target.equalsIgnoreCase("--all");
    }

    /** Resolves a {@code --to} target by name: an active bot first, then a real online player. */
    @Nullable
    private PathfindTarget parseToTarget(CommandSender sender, FakePlayer fp, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.get("move-usage"));
            return null;
        }
        ResolvedTarget resolved = resolveToTarget(sender, args[2]);
        if (resolved == null) return null;
        if (resolved.excludeUuid() != null && resolved.excludeUuid().equals(fp.getUuid())) {
            sender.sendMessage(Lang.get("move-to-self"));
            return null;
        }
        return resolved.target();
    }

    /**
     * @return {@code null} if the sender was already messaged with a failure. {@code excludeUuid} is
     *     non-null only when the target is itself an active bot (used for self-target checks).
     */
    @Nullable
    private ResolvedTarget resolveToTarget(CommandSender sender, String targetName) {
        FakePlayer targetBot = manager.getByName(targetName);
        if (targetBot != null) {
            Player targetPlayer = targetBot.getPlayer();
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage(Lang.get("move-bot-not-online", "name", targetName));
                return null;
            }
            UUID targetUuid = targetBot.getUuid();
            Supplier<Location> live = () -> {
                FakePlayer live2 = manager.getByUuid(targetUuid);
                Player p = live2 != null ? live2.getPlayer() : null;
                return p != null && p.isOnline() ? p.getLocation() : null;
            };
            return new ResolvedTarget(new PathfindTarget(live, targetBot.getDisplayName()), targetUuid);
        }

        Player realPlayer = Bukkit.getPlayer(targetName);
        if (realPlayer == null || !realPlayer.isOnline()) {
            sender.sendMessage(Lang.get("move-to-target-not-found", "name", targetName));
            return null;
        }
        UUID playerUuid = realPlayer.getUniqueId();
        Supplier<Location> live = () -> {
            Player p = Bukkit.getPlayer(playerUuid);
            return p != null && p.isOnline() ? p.getLocation() : null;
        };
        return new ResolvedTarget(new PathfindTarget(live, realPlayer.getName()), null);
    }

    private record ResolvedTarget(PathfindTarget target, @Nullable UUID excludeUuid) {}

    @Nullable
    private PathfindTarget parseCoordsTarget(CommandSender sender, Player bot, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(Lang.get("move-coords-invalid"));
            return null;
        }
        double x, y, z;
        try {
            x = Double.parseDouble(args[2]);
            y = Double.parseDouble(args[3]);
            z = Double.parseDouble(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Lang.get("move-coords-invalid"));
            return null;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            sender.sendMessage(Lang.get("move-coords-invalid"));
            return null;
        }

        World world = bot.getWorld();
        if (args.length >= 6) {
            world = Bukkit.getWorld(args[5]);
            if (world == null) {
                sender.sendMessage(Lang.get("move-world-not-found", "world", args[5]));
                return null;
            }
        }

        Location loc = new Location(world, x, y, z);
        String label = (int) x + ", " + (int) y + ", " + (int) z;
        return new PathfindTarget(() -> loc, label);
    }

    /**
     * @param sender if non-null, notified on arrival/failure (the controller invokes these callbacks
     *     on the main/region thread already, so no extra scheduling is needed here).
     */
    private void startPathfindMove(
            FakePlayer fp, Supplier<Location> destination, String destinationLabel, CommandSender sender) {
        pathfinding.navigate(
                fp,
                new PathfindingService.NavigationRequest(
                        PathfindingService.Owner.MOVE,
                        destination,
                        Config.pathfindingArrivalDistance(),
                        Config.pathfindingFollowRecalcDistance(),
                        Integer.MAX_VALUE,
                        () -> {
                            if (sender != null) {
                                sender.sendMessage(Lang.get(
                                        "move-pathfind-arrived",
                                        "name",
                                        fp.getDisplayName(),
                                        "destination",
                                        destinationLabel));
                            }
                        },
                        null,
                        () -> {
                            if (sender != null) {
                                sender.sendMessage(Lang.get(
                                        "move-pathfind-no-path",
                                        "name",
                                        fp.getDisplayName(),
                                        "destination",
                                        destinationLabel));
                            }
                        }));
        FppApiImpl.fireTaskEvent(fp, "move", FppBotTaskEvent.Action.START);
    }

    private void stopMovement(FakePlayer fp) {
        UUID uuid = fp.getUuid();
        // Only release the nav slot if /fpp move itself currently owns it — another concurrently
        // running task (mining, using, finding, PVE) may hold it instead, and stopping *this* bot's
        // move command must never cancel someone else's navigation.
        if (pathfinding.isNavigating(uuid, PathfindingService.Owner.MOVE)) {
            pathfinding.cancel(uuid);
        }
        FppApiImpl.fireTaskEvent(fp, "move", FppBotTaskEvent.Action.STOP);
    }

    private record PathfindTarget(Supplier<Location> locationSupplier, String label) {}

    public void cancelAll() {
        pathfinding.cancelAll(PathfindingService.Owner.MOVE);
    }

    public void cleanupBot(@NotNull UUID botUuid) {
        if (pathfinding.isNavigating(botUuid, PathfindingService.Owner.MOVE)) {
            pathfinding.cancel(botUuid);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (!canUse(sender)) return out;

        if (args.length == 1) {
            String in = args[0].toLowerCase(Locale.ROOT);
            if ("--all".startsWith(in)) out.add("--all");
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(in)) out.add(fp.getName());
            }
        } else if (args.length == 2) {
            String in = args[1].toLowerCase(Locale.ROOT);
            for (String flag : List.of("--to", "--coords", "--stop")) {
                if (flag.startsWith(in)) out.add(flag);
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("--to")) {
            String in = args[2].toLowerCase(Locale.ROOT);
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(in)) out.add(fp.getName());
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(in) && !out.contains(p.getName())) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 6 && args[1].equalsIgnoreCase("--coords")) {
            String in = args[5].toLowerCase(Locale.ROOT);
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase(Locale.ROOT).startsWith(in)) out.add(w.getName());
            }
        }
        return out;
    }
}
