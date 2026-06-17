package me.bill.fakePlayerPlugin.perf;

import java.util.List;
import java.util.Map;

/**
 * Public API for the FPP self-profiler. Implementations collect timing samples
 * for named sections and expose aggregated reports.
 */
public interface FppProfiler {

    /** Name of the profiler implementation, e.g. "builtin". */
    String name();

    /** True when profiling is active and samples are being collected. */
    boolean isActive();

    /**
     * Enter a named section. The returned token must be closed; using try-with-resources
     * is recommended. If profiling is disabled, the returned token is a cheap no-op.
     */
    ProfilerToken enter(String section);

    /**
     * Enter a method-level section. Only recorded when method-level profiling is enabled.
     */
    default ProfilerToken enterMethod(String section) {
        return enter(section);
    }

    /**
     * Record an event count. Events are cheap counters used for event correlation.
     */
    void incrementEvent(String eventName, long delta);

    /** Convenience: increment by one. */
    default void incrementEvent(String eventName) {
        incrementEvent(eventName, 1L);
    }

    /** Record raw nanoseconds against a section from external timing. */
    void record(String section, long elapsedNanos, long threadUserNanos, boolean methodLevel);

    /** Produce a report snapshot from the last N minutes of collected data. */
    ReportSnapshot snapshot(int minutes, boolean includeMethods);

    /** Rolling window minute options supported by this profiler. */
    List<Integer> supportedWindows();

    /** Runtime event counters accumulated since startup. */
    Map<String, Long> eventCounters();
}
