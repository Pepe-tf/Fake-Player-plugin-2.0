package me.bill.fakePlayerPlugin.auth;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.compat.nlogin.NLoginIntegration;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeServerGamePacketListenerImpl;
import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * Makes bots play along with a login-wall plugin on the server (nLogin, AuthMe, LoginSecurity, or
 * anything else built around the same {@code /register <password> <password>} / {@code /login
 * <password>} command shape - see {@link AuthPluginDetector}), instead of sitting there
 * unauthenticated and unable to move/see the world the way a real player would if they never
 * typed their password.
 *
 * <p><b>The flow, called from {@code FakePlayerManager}</b> right after a bot is registered
 * into its {@code activePlayers} map - deliberately not a {@code PlayerJoinEvent} listener: the
 * join a bot's own spawn fires (via NMS placement) runs before that registration, so a listener
 * checking {@code FakePlayerManager#getByName} at that point would still see the bot as unknown.
 * Called on every genuine join - a fresh {@code /fpp spawn}, and a bot silently restored by
 * persistence after a restart:
 * <ol>
 *   <li>Mark the bot {@link FakePlayer#setAuthPending pending} - the tick loop freezes it (no
 *       movement, no head-turning) exactly like a real not-yet-authenticated player, until this
 *       resolves one way or another (see {@link #armOutcomeDetection}).
 *   <li>Look up {@code fpp_bot_auth} for this bot's name.
 *   <li>Nothing on record -&gt; this is the bot's first-ever join. Generate a new password
 *       ({@link SmartPasswordGenerator}), <b>persist it encrypted before sending anything</b> (so
 *       a crash right after the register command still leaves the password recoverable), then
 *       dispatch {@code auth.register-command} as the bot after a randomized human-like delay.
 *   <li>A row already exists -&gt; decrypt it and dispatch {@code auth.login-command} instead,
 *       the same delay. This is the "remembers the password and logs in instead of registering"
 *       behaviour.
 * </ol>
 *
 * <p>Commands are sent by replaying the exact two-step process a real client's typed chat command
 * goes through - see {@link #runAsRealCommand} - not a bare {@link
 * Bukkit#dispatchCommand(org.bukkit.command.CommandSender, String)}. That distinction matters:
 * most login plugins (nLogin included) hook {@link PlayerCommandPreprocessEvent} directly rather
 * than registering "register"/"login" as a formal Bukkit command - partly so they can also block
 * every <i>other</i> command an unauthenticated player tries to run. {@code dispatchCommand} alone
 * skips that event entirely, so a bot's login attempt would silently never reach such a plugin at
 * all: it would stay unauthenticated forever (unable to take damage, move for real, etc. - whatever
 * that plugin restricts) while this plugin believed the command had "run" successfully. Firing the
 * event first, exactly like a real client's chat packet does, means this works against any login
 * plugin regardless of which of the two integration styles it uses - and without a compile-time
 * dependency on that plugin at all.
 *
 * <p><b>Reading the plugin's own reply</b> ({@link #armOutcomeDetection}): a fake player's
 * connection silently drops every packet a real client would normally render, including any chat
 * message a plugin sends back via {@code Player#sendMessage} - so without help, this plugin would
 * have no way to know whether "wrong password" or "welcome back!" came back. {@link
 * FakeServerGamePacketListenerImpl#listen} taps that one packet type before it's dropped. Every
 * captured line is logged (this is the actual troubleshooting signal - read it when auth doesn't
 * seem to be taking effect), and a small, deliberately conservative keyword match against it
 * releases the bot's freeze early on an unambiguous success. This is inherently best-effort - the
 * exact wording differs per plugin and locale - so {@code auth.pending-timeout-ticks} is the real
 * safety net: the bot is released regardless once that elapses, so a message this plugin fails to
 * recognize (or no response at all) never leaves it frozen forever.
 *
 * <p><b>The one exception to all of the above: nLogin.</b> {@link #scheduleDispatch} checks {@link
 * NLoginIntegration#tryInstall} first and, if nLogin is installed, calls its own public API
 * ({@link NLoginIntegration#performRegister}/{@link NLoginIntegration#forceLogin}) directly instead
 * of simulating a command at all. This isn't optional polish - confirmed live, nLogin's command
 * path throws {@code IllegalStateException: Player session not set} for a bot every time, because
 * its live "authenticated" state lives on a per-connection session object only ever created while
 * its bundled PacketEvents processes a <i>real</i> client's login/configuration packets, which a
 * bot's spawn never goes through. Its own API's {@code forceLogin} is nLogin's sanctioned way
 * around exactly that gap - see {@link NLoginIntegration}'s own class doc for the full story.
 */
public final class BotAuthManager {

    // Deliberately conservative multi-word phrases, not bare words like "welcome" or "success" -
    // a login plugin's own PROMPT to an unauthenticated player ("please register", "welcome, log
    // in to continue") can easily contain those in isolation. Never exhaustive - see class doc for
    // why the hard timeout is the actual guarantee, not this list.
    private static final List<String> POSITIVE_HINTS = List.of(
            "logged in successfully",
            "successfully logged in",
            "you are now logged in",
            "successfully registered",
            "registration successful",
            "successfully register",
            "authentication successful",
            "welcome back");
    private static final List<String> NEGATIVE_HINTS = List.of(
            "wrong password",
            "incorrect password",
            "invalid password",
            "already registered",
            "not registered",
            "too short",
            "too long",
            "too weak",
            "please register",
            "please login",
            "please log in");

    private final Plugin plugin;
    private final DatabaseManager database;
    private final AuthCipher cipher;

    public BotAuthManager(Plugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.cipher = new AuthCipher(plugin);
        if (Config.authEnabled()) {
            // Deferred a tick, not checked right here in the constructor - this runs during plugin
            // startup, which can fire before every other plugin has finished its own onEnable
            // (load order between unrelated plugins isn't guaranteed), so detecting immediately
            // could easily miss a login plugin that just hasn't loaded yet. One tick later, Bukkit
            // has finished enabling everything.
            Bukkit.getScheduler().runTask(plugin, () -> {
                String detected = AuthPluginDetector.detect();
                FppLogger.success("Auth: enabled - "
                        + (detected != null
                                ? "detected " + detected + "."
                                : "no login plugin detected yet; using the configured "
                                        + "auth.register-command/auth.login-command as-is regardless."));
            });
        }
    }

    /** Whichever login plugin {@link AuthPluginDetector} currently thinks is installed, or {@code null} if none - computed fresh on every call, not cached, since a plugin can (dis/re)appear across a {@code /fpp reload}. */
    public String detectedPlugin() {
        return AuthPluginDetector.detect();
    }

    /**
     * Entry point for a genuine bot join - see class doc for the full flow. A no-op if {@code
     * auth.enabled} is off or the database isn't available (credentials can't be remembered
     * without it, and re-registering a fresh password on every single restart/reconnect would be
     * actively worse than doing nothing).
     */
    public void handleBotJoin(FakePlayer bot) {
        if (!Config.authEnabled()) return;
        bot.setAuthPending(true);
        if (database == null || !database.isConnectionValid()) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: enabled, but the database isn't available - can't remember bot "
                    + "credentials, so skipping auto-login for '" + bot.getName() + "'. Enable database.enabled "
                    + "in config.yml to use this feature.");
            return;
        }

        String name = bot.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager.BotAuthRow row = database.getBotAuth(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (row != null) {
                    loginExisting(bot, row);
                } else {
                    registerNew(bot);
                }
            });
        });
    }

    private void loginExisting(FakePlayer bot, DatabaseManager.BotAuthRow row) {
        String password;
        try {
            password = cipher.decrypt(row.passwordEncrypted());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: couldn't decrypt the stored password for '" + bot.getName() + "' ("
                    + e.getMessage() + ") - skipping login. Run /fpp auth reset " + bot.getName()
                    + " to clear it so the next join registers a fresh one.");
            return;
        }
        FppLogger.debug("AUTH", Config.debugAuth(), "'" + bot.getName() + "' has a stored password - logging in.");
        scheduleDispatch(bot, AuthAction.LOGIN, Config.authLoginCommand(), password);
    }

    private void registerNew(FakePlayer bot) {
        String password = SmartPasswordGenerator.generate();
        String encrypted;
        try {
            encrypted = cipher.encrypt(password);
        } catch (GeneralSecurityException e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: couldn't encrypt a new password for '" + bot.getName() + "' (" + e.getMessage()
                    + ") - skipping registration.");
            return;
        }
        // Written BEFORE the register command is ever dispatched, deliberately - if the server
        // crashes or this bot gets kicked between the two, the password this bot was told is still
        // on record for its next join to retry the (from its perspective, identical either way)
        // register call with, rather than being lost and forcing a manual /fpp auth setpassword.
        database.upsertBotAuth(bot.getName(), encrypted, () -> {
            FppLogger.debug(
                    "AUTH",
                    Config.debugAuth(),
                    "'" + bot.getName() + "' has no stored password - registering a new one.");
            scheduleDispatch(bot, AuthAction.REGISTER, Config.authRegisterCommand(), password);
        });
    }

    private enum AuthAction {
        REGISTER,
        LOGIN
    }

    private void scheduleDispatch(FakePlayer bot, AuthAction action, String commandTemplate, String password) {
        int minTicks = Config.authDelayMinTicks();
        int maxTicks = Config.authDelayMaxTicks();
        long delay = minTicks >= maxTicks
                ? minTicks
                : minTicks + ThreadLocalRandom.current().nextInt(maxTicks - minTicks + 1);

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            Player player = bot.getPlayer();
                            if (player == null || !player.isOnline()) {
                                bot.setAuthPending(false);
                                return;
                            }

                            // See class doc - nLogin's own API bypasses a hard blocker in the generic
                            // command-simulation path below, so it always takes priority when nLogin is installed.
                            NLoginIntegration nLogin = NLoginIntegration.tryInstall();
                            if (nLogin != null) {
                                runViaNLoginApi(bot, nLogin, action, password);
                                return;
                            }

                            String command = commandTemplate.replace("%password%", password);
                            if (command.startsWith("/")) command = command.substring(1);
                            String finalCommand = command;
                            FppLogger.debug(
                                    "AUTH",
                                    Config.debugAuth(),
                                    "'" + bot.getName() + "' running auth command: "
                                            + finalCommand.replace(password, "*".repeat(password.length())));

                            runAsRealCommand(bot, player, command);
                        },
                        delay);
    }

    // Set the first time runViaNLoginApi actually fails, so every bot after the first doesn't
    // repeat the same paragraph-long explanation - see that method's own doc.
    private volatile boolean nLoginLimitationExplained = false;

    /**
     * Authenticates {@code bot} through nLogin's own API instead of simulating a command - see
     * class doc for why the command path can't work for nLogin at all. Register additionally calls
     * {@code forceLogin} right after {@code performRegister}, since (confirmed live) registering
     * alone creates the account but doesn't by itself flip the live connection to authenticated.
     *
     * <p>On some nLogin builds/configurations this still fails outright (confirmed live: even
     * {@code forceLogin} itself can throw the identical {@code Player session not set} every other
     * entry point does - nLogin's whole authentication surface, API included, funnels through one
     * session object only its own real login-packet handling ever creates, which a bot's spawn
     * never goes through). There's no further fallback past this - the bot's freeze still lifts
     * (see {@link FakePlayer#setAuthPending}) so it isn't left standing frozen forever, but nLogin's
     * <i>own</i> restrictions (no damage, an eventual "took too long" kick) stay in effect for it
     * regardless, since those are enforced entirely on nLogin's side, not ours.
     */
    private void runViaNLoginApi(FakePlayer bot, NLoginIntegration nLogin, AuthAction action, String password) {
        String name = bot.getName();
        boolean ok = action == AuthAction.REGISTER
                ? nLogin.performRegister(name, password) && nLogin.forceLogin(name)
                : nLogin.forceLogin(name);
        if (ok) {
            FppLogger.debug(
                    "AUTH",
                    Config.debugAuth(),
                    "'" + name + "' authenticated via nLogin's API directly (" + action + ").");
        } else if (!nLoginLimitationExplained) {
            nLoginLimitationExplained = true;
            FppLogger.warn("Auth: nLogin's API rejected " + action + " for '" + name + "' - and will for "
                    + "every other bot too, most likely: on this nLogin build/config, its whole "
                    + "authentication surface (commands AND its own API) requires a per-connection "
                    + "session it only creates while handling a real client's actual login packets, which "
                    + "a bot never sends. There's no supported way around that from here - this bot (and "
                    + "future ones) will stay frozen-then-released but still restricted by nLogin itself "
                    + "(no damage, an eventual login-timeout kick). nLogin's own config has a "
                    + "'bypass authentication for these nicknames' list (Security section) if you want "
                    + "bots specifically exempted from its login wall instead.");
        } else {
            FppLogger.warn("Auth: nLogin's API rejected " + action + " for '" + name
                    + "' (same known limitation - see the earlier warning).");
        }
        bot.setAuthPending(false);
    }

    /**
     * Sends {@code command} (no leading slash) exactly the way a real client's typed "/command"
     * chat line reaches the server - see class doc for why a bare {@link Bukkit#dispatchCommand}
     * isn't enough. Step 1: build and fire a {@link PlayerCommandPreprocessEvent} with the bot as
     * the sender, same as vanilla does for a genuine chat-command packet. If any listener cancels
     * it - the expected outcome for a login plugin that hooks this event directly - treat that as
     * handled and arm outcome detection. Step 2: only if nothing cancelled it, actually dispatch
     * the (possibly listener-modified) command, same as vanilla does next.
     */
    private void runAsRealCommand(FakePlayer bot, Player player, String command) {
        PlayerCommandPreprocessEvent preEvent = new PlayerCommandPreprocessEvent(player, "/" + command);
        try {
            Bukkit.getPluginManager().callEvent(preEvent);
        } catch (Exception e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: PlayerCommandPreprocessEvent threw for '" + bot.getName() + "': " + e.getMessage());
            return;
        }
        if (preEvent.isCancelled()) {
            FppLogger.debug(
                    "AUTH",
                    Config.debugAuth(),
                    "'" + bot.getName()
                            + "' auth command was intercepted by a PlayerCommandPreprocessEvent listener "
                            + "(almost certainly the login plugin itself) - waiting for its response.");
            armOutcomeDetection(bot);
            return;
        }

        String dispatchedCommand = preEvent.getMessage();
        if (dispatchedCommand.startsWith("/")) dispatchedCommand = dispatchedCommand.substring(1);

        boolean dispatched;
        try {
            dispatched = Bukkit.dispatchCommand(player, dispatchedCommand);
        } catch (Exception e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: command dispatch threw for '" + bot.getName() + "': " + e.getMessage());
            return;
        }
        if (!dispatched) {
            bot.setAuthPending(false);
            String firstWord =
                    dispatchedCommand.isBlank() ? "" : dispatchedCommand.trim().split("\\s+", 2)[0];
            FppLogger.warn("Auth: no command handler responded to '/" + firstWord + "' for '" + bot.getName()
                    + "' - check auth.register-command/auth.login-command in config.yml, and that a login "
                    + "plugin is actually installed and enabled.");
            return;
        }
        armOutcomeDetection(bot);
    }

    /**
     * Watches for the login plugin's own reply (see class doc) so the bot's freeze can lift the
     * moment a response looks like a clear success, and always lifts it - and stops
     * listening - once {@code auth.pending-timeout-ticks} passes regardless, so a response this
     * plugin can't read never leaves a bot stuck.
     */
    private void armOutcomeDetection(FakePlayer bot) {
        UUID uuid = bot.getUuid();
        FakeServerGamePacketListenerImpl.listen(
                uuid, text -> Bukkit.getScheduler().runTask(plugin, () -> {
                    FppLogger.info("Auth: '" + bot.getName() + "' received: \"" + text + "\"");
                    String lower = text.toLowerCase(Locale.ROOT);
                    if (containsAny(lower, POSITIVE_HINTS)) {
                        FakeServerGamePacketListenerImpl.stopListening(uuid);
                        bot.setAuthPending(false);
                    } else if (containsAny(lower, NEGATIVE_HINTS)) {
                        FppLogger.warn("Auth: '" + bot.getName() + "' looks like it FAILED to authenticate (see the "
                                + "message above). If a stored password is stale, run /fpp auth reset " + bot.getName()
                                + " or /fpp auth setpassword " + bot.getName() + " <password>.");
                    }
                }));

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            FakeServerGamePacketListenerImpl.stopListening(uuid);
                            bot.setAuthPending(false);
                        },
                        Config.authPendingTimeoutTicks());
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }

    // ── Admin operations (/fpp auth ...) ────────────────────────────────────────────────────────

    /** Delivers the result on the main thread via {@code callback} - the underlying DB read is blocking, see {@link DatabaseManager#getBotAuth}. */
    public void lookup(String botName, Consumer<DatabaseManager.BotAuthRow> callback) {
        if (database == null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager.BotAuthRow row = database.getBotAuth(botName);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(row));
        });
    }

    /** Forgets whatever password this plugin has on record for {@code botName} - does NOT unregister it on the auth plugin's own side, see class doc. */
    public void reset(String botName) {
        if (database != null) database.deleteBotAuth(botName);
    }

    /** Manually tells this plugin what {@code botName}'s real current password is (e.g. after an admin reset it on the auth plugin's side directly), so the next join logs in with it instead of registering. */
    public boolean setPassword(String botName, String rawPassword) {
        if (database == null) return false;
        try {
            database.upsertBotAuth(botName, cipher.encrypt(rawPassword), null);
            return true;
        } catch (GeneralSecurityException e) {
            FppLogger.warn("Auth: couldn't encrypt the password supplied for '" + botName + "': " + e.getMessage());
            return false;
        }
    }
}
