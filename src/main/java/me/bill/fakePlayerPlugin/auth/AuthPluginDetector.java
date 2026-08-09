package me.bill.fakePlayerPlugin.auth;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.config.Config;

/**
 * Purely informational: names whichever plugin looks like it's providing the register/login
 * commands, so {@code /fpp auth} and the startup log can say e.g. "detected nLogin" instead of
 * the admin having to guess whether {@link BotAuthManager} is even talking to anything. Doesn't
 * change behaviour at all - {@link BotAuthManager} sends its commands the same way regardless of
 * what (if anything) this finds.
 *
 * <p>Genuinely dynamic, not a hard-coded plugin list: first asks Bukkit's own command map who owns
 * whatever command {@code auth.register-command}/{@code auth.login-command} actually configure
 * (works for <i>any</i> plugin that registers "register"/"login" - or whatever custom command name
 * an admin configured - as a formal Bukkit command). Many login plugins <i>don't</i> do that,
 * though - they hook {@code PlayerCommandPreprocessEvent} directly instead (see {@link
 * BotAuthManager}'s own class doc for why), which leaves nothing in the command map to find. For
 * those, this falls back to a name-based guess across every currently-enabled plugin - nothing to
 * maintain, so a login plugin nobody thought to hard-code still gets picked up as long as its name
 * says what it is (nLogin, AuthMe, xAuth, LoginSecurity, CrazyLogin, EasyAuth, ...).
 */
final class AuthPluginDetector {

    private static final List<String> NAME_HINTS = List.of("login", "auth");

    private AuthPluginDetector() {}

    /** Best current guess at the login plugin in play, or {@code null} if nothing matches either signal. Never throws. Cheap enough to call on demand rather than cache. */
    static String detect() {
        String byCommand = detectByCommandOwner();
        if (byCommand != null) return byCommand;
        return detectByName();
    }

    private static String detectByCommandOwner() {
        for (String cmd : List.of(firstWord(Config.authRegisterCommand()), firstWord(Config.authLoginCommand()))) {
            if (cmd.isEmpty()) continue;
            try {
                PluginCommand pc = Bukkit.getPluginCommand(cmd);
                if (pc != null && pc.getPlugin() != null) return pc.getPlugin().getName();
            } catch (Throwable ignored) {
                // Defensive, like AuthPluginDetector's other lookups - must never affect startup.
            }
        }
        return null;
    }

    private static String detectByName() {
        try {
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                if (plugin == null || !plugin.isEnabled()) continue;
                String lower = plugin.getName().toLowerCase(Locale.ROOT);
                for (String hint : NAME_HINTS) {
                    if (lower.contains(hint)) return plugin.getName();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** The command word alone, e.g. {@code "register %password% %password%"} -&gt; {@code "register"}. */
    private static String firstWord(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        int space = trimmed.indexOf(' ');
        return (space >= 0 ? trimmed.substring(0, space) : trimmed).trim();
    }
}
