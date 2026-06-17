package me.bill.fakePlayerPlugin.perf;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;

/**
 * PerfDataProvider implementation backed by the Spark API.
 */
public final class SparkPerfProvider implements PerfDataProvider {

    private final Object sparkLock = new Object();
    private volatile Spark spark;
    private volatile boolean triedResolve;

    @Override
    public String name() {
        return "spark";
    }

    @Override
    public boolean available() {
        resolve();
        return spark != null;
    }

    private void resolve() {
        if (triedResolve) return;
        synchronized (sparkLock) {
            if (triedResolve) return;
            triedResolve = true;
            try {
                RegisteredServiceProvider<Spark> provider =
                        Bukkit.getServicesManager().getRegistration(Spark.class);
                if (provider != null) {
                    spark = provider.getProvider();
                }
            } catch (Exception ignored) {
            }
            if (spark == null) {
                try {
                    spark = SparkProvider.get();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public PerfSnapshot snapshot(int botCount) {
        resolve();
        return PerfSnapshot.create(
                tps(), msptMean(), mspt95(), cpuProcess(), cpuSystem(), gcAvgTime(), gcAvgFrequency(), botCount);
    }

    @Override
    public double tps() {
        if (!available()) return -1;
        try {
            DoubleStatistic<StatisticWindow.TicksPerSecond> stat = spark.tps();
            if (stat == null) return -1;
            return stat.poll(StatisticWindow.TicksPerSecond.SECONDS_10);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double msptMean() {
        if (!available()) return -1;
        try {
            GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> stat = spark.mspt();
            if (stat == null) return -1;
            DoubleAverageInfo info = stat.poll(StatisticWindow.MillisPerTick.MINUTES_1);
            if (info == null) return -1;
            return info.mean();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double mspt95() {
        if (!available()) return -1;
        try {
            GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> stat = spark.mspt();
            if (stat == null) return -1;
            DoubleAverageInfo info = stat.poll(StatisticWindow.MillisPerTick.MINUTES_1);
            if (info == null) return -1;
            return info.percentile95th();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double cpuProcess() {
        if (!available()) return -1;
        try {
            DoubleStatistic<StatisticWindow.CpuUsage> stat = spark.cpuProcess();
            if (stat == null) return -1;
            return stat.poll(StatisticWindow.CpuUsage.MINUTES_1);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double cpuSystem() {
        if (!available()) return -1;
        try {
            DoubleStatistic<StatisticWindow.CpuUsage> stat = spark.cpuSystem();
            if (stat == null) return -1;
            return stat.poll(StatisticWindow.CpuUsage.MINUTES_1);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double gcAvgTime() {
        if (!available()) return fallbackGcTime();
        try {
            var collectors = spark.gc();
            if (collectors == null || collectors.isEmpty()) return fallbackGcTime();
            double total = 0;
            int count = 0;
            for (var gc : collectors.values()) {
                if (gc == null) continue;
                double t = gc.avgTime();
                if (t >= 0) {
                    total += t;
                    count++;
                }
            }
            return count == 0 ? fallbackGcTime() : total / count;
        } catch (Exception e) {
            return fallbackGcTime();
        }
    }

    @Override
    public long gcAvgFrequency() {
        if (!available()) return -1;
        try {
            var collectors = spark.gc();
            if (collectors == null || collectors.isEmpty()) return -1;
            long total = 0;
            int count = 0;
            for (var gc : collectors.values()) {
                if (gc == null) continue;
                long f = gc.avgFrequency();
                if (f >= 0) {
                    total += f;
                    count++;
                }
            }
            return count == 0 ? -1 : total / count;
        } catch (Exception e) {
            return -1;
        }
    }

    private double fallbackGcTime() {
        var delta = PerfSnapshot.computeGcDelta();
        if (delta.countDelta() <= 0) return 0.0;
        return (double) delta.timeDelta() / delta.countDelta();
    }
}
