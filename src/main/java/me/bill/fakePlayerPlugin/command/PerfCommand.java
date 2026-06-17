package me.bill.fakePlayerPlugin.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.perf.BuiltinFppProfiler;
import me.bill.fakePlayerPlugin.perf.PerfSnapshot;
import me.bill.fakePlayerPlugin.perf.PerformanceMonitor;
import me.bill.fakePlayerPlugin.perf.PerformanceReportExporter;
import me.bill.fakePlayerPlugin.perf.ReportSnapshot;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class PerfCommand implements FppCommand {

    private static final int BENCHMARK_MINUTES = 10;
    private static final long BENCHMARK_SECONDS = BENCHMARK_MINUTES * 60L;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    private final AtomicBoolean benchmarkActive = new AtomicBoolean(false);
    private final AtomicInteger benchmarkTaskId = new AtomicInteger(-1);
    private final AtomicInteger benchmarkReminderTaskId = new AtomicInteger(-1);

    public PerfCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "perf";
    }

    @Override
    public String getUsage() {
        return "[check|top|report|report stop|history [1|5|15]|spark]";
    }

    @Override
    public String getDescription() {
        return "Show server performance diagnostics and trigger benchmark or Spark profiler.";
    }

    @Override
    public String getPermission() {
        return Perm.PERF;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.PERF);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) {
            sender.sendMessage(Lang.get("perf-disabled"));
            return true;
        }

        String sub = args.length == 0 ? "top" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "check", "top" -> sendTop(sender, monitor);
            case "report" -> {
                if (args.length >= 2 && "stop".equalsIgnoreCase(args[1])) {
                    stopBenchmark(sender);
                } else {
                    startBenchmark(sender);
                }
            }
            case "history" -> {
                int minutes = 5;
                if (args.length >= 2) {
                    try {
                        int parsed = Integer.parseInt(args[1]);
                        if (parsed == 1 || parsed == 5 || parsed == 15) minutes = parsed;
                    } catch (NumberFormatException ignored) {
                    }
                }
                sendHistory(sender, monitor, minutes);
            }
            case "spark" -> monitor.runSparkProfiler(
                    msg -> sender.sendMessage(MiniMessage.miniMessage().deserialize(msg)));
            default -> sender.sendMessage(Lang.get("perf-usage"));
        }
        return true;
    }

    private void startBenchmark(CommandSender sender) {
        if (!benchmarkActive.compareAndSet(false, true)) {
            sender.sendMessage(Lang.get("perf-benchmark-already"));
            return;
        }

        if (!(plugin.getBuiltinProfiler() instanceof BuiltinFppProfiler builtin)) {
            benchmarkActive.set(false);
            sender.sendMessage(Lang.get("perf-benchmark-no-profiler"));
            return;
        }

        builtin.startBenchmark();

        Component startedMsg = Lang.get("perf-benchmark-started", "minutes", String.valueOf(BENCHMARK_MINUTES));
        sender.sendMessage(startedMsg);
        broadcastToStaff(startedMsg, sender);
        Bukkit.getConsoleSender().sendMessage(startedMsg);
        Bukkit.getConsoleSender()
                .sendMessage("[FPP-Perf] Benchmark started. Spawn bots, run commands, etc. Report will auto-export in "
                        + BENCHMARK_MINUTES + " minutes. Use /fpp perf report stop to end early.");

        // Reminder every 2 minutes.
        int reminderTask = FppScheduler.runSyncRepeatingWithId(
                plugin,
                () -> {
                    long elapsed = builtin.benchmarkElapsedSeconds();
                    long remaining = Math.max(0, BENCHMARK_SECONDS - elapsed);
                    Component reminder =
                            Lang.get("perf-benchmark-reminder", "remaining", String.valueOf(remaining / 60));
                    sender.sendMessage(reminder);
                    broadcastToStaff(reminder, sender);
                    Bukkit.getConsoleSender().sendMessage(reminder);
                },
                2L * 60L * 20L,
                2L * 60L * 20L);
        benchmarkReminderTaskId.set(reminderTask);

        int endTask = FppScheduler.runSyncLaterWithId(
                plugin, () -> finishBenchmark(sender, builtin), BENCHMARK_SECONDS * 20L);
        benchmarkTaskId.set(endTask);
    }

    private void stopBenchmark(CommandSender sender) {
        if (!benchmarkActive.get()) {
            sender.sendMessage(Lang.get("perf-benchmark-none"));
            return;
        }
        if (!(plugin.getBuiltinProfiler() instanceof BuiltinFppProfiler builtin)) {
            sender.sendMessage(Lang.get("perf-benchmark-no-profiler"));
            return;
        }
        finishBenchmark(sender, builtin);
    }

    private void finishBenchmark(CommandSender sender, BuiltinFppProfiler builtin) {
        if (!benchmarkActive.compareAndSet(true, false)) {
            return;
        }
        cancelBenchmarkTasks();
        builtin.stopBenchmark();

        long elapsedSeconds = builtin.benchmarkElapsedSeconds();
        int minutes = Math.max(1, (int) ((elapsedSeconds + 59) / 60));

        List<ReportSnapshot.SectionEntry> tree = builtin.snapshotTree(minutes);
        PerformanceReportExporter exporter =
                new PerformanceReportExporter(plugin, plugin.getPerformanceMonitor(), builtin);
        File file = exporter.exportBenchmark(minutes, "Manual benchmark", tree);

        Component done = Lang.get("perf-benchmark-done", "file", file.getPath());
        sender.sendMessage(done);
        broadcastToStaff(done, sender);
        Bukkit.getConsoleSender().sendMessage(done);
    }

    private void cancelBenchmarkTasks() {
        int task = benchmarkTaskId.getAndSet(-1);
        if (task >= 0) {
            FppScheduler.cancel(plugin, task);
        }
        int reminder = benchmarkReminderTaskId.getAndSet(-1);
        if (reminder >= 0) {
            FppScheduler.cancel(plugin, reminder);
        }
    }

    private void broadcastToStaff(Component message, CommandSender exclude) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(Perm.PERF) && p != exclude) {
                p.sendMessage(message);
            }
        }
    }

    private void sendTop(CommandSender sender, PerformanceMonitor monitor) {
        PerfSnapshot snap = monitor.latest();
        sender.sendMessage(MiniMessage.miniMessage().deserialize(Lang.raw("perf-top-header")));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(Lang.raw("perf-top-tps", "tps", fmt(snap.tps()))));
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(Lang.raw("perf-top-mspt", "mspt", fmt(snap.msptMean()), "mspt95", fmt(snap.mspt95()))));
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(Lang.raw(
                        "perf-top-cpu", "cpu_process", fmt(snap.cpuProcess()), "cpu_system", fmt(snap.cpuSystem()))));
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(Lang.raw(
                        "perf-top-gc",
                        "gc_time",
                        fmt(snap.gcAvgTime()),
                        "gc_freq",
                        String.valueOf(snap.gcAvgFrequency()))));
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(Lang.raw(
                        "perf-top-memory",
                        "memory_used",
                        String.valueOf(snap.usedMemoryMb()),
                        "memory_committed",
                        String.valueOf(snap.committedMemoryMb()))));
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(Lang.raw(
                        "perf-top-world",
                        "players",
                        String.valueOf(snap.onlinePlayers()),
                        "bots",
                        String.valueOf(snap.botCount()),
                        "entities",
                        String.valueOf(snap.entityCount()))));
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(Lang.raw(
                        "perf-top-score", "score", String.valueOf(snap.healthScore()), "health", snap.healthLabel())));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(Lang.raw("perf-top-footer")));
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(
                        Lang.raw("perf-provider", "provider", monitor.provider().name())));
    }

    private void sendHistory(CommandSender sender, PerformanceMonitor monitor, int minutes) {
        PerformanceMonitor.HistorySummary summary = monitor.summary(minutes);
        sender.sendMessage(Lang.get(
                "perf-history",
                "minutes",
                String.valueOf(minutes),
                "samples",
                String.valueOf(summary.samples()),
                "min_tps",
                fmt(summary.minTps().tps()),
                "max_tps",
                fmt(summary.maxTps().tps()),
                "avg_tps",
                fmt(summary.avg().tps()),
                "min_mspt",
                fmt(summary.minMspt().msptMean()),
                "max_mspt",
                fmt(summary.maxMspt().msptMean()),
                "avg_mspt",
                fmt(summary.avg().msptMean()),
                "avg_cpu",
                fmt(summary.avg().cpuProcess())));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (!canUse(sender)) return out;
        if (args.length == 1) {
            String in = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("check", "top", "report", "history", "spark")) {
                if (s.startsWith(in)) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("report")) {
            String in = args[1].toLowerCase(Locale.ROOT);
            if ("stop".startsWith(in)) out.add("stop");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            String in = args[1].toLowerCase(Locale.ROOT);
            for (String s : List.of("1", "5", "15")) {
                if (s.startsWith(in)) out.add(s);
            }
        }
        return out;
    }

    private static String fmt(double value) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) return "N/A";
        return String.format(Locale.US, "%.2f", value);
    }
}
