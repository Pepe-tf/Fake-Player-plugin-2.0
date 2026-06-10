package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotTaskEvent;
import me.bill.fakePlayerPlugin.api.impl.FppApiImpl;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public final class MoveCommand implements FppCommand {

    private final FakePlayerManager manager;
    private final Set<UUID> movingBots = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> stopTaskIds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> movementTokens = new ConcurrentHashMap<>();
    private final AtomicLong movementSequence = new AtomicLong();

    public MoveCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "move";
    }

    @Override
    public String getUsage() {
        return "<bot|all> --direction <forward|backward|left|right> [--seconds <n>|--ticks <n>]  |  <bot|all> --stop";
    }

    @Override
    public String getDescription() {
        return "Move bot bodies in a simple direction. Pathfinding is extension-owned.";
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
        if (args.length < 2) {
            sender.sendMessage(Lang.get("move-usage"));
            return true;
        }

        String target = args[0];
        String flag = args[1].toLowerCase(Locale.ROOT);
        if (!flag.equals("--direction") && !flag.equals("--stop")) {
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
        if (sender instanceof Player player
                && !Perm.hasOrOp(sender, Perm.ADMIN)
                && !BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) {
            sender.sendMessage(Lang.get("move-bot-not-online", "name", target));
            return true;
        }

        if (flag.equals("--stop")) {
            stopDirectionalMove(fp);
            sender.sendMessage(Lang.get("move-stopped", "name", fp.getDisplayName()));
            return true;
        }

        MoveOptions options = parseMoveOptions(sender, args);
        if (options == null) return true;

        startDirectionalMove(fp, bot, options.direction(), options.durationTicks());
        sender.sendMessage(Lang.get(
                "move-direction-started", "name", fp.getDisplayName(), "direction", options.direction().label));
        return true;
    }

    private boolean executeAll(CommandSender sender, String flag, String[] args) {
        if (sender instanceof Player && !Perm.hasOrOp(sender, Perm.ADMIN)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (flag.equals("--stop")) {
            int stopped = 0;
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (movingBots.contains(fp.getUuid())) {
                    stopDirectionalMove(fp);
                    stopped++;
                }
            }
            sender.sendMessage(Lang.get("move-all-stopped", "count", String.valueOf(stopped)));
            return true;
        }

        MoveOptions options = parseMoveOptions(sender, args);
        if (options == null) return true;

        int started = 0;
        int skipped = 0;
        for (FakePlayer fp : manager.getActivePlayers()) {
            Player bot = fp.getPlayer();
            if (bot == null || !bot.isOnline()) {
                skipped++;
                continue;
            }
            startDirectionalMove(fp, bot, options.direction(), options.durationTicks());
            started++;
        }
        sender.sendMessage(Lang.get(
                "move-all-direction-started",
                "count",
                String.valueOf(started),
                "direction",
                options.direction().label,
                "skipped",
                String.valueOf(skipped)));
        return true;
    }

    private boolean isAllTarget(String target) {
        return target.equalsIgnoreCase("all") || target.equalsIgnoreCase("--all");
    }

    private MoveOptions parseMoveOptions(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Lang.get("move-usage"));
            return null;
        }

        Direction direction = Direction.parse(args[2]);
        if (direction == null) {
            sender.sendMessage(Lang.get("move-direction-invalid"));
            return null;
        }

        long durationTicks = 0L;
        for (int i = 3; i < args.length; i++) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            if (!flag.equals("--seconds") && !flag.equals("--ticks")) {
                sender.sendMessage(Lang.get("move-usage"));
                return null;
            }
            if (durationTicks > 0L || i + 1 >= args.length) {
                sender.sendMessage(Lang.get("move-duration-invalid"));
                return null;
            }

            String raw = args[++i];
            try {
                if (flag.equals("--seconds")) {
                    double seconds = Double.parseDouble(raw);
                    if (!Double.isFinite(seconds) || seconds <= 0.0D) {
                        sender.sendMessage(Lang.get("move-duration-invalid"));
                        return null;
                    }
                    durationTicks = Math.max(1L, (long) Math.ceil(seconds * 20.0D));
                } else {
                    durationTicks = Long.parseLong(raw);
                    if (durationTicks <= 0L) {
                        sender.sendMessage(Lang.get("move-duration-invalid"));
                        return null;
                    }
                }
            } catch (NumberFormatException ex) {
                sender.sendMessage(Lang.get("move-duration-invalid"));
                return null;
            }
        }
        return new MoveOptions(direction, durationTicks);
    }

    private void startDirectionalMove(FakePlayer fp, Player bot, Direction direction, long durationTicks) {
        stopMovementInput(bot);
        cancelScheduledStop(fp.getUuid());
        NmsPlayerSpawner.setMovementForward(bot, direction.forward);
        NmsPlayerSpawner.setMovementStrafe(bot, direction.strafe);
        bot.setSprinting(direction.forward > 0f);
        movingBots.add(fp.getUuid());
        long token = movementSequence.incrementAndGet();
        movementTokens.put(fp.getUuid(), token);
        if (durationTicks > 0L) {
            scheduleStop(fp.getUuid(), bot, token, durationTicks);
        }
        FppApiImpl.fireTaskEvent(fp, "move", FppBotTaskEvent.Action.START);
    }

    private void stopDirectionalMove(FakePlayer fp) {
        cancelScheduledStop(fp.getUuid());
        movementTokens.remove(fp.getUuid());
        Player bot = fp.getPlayer();
        if (bot != null && bot.isOnline()) {
            stopMovementInput(bot);
        }
        movingBots.remove(fp.getUuid());
        FppApiImpl.fireTaskEvent(fp, "move", FppBotTaskEvent.Action.STOP);
    }

    private void scheduleStop(UUID botUuid, Player bot, long token, long durationTicks) {
        FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
        if (plugin == null) return;
        int taskId = FppScheduler.runAtEntityLaterWithId(
                plugin, bot, () -> stopDirectionalMoveIfCurrent(botUuid, token), durationTicks);
        if (taskId >= 0) {
            stopTaskIds.put(botUuid, taskId);
        }
    }

    private void stopDirectionalMoveIfCurrent(UUID botUuid, long token) {
        Long current = movementTokens.get(botUuid);
        if (current == null || current != token || !movingBots.contains(botUuid)) return;
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) {
            stopDirectionalMove(fp);
        } else {
            movingBots.remove(botUuid);
            movementTokens.remove(botUuid);
            cancelScheduledStop(botUuid);
        }
    }

    private void cancelScheduledStop(UUID botUuid) {
        Integer taskId = stopTaskIds.remove(botUuid);
        if (taskId != null) {
            FppScheduler.cancelTask(taskId);
        }
    }

    private void stopMovementInput(Player bot) {
        NmsPlayerSpawner.setMovementForward(bot, 0f);
        NmsPlayerSpawner.setMovementStrafe(bot, 0f);
        NmsPlayerSpawner.setJumping(bot, false);
        bot.setSprinting(false);
    }

    public void cancelAll() {
        new ArrayList<>(movingBots).forEach(this::cleanupBot);
    }

    public void cleanupBot(@NotNull UUID botUuid) {
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) stopDirectionalMove(fp);
        else {
            movingBots.remove(botUuid);
            movementTokens.remove(botUuid);
            cancelScheduledStop(botUuid);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (!canUse(sender)) return out;

        if (args.length == 1) {
            String in = args[0].toLowerCase(Locale.ROOT);
            if ("all".startsWith(in)) out.add("all");
            if ("--all".startsWith(in)) out.add("--all");
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(in)) out.add(fp.getName());
            }
        } else if (args.length == 2) {
            String in = args[1].toLowerCase(Locale.ROOT);
            for (String flag : List.of("--direction", "--stop")) {
                if (flag.startsWith(in)) out.add(flag);
            }
        } else if (args.length == 3 && args[1].equalsIgnoreCase("--direction")) {
            String in = args[2].toLowerCase(Locale.ROOT);
            for (Direction direction : Direction.values()) {
                if (direction.label.startsWith(in)) out.add(direction.label);
            }
        } else if (args.length >= 4 && args[1].equalsIgnoreCase("--direction")) {
            String previous = args[args.length - 2].toLowerCase(Locale.ROOT);
            if (previous.equals("--seconds") || previous.equals("--ticks")) return out;
            String in = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (String flag : List.of("--seconds", "--ticks")) {
                if (!List.of(args).contains(flag) && flag.startsWith(in)) out.add(flag);
            }
        }
        return out;
    }

    private record MoveOptions(Direction direction, long durationTicks) {}

    private enum Direction {
        FORWARD("forward", 1f, 0f),
        BACKWARD("backward", -1f, 0f),
        LEFT("left", 0f, 1f),
        RIGHT("right", 0f, -1f);

        private final String label;
        private final float forward;
        private final float strafe;

        Direction(String label, float forward, float strafe) {
            this.label = label;
            this.forward = forward;
            this.strafe = strafe;
        }

        private static Direction parse(String raw) {
            for (Direction direction : values()) {
                if (direction.label.equalsIgnoreCase(raw)) return direction;
            }
            return null;
        }
    }
}
