package me.bill.fakePlayerPlugin.compat.nlogin;

import java.lang.reflect.Method;

import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * Hooks bots into nLogin (nickuc.com's closed-source auth plugin) through its genuine public API
 * ({@code com.nickuc.login.api.nLoginAPI}), if it's installed - a fully optional soft-dependency,
 * completely inert if nLogin isn't present. Resolved entirely through reflection (see
 * {@link #resolveMethods()}) rather than a compile-time dependency: nLogin is a paid plugin with no
 * public Maven artifact, so its API package is never actually on this project's build classpath -
 * a hard {@code import} would make the whole plugin fail to compile for anyone without a private
 * copy of {@code nLogin.jar}.
 *
 * <p><b>Why this exists at all, when every other tested login plugin (OpeNLogin, AuthMe-shaped
 * ones) works through {@code BotAuthManager}'s normal command-simulation path instead:</b> nLogin's
 * own live "is this connection authenticated" state lives on a per-connection session object it
 * only ever creates while its bundled PacketEvents processes a client's <i>real</i> login/
 * configuration packets. A bot never goes through that - {@code NmsPlayerSpawner} places an
 * already-fully-constructed entity directly into the world, not a simulated network handshake -
 * so simulating a chat command (indistinguishable from a real player's, for every other plugin
 * tested) still hits {@code IllegalStateException: Player session not set} every single time,
 * confirmed live against a real nLogin install. Even nLogin's own admin commands ({@code /nlogin
 * register}, {@code /nlogin forcelogin}) hit the same wall - {@code register} alone can create the
 * database row without a session, but doesn't flip the live connection to authenticated, and
 * {@code forcelogin} throws the identical exception.
 *
 * <p>{@code nLoginAPI#forceLogin} is different: it's nLogin's own sanctioned, documented way to
 * mark a player authenticated programmatically - built for exactly this shape of integration
 * (proxy session sync, premium auto-login, etc.), not layered on top of the same session
 * requirement everything else here runs into. Confirmed live: register via
 * {@code nLoginAPI#performRegister} then {@code nLoginAPI#forceLogin} actually lifts a bot's damage
 * cancellation, unlike the admin commands.
 */
public final class NLoginIntegration {

    private static final String API_CLASS = "com.nickuc.login.api.nLoginAPI";

    /** Non-null once the API class/methods were successfully resolved; reflection lookups are cheap to cache forever. */
    private static volatile Handles handles;

    private final Object api;
    private final Handles handlesForApi;

    private NLoginIntegration(Object api, Handles handlesForApi) {
        this.api = api;
        this.handlesForApi = handlesForApi;
    }

    private record Handles(
            Method getApi,
            Method isAvailable,
            Method isRegistered,
            Method isAuthenticated,
            Method performRegister,
            Method forceLogin) {}

    /**
     * Resolves the API's method handles via reflection the first time nLogin's class is actually
     * loadable, then caches them forever. Deliberately re-attempted on every call until it first
     * succeeds - not memoized as a permanent failure - so nLogin enabling after this plugin (load
     * order) or being installed during a {@code /reload} still gets picked up without a restart.
     */
    private static Handles resolveMethods() {
        Handles cached = handles;
        if (cached != null) return cached;
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Handles resolved = new Handles(
                    apiClass.getMethod("getApi"),
                    apiClass.getMethod("isAvailable"),
                    apiClass.getMethod("isRegistered", String.class),
                    apiClass.getMethod("isAuthenticated", String.class),
                    apiClass.getMethod("performRegister", String.class, String.class),
                    apiClass.getMethod("forceLogin", String.class));
            handles = resolved;
            return resolved;
        } catch (Throwable t) {
            // nLogin isn't installed, or its API shape differs from what we expect - stays fully inert.
            return null;
        }
    }

    /**
     * Looks up nLogin's API via its own static holder. Returns {@code null} (and does nothing
     * else) if nLogin isn't installed or its API isn't ready yet - safe to call unconditionally,
     * matching this codebase's other soft-dependency integrations. The availability check itself is
     * never cached - only the reflective method handles are - so calling this fresh every time
     * sidesteps any plugin-load-order timing issue entirely (nLogin enabling after this plugin, a
     * {@code /reload}, etc.) rather than needing a deferred-check dance.
     *
     * <p>Wrapped in {@code catch (Throwable}, not {@code catch (Exception}: reflective invocation
     * wraps target-side throwables in {@link java.lang.reflect.InvocationTargetException}, and a
     * missing/incompatible nLogin build could still throw something unchecked underneath that.
     */
    public static NLoginIntegration tryInstall() {
        Handles h = resolveMethods();
        if (h == null) return null;
        try {
            Object api = h.getApi().invoke(null);
            if (api == null || !(Boolean) h.isAvailable().invoke(api)) return null;
            return new NLoginIntegration(api, h);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Never throws. */
    public boolean isRegistered(String name) {
        try {
            return (Boolean) handlesForApi.isRegistered().invoke(api, name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Never throws. */
    public boolean isAuthenticated(String name) {
        try {
            return (Boolean) handlesForApi.isAuthenticated().invoke(api, name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Creates the account via nLogin's own API - does NOT by itself flip the live session to authenticated, see class doc; always follow with {@link #forceLogin}. Never throws. */
    public boolean performRegister(String name, String password) {
        try {
            return (Boolean) handlesForApi.performRegister().invoke(api, name, password);
        } catch (Throwable t) {
            FppLogger.warn("nLogin API: performRegister threw for '" + name + "': " + t.getMessage());
            return false;
        }
    }

    /** nLogin's own sanctioned way to mark a connection authenticated without a real login handshake - see class doc. Never throws. */
    public boolean forceLogin(String name) {
        try {
            return (Boolean) handlesForApi.forceLogin().invoke(api, name);
        } catch (Throwable t) {
            FppLogger.warn("nLogin API: forceLogin threw for '" + name + "': " + t.getMessage());
            return false;
        }
    }
}
