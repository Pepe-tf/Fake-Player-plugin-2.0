package me.bill.fakePlayerPlugin.perf;

/**
 * Provider interface for performance metrics. Allows future integrations with
 * Spark, Paper/Folia APIs, JMX, Prometheus exporters, etc. without changing command logic.
 */
public interface PerfDataProvider {

    /**
     * Provider name for diagnostics.
     */
    String name();

    /**
     * True if the provider is currently available and able to produce data.
     */
    boolean available();

    /**
     * Collect a current performance snapshot. Must be safe to call from the server thread.
     * Implementations should return a snapshot even if underlying APIs are unavailable.
     */
    PerfSnapshot snapshot(int botCount);

    /**
     * Get the current TPS. Return -1 if unavailable.
     */
    double tps();

    /**
     * Get the current MSPT mean. Return -1 if unavailable.
     */
    double msptMean();

    /**
     * Get the current MSPT 95th percentile. Return -1 if unavailable.
     */
    double mspt95();

    /**
     * Get current process CPU usage percentage. Return -1 if unavailable.
     */
    double cpuProcess();

    /**
     * Get current system CPU usage percentage. Return -1 if unavailable.
     */
    double cpuSystem();

    /**
     * Average GC collection time in ms. Return -1 if unavailable.
     */
    double gcAvgTime();

    /**
     * Average GC frequency in ms between collections. Return -1 if unavailable.
     */
    long gcAvgFrequency();
}
