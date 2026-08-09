package me.bill.fakePlayerPlugin.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import me.bill.fakePlayerPlugin.auth.BotAuthManager;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

/** Admin controls for {@link BotAuthManager}: toggle it, check its status, and manage a bot's remembered password. */
public class AuthCommand implements FppCommand {

    private final FakePlayerManager manager;
    private final BotAuthManager authManager;

    public AuthCommand(FakePlayerManager manager, BotAuthManager authManager) {
        this.manager = manager;
        this.authManager = authManager;
    }

    @Override
    public String getName() {
        return "auth";
    }

    @Override
    public String getUsage() {
        return "on|off|status [bot]|reset <bot>|setpassword <bot> <password>";
    }

    @Override
    public String getDescription() {
        return "Manage bot auto-register/login against an installed login plugin.";
    }

    @Override
    public String getPermission() {
        return Perm.AUTH;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (authManager == null) {
            sender.sendMessage(Lang.get("auth-unavailable"));
            return true;
        }
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                Config.set("auth.enabled", true);
                Config.save();
                sender.sendMessage(Lang.get("auth-enabled"));
            }
            case "off" -> {
                Config.set("auth.enabled", false);
                Config.save();
                sender.sendMessage(Lang.get("auth-disabled"));
            }
            case "status" -> {
                if (args.length >= 2) {
                    sendBotStatus(sender, args[1]);
                } else {
                    sendStatus(sender);
                }
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(Lang.get("auth-usage"));
                    return true;
                }
                // Deliberately NOT gated on manager.getByName - the stored credential is a
                // database row keyed by name, entirely independent of whether that bot happens to
                // be active right now (an admin fixing up a despawned/offline bot's stale password
                // is exactly the normal use case for this).
                String name = args[1];
                authManager.reset(name);
                sender.sendMessage(Lang.get("auth-reset", "name", name));
            }
            case "setpassword" -> {
                if (args.length < 3) {
                    sender.sendMessage(Lang.get("auth-setpassword-usage"));
                    return true;
                }
                String name = args[1];
                if (authManager.setPassword(name, args[2])) {
                    sender.sendMessage(Lang.get("auth-setpassword-done", "name", name));
                } else {
                    sender.sendMessage(Lang.get("auth-setpassword-failed", "name", name));
                }
            }
            default -> sender.sendMessage(Lang.get("auth-usage"));
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Lang.get(Config.authEnabled() ? "auth-status-on" : "auth-status-off"));
        String detected = authManager.detectedPlugin();
        sender.sendMessage(Lang.get("auth-status-detected", "plugin", detected != null ? detected : "none"));
    }

    private void sendBotStatus(CommandSender sender, String name) {
        FakePlayer fp = manager.getByName(name);
        if (fp == null) {
            sender.sendMessage(Lang.get("auth-bot-not-found", "name", name));
            return;
        }
        String resolvedName = fp.getName();
        authManager.lookup(resolvedName, row -> {
            String key = row != null ? "auth-bot-status-known" : "auth-bot-status-unknown";
            sender.sendMessage(Lang.get(key, "name", resolvedName));
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "status", "reset", "setpassword").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("status")
                        || args[0].equalsIgnoreCase("reset")
                        || args[0].equalsIgnoreCase("setpassword"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return manager.getActiveNames().stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
