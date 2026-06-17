package me.bill.fakePlayerPlugin.perf;

import java.util.Objects;

/**
 * Lightweight token returned by the profiler when entering a section. Closing
 * the token records the elapsed time. Tokens are cheap to create; if profiling
 * is disabled, the close call is a near-no-op.
 */
public interface ProfilerToken extends AutoCloseable {

    /** A cheap no-op token returned when profiling is disabled. */
    ProfilerToken NO_OP = new ProfilerToken() {
        @Override
        public void close() {}
    };

    @Override
    void close();
}

final class ActiveProfilerToken implements ProfilerToken {
    private final BuiltinFppProfiler profiler;
    private final ActiveProfilerToken parent;
    private final String section;
    private final boolean methodLevel;
    private final long startNanos;
    private final long startThreadUserTime;
    private volatile boolean closed;

    ActiveProfilerToken(BuiltinFppProfiler profiler, String section, boolean methodLevel) {
        this.profiler = profiler;
        this.section = Objects.requireNonNull(section);
        this.methodLevel = methodLevel;
        this.startNanos = System.nanoTime();
        this.startThreadUserTime = threadUserTime();
        this.parent = profiler.peekActiveToken();
        profiler.pushActiveToken(this);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (profiler == null || !profiler.isActive()) return;
        long elapsedNanos = System.nanoTime() - startNanos;
        long threadUserDelta = threadUserTime() - startThreadUserTime;
        profiler.popActiveToken(this);
        String path = buildPath();
        profiler.record(path, elapsedNanos, threadUserDelta, methodLevel);
    }

    String section() {
        return section;
    }

    ActiveProfilerToken parent() {
        return parent;
    }

    private String buildPath() {
        StringBuilder sb = new StringBuilder();
        appendPath(sb);
        return sb.toString();
    }

    private void appendPath(StringBuilder sb) {
        if (parent != null) {
            parent.appendPath(sb);
            sb.append('.');
        }
        sb.append(section);
    }

    private static long threadUserTime() {
        try {
            java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
            if (bean.isCurrentThreadCpuTimeSupported()) {
                return bean.getCurrentThreadUserTime();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }
}
