package me.bill.fakePlayerPlugin.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;

import org.bukkit.Bukkit;

/**
 * Fallback PerfDataProvider that uses only Bukkit/JMX APIs when Spark is unavailable.
 */
public final class BuiltinPerfProvider implements PerfDataProvider {

    @Override
    public String name() {
        return "builtin";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public PerfSnapshot snapshot(int botCount) {
        double tps = tps();
        double mspt = msptMean();
        double cpuProcess = cpuProcess();
        double cpuSystem = cpuSystem();
        double gcTime = gcAvgTime();
        long gcFreq = gcAvgFrequency();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        int online = Bukkit.getOnlinePlayers().size();
        int entities = 0;
        try {
            entities = Bukkit.getWorlds().stream()
                    .mapToInt(w -> w.getEntities().size())
                    .sum();
        } catch (Exception ignored) {
        }
        return new PerfSnapshot(
                Instant.now(),
                tps,
                mspt,
                -1,
                cpuProcess,
                cpuSystem,
                gcTime,
                gcFreq,
                heap.getUsed() / (1024L * 1024L),
                heap.getCommitted() / (1024L * 1024L),
                online,
                botCount,
                entities);
    }

    @Override
    public double tps() {
        try {
            double[] tps = Bukkit.getServer().getTPS();
            return tps.length > 0 ? tps[0] : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double msptMean() {
        return -1;
    }

    @Override
    public double mspt95() {
        return -1;
    }

    @Override
    public double cpuProcess() {
        return -1;
    }

    @Override
    public double cpuSystem() {
        return -1;
    }

    @Override
    public double gcAvgTime() {
        var delta = PerfSnapshot.computeGcDelta();
        if (delta.countDelta() <= 0) return 0.0;
        return (double) delta.timeDelta() / delta.countDelta();
    }

    @Override
    public long gcAvgFrequency() {
        return -1;
    }
}
