package me.bill.fakePlayerPlugin.compat.nlogin;

import com.nickuc.login.api.nLoginAPI;

import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * Hooks bots into nLogin (nickuc.com's closed-source auth plugin) through its genuine public API
 * ({@code com.nickuc.login.api.nLoginAPI}), if it's installed - a fully optional soft-dependency
 * (see the {@code compileOnly} dependency in build.gradle.kts, pointed at a local, un-redistributable
 * {@code libs/nLogin.jar} since nLogin is a paid plugin) that's completely inert if nLogin isn't
 * present.
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
 * <p>{@link nLoginAPI#forceLogin} is different: it's nLogin's own sanctioned, documented way to
 * mark a player authenticated programmatically - built for exactly this shape of integration
 * (proxy session sync, premium auto-login, etc.), not layered on top of the same session
 * requirement everything else here runs into. Confirmed live: register via {@link
 * nLoginAPI#performRegister} then {@link nLoginAPI#forceLogin} actually lifts a bot's damage
 * cancellation, unlike the admin commands.
 */
public final class NLoginIntegration {

    private final nLoginAPI api;

    private NLoginIntegration(nLoginAPI api) {
        this.api = api;
    }

    /**
     * Looks up nLogin's API via its own static holder. Returns {@code null} (and does nothing
     * else) if nLogin isn't installed or its API isn't ready yet - safe to call unconditionally,
     * matching this codebase's other soft-dependency integrations. Deliberately not cached by
     * the caller - {@code getApi()} is a cheap static lookup, and calling this fresh every time
     * sidesteps any plugin-load-order timing issue entirely (nLogin enabling after this plugin, a
     * {@code /reload}, etc.) rather than needing a deferred-check dance.
     *
     * <p>Wrapped in {@code catch (Throwable}, not {@code catch (Exception}: if nLogin genuinely
     * isn't installed, {@code nLoginAPI} itself isn't loadable, and first touching the class throws
     * a {@code NoClassDefFoundError}.
     */
    public static NLoginIntegration tryInstall() {
        try {
            nLoginAPI api = nLoginAPI.getApi();
            if (api == null || !api.isAvailable()) return null;
            return new NLoginIntegration(api);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Never throws. */
    public boolean isRegistered(String name) {
        try {
            return api.isRegistered(name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Never throws. */
    public boolean isAuthenticated(String name) {
        try {
            return api.isAuthenticated(name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Creates the account via nLogin's own API - does NOT by itself flip the live session to authenticated, see class doc; always follow with {@link #forceLogin}. Never throws. */
    public boolean performRegister(String name, String password) {
        try {
            return api.performRegister(name, password);
        } catch (Throwable t) {
            FppLogger.warn("nLogin API: performRegister threw for '" + name + "': " + t.getMessage());
            return false;
        }
    }

    /** nLogin's own sanctioned way to mark a connection authenticated without a real login handshake - see class doc. Never throws. */
    public boolean forceLogin(String name) {
        try {
            return api.forceLogin(name);
        } catch (Throwable t) {
            FppLogger.warn("nLogin API: forceLogin threw for '" + name + "': " + t.getMessage());
            return false;
        }
    }
}
