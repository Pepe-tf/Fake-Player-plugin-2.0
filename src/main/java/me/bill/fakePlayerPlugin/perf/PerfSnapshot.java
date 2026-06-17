package me.bill.fakePlayerPlugin.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;

/**
 * Immutable snapshot of server performance metrics at a single point in time.
 */
public record PerfSnapshot(
        Instant timestamp,
        double tps,
        double msptMean,
        double mspt95,
        double cpuProcess,
        double cpuSystem,
        double gcAvgTime,
        long gcAvgFrequency,
        long usedMemoryMb,
        long committedMemoryMb,
        int onlinePlayers,
        int botCount,
        int entityCount) {

    private static final AtomicLong LAST_GC_COUNT = new AtomicLong(-1);
    private static final AtomicLong LAST_GC_TIME = new AtomicLong(-1);

    /**
     * Builds a snapshot from raw values. Gc fields are left to the provider.
     */
    public static PerfSnapshot create(
            double tps,
            double msptMean,
            double mspt95,
            double cpuProcess,
            double cpuSystem,
            double gcAvgTime,
            long gcAvgFrequency,
            int botCount) {
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
                msptMean,
                mspt95,
                cpuProcess,
                cpuSystem,
                gcAvgTime,
                gcAvgFrequency,
                heap.getUsed() / (1024L * 1024L),
                heap.getCommitted() / (1024L * 1024L),
                online,
                botCount,
                entities);
    }

    /**
     * Legacy helper to compute GC avg time/frequency from management beans.
     */
    public static GcDelta computeGcDelta() {
        long totalCount = 0;
        long totalTime = 0;
        for (java.lang.management.GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c >= 0) totalCount += c;
            if (t >= 0) totalTime += t;
        }

        long prevCount = LAST_GC_COUNT.getAndSet(totalCount);
        long prevTime = LAST_GC_TIME.getAndSet(totalTime);
        if (prevCount < 0 || totalCount < prevCount) {
            return new GcDelta(0, 0);
        }
        long countDelta = totalCount - prevCount;
        long timeDelta = totalTime - prevTime;
        return new GcDelta(countDelta, timeDelta);
    }

    public record GcDelta(long countDelta, long timeDelta) {}

    /**
     * Score helper: returns 0-100 health score based on this snapshot.
     */
    public int healthScore() {
        double score = 100.0;

        // TPS: linear penalty below 20 down to 50 at 0
        score -= Math.max(0, (20.0 - tps) * 2.5);

        // MSPT: penalty above 50ms, up to 30 at 200ms
        if (msptMean > 50.0) {
            score -= Math.min(30.0, (msptMean - 50.0) * 0.2);
        }

        // CPU process: penalty above 70%, up to 20 at 100%
        if (cpuProcess > 70.0) {
            score -= Math.min(20.0, (cpuProcess - 70.0) * 0.66);
        }

        // GC: penalty based on avg time
        if (gcAvgTime > 50.0) {
            score -= Math.min(15.0, (gcAvgTime - 50.0) * 0.3);
        }

        // Entity pressure: small penalty
        if (entityCount > 2000) {
            score -= Math.min(10.0, (entityCount - 2000) / 500.0);
        }

        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    public String healthLabel() {
        int score = healthScore();
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Good";
        if (score >= 50) return "Fair";
        if (score >= 25) return "Poor";
        return "Critical";
    }
}
