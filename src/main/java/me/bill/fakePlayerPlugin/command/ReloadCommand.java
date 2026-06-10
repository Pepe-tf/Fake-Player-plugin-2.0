package me.bill.fakePlayerPlugin.command;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.ConfigValidator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.UpdateChecker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class ReloadCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#0079FF");
    private static final TextColor GRAY = NamedTextColor.GRAY;
    private static final TextColor GREEN = NamedTextColor.GREEN;
    private static final TextColor YELLOW = NamedTextColor.YELLOW;
    private static final TextColor RED = NamedTextColor.RED;

    private static final List<String> TARGETS = List.of("all", "config", "lang", "extensions");

    private final FakePlayerPlugin plugin;

    public ReloadCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getUsage() {
        return "[all|config|lang|extensions]";
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin configuration (optionally target a subsystem).";
    }

    @Override
    public String getPermission() {
        return Perm.RELOAD;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.RELOAD);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String target = args.length > 0 ? args[0].toLowerCase() : "all";

        long start = System.currentTimeMillis();
        String version = plugin.getPluginMeta().getVersion();

        String label = target.equals("all") ? "full reload" : "reload:" + target;
        sender.sendMessage(Component.text("┌ FakePlayerPlugin v" + version + " — " + label + "…")
                .color(ACCENT));

        switch (target) {
            case "config" -> reloadConfig(sender);
            case "lang" -> reloadLang(sender);
            case "extensions" -> reloadExtensions(sender);
            case "all" -> reloadAll(sender);
            default -> {
                sender.sendMessage(Component.text("│  ")
                        .color(ACCENT)
                        .append(Component.text("✗ Unknown target '").color(RED))
                        .append(Component.text(target).color(YELLOW))
                        .append(Component.text("'.  Valid: " + String.join(", ", TARGETS))
                                .color(RED)));
            }
        }

        long ms = System.currentTimeMillis() - start;
        sender.sendMessage(Component.text("└ ")
                .color(ACCENT)
                .append(Component.text("✓ Done").color(GREEN))
                .append(Component.text("  in " + ms + "ms").color(GRAY)));
        FppLogger.success("Plugin reloaded [" + label + "] by " + sender.getName() + " in " + ms + "ms.");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return TARGETS.stream().filter(t -> t.startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }

    private void reloadConfig(CommandSender sender) {
        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BadwordFilter.reload(plugin);

        if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
            sender.sendMessage(Component.text("│  ⚠ Badword filter is ON but no sources are active — enable"
                            + " 'badword-filter.use-global-list' or add words to"
                            + " config.yml / bad-words.yml!")
                    .color(YELLOW));
        }

        FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) fpm.refreshCleanNamePool();

        sendStep(sender, "Config, lang, names (" + BotNameConfig.getNames().size() + "), badword filter");

        if (Config.configSyncMode().equalsIgnoreCase("AUTO_PUSH") && plugin.getConfigSyncManager() != null) {
            var csm = plugin.getConfigSyncManager();
            FppScheduler.runAsync(plugin, () -> {
                int pushed = csm.pushAll(sender.getName());
                FppScheduler.runSync(
                        plugin,
                        () -> sendStep(sender, "AUTO_PUSH: " + pushed + " config file(s) pushed" + " to network"));
            });
        }
    }

    private void reloadLang(CommandSender sender) {
        Lang.reload();
        sendStep(sender, "Language file reloaded");
    }

    private void reloadExtensions(CommandSender sender) {
        var loader = plugin.getExtensionLoader();
        if (loader != null) {
            loader.reload();
            loader.reloadExtensionConfigs();
            sendStep(sender, "Extensions reloaded from plugins/FakePlayerPlugin/extensions/");
        } else {
            sendStep(sender, "Extension loader not available");
        }
    }

    private void reloadAll(CommandSender sender) {

        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BadwordFilter.reload(plugin);

        if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
            sender.sendMessage(Component.text("│  ⚠ Badword filter is ON but no sources are active — enable"
                            + " 'badword-filter.use-global-list' or add words to"
                            + " config.yml / bad-words.yml!")
                    .color(YELLOW));
        }

        FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) fpm.refreshCleanNamePool();

        sendStep(sender, "Config, lang, names (" + BotNameConfig.getNames().size() + "), badword filter");

        if (Config.configSyncMode().equalsIgnoreCase("AUTO_PUSH") && plugin.getConfigSyncManager() != null) {
            var csm = plugin.getConfigSyncManager();
            FppScheduler.runAsync(plugin, () -> {
                int pushed = csm.pushAll(sender.getName());
                FppScheduler.runSync(
                        plugin,
                        () -> sendStep(sender, "AUTO_PUSH: " + pushed + " config file(s) pushed" + " to network"));
            });
        }

        if (fpm != null) {
            fpm.applyBodyConfig();
            int active = fpm.getCount();
            if (active > 0)
                sendStep(
                        sender,
                        active
                                + " active bot(s) state updated"
                                + "  (damageable="
                                + Config.bodyDamageable()
                                + ", pushable="
                                + Config.bodyPushable()
                                + ")");
        }

        sendStep(sender, "LuckPerms — auto-updates via UserDataRecalculateEvent");

        boolean taskPersistActive = Config.persistOnRestart() && plugin.getDatabaseManager() != null;
        String taskPersistDetail = taskPersistActive
                ? "db + yaml  (schema v" + DatabaseManager.getCurrentSchemaVersion() + ")"
                : Config.persistOnRestart() ? "yaml only  (DB disabled)" : "disabled";
        sendStep(sender, "Task persistence — " + taskPersistDetail);

        reloadExtensions(sender);

        int issues = ConfigValidator.validate();
        if (issues > 0) {
            sender.sendMessage(Component.text("│  ⚠ " + issues + " config issue(s) detected — check console")
                    .color(YELLOW));
        } else {
            sendStep(sender, "Config validation passed  (0 issues)");
        }

        UpdateChecker.invalidateCache();
        UpdateChecker.check(plugin);
        sendStep(sender, "Update check triggered  (async)");
    }

    private void sendStep(CommandSender sender, String message) {
        sender.sendMessage(Component.text("│  ")
                .color(ACCENT)
                .append(Component.text("✓ ").color(GREEN))
                .append(Component.text(message).color(GRAY)));
    }
}
