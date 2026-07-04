package me.bill.fakePlayerPlugin.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;

/**
 * Suppresses the vanilla server console line
 * {@code <name>[<ip>] logged in with entity id <id> at (<pos>)} for FPP bots, so spawning bots does
 * not spam the log. Real players' login lines are left untouched.
 */
public final class BotLoginLogFilter extends AbstractFilter {

    private static final String MARKER = "logged in with entity id";
    private static volatile BotLoginLogFilter installed;

    private final FakePlayerPlugin plugin;
    private volatile boolean active = true;

    private BotLoginLogFilter(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public static void install(FakePlayerPlugin plugin) {
        if (installed != null) return;
        try {
            BotLoginLogFilter filter = new BotLoginLogFilter(plugin);
            ((Logger) LogManager.getRootLogger()).addFilter(filter);
            installed = filter;
        } catch (Throwable t) {
            FppLogger.debug("BotLoginLogFilter install failed: " + t.getMessage());
        }
    }

    public static void uninstall() {
        // The core Logger has no public removeFilter; deactivate instead so it becomes a no-op.
        BotLoginLogFilter f = installed;
        installed = null;
        if (f != null) f.active = false;
    }

    private boolean nameIsBot(String name) {
        if (!active || name == null || name.isBlank()) return false;
        try {
            var manager = plugin.getFakePlayerManager();
            return manager != null && manager.getByName(name.trim()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // Full, already-formatted message: "<name>[<ip>] logged in with entity id ...".
    private Result checkFormatted(String formatted) {
        if (formatted == null || !formatted.contains(MARKER)) return Result.NEUTRAL;
        int bracket = formatted.indexOf('[');
        String name = bracket > 0 ? formatted.substring(0, bracket) : null;
        return nameIsBot(name) ? Result.DENY : Result.NEUTRAL;
    }

    // Pattern form "{}[{}] logged in with entity id {} at ({})" with the name as the first param.
    private Result checkPattern(String pattern, Object firstParam) {
        if (pattern == null || !pattern.contains(MARKER)) return Result.NEUTRAL;
        return firstParam != null && nameIsBot(String.valueOf(firstParam)) ? Result.DENY : Result.NEUTRAL;
    }

    @Override
    public Result filter(LogEvent event) {
        Message m = event != null ? event.getMessage() : null;
        return checkFormatted(m != null ? m.getFormattedMessage() : null);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return checkFormatted(msg != null ? msg.getFormattedMessage() : null);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return checkFormatted(msg != null ? String.valueOf(msg) : null);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return checkPattern(msg, params != null && params.length > 0 ? params[0] : null);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg) {
        return checkFormatted(msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object p0) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object p0, Object p1) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object p0, Object p1, Object p2) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(
            Logger logger, Level level, Marker marker, String msg, Object p0, Object p1, Object p2, Object p3) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String msg,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String msg,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String msg,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String msg,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String msg,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7,
            Object p8) {
        return checkPattern(msg, p0);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String msg,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7,
            Object p8,
            Object p9) {
        return checkPattern(msg, p0);
    }
}
