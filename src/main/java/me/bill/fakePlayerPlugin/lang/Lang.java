package me.bill.fakePlayerPlugin.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.TextUtil;
import me.bill.fakePlayerPlugin.util.YamlFileSyncer;

import net.kyori.adventure.text.Component;

public final class Lang {

    private static FakePlayerPlugin plugin;
    private static FileConfiguration cfg;

    private Lang() {}

    public static void init(FakePlayerPlugin instance) {
        plugin = instance;
        reload();
    }

    public static void reload() {

        YamlFileSyncer.syncMissingKeys(plugin, "language/en.yml", "language/en.yml");

        File file = new File(plugin.getDataFolder(), "language/en.yml");
        if (!file.exists()) {
            plugin.saveResource("language/en.yml", false);
        }

        FileConfiguration disk = YamlConfiguration.loadConfiguration(file);
        disk.options().copyDefaults(true);

        InputStream jarStream = plugin.getResource("language/en.yml");
        if (jarStream != null) {
            try {
                YamlConfiguration jarDefaults =
                        YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));
                disk.setDefaults(jarDefaults);
            } catch (Exception e) {
                FppLogger.warn("Lang: failed to load JAR defaults for language/en.yml: " + e.getMessage());
            }
        }

        cfg = disk;
        Config.debug("Lang loaded from: " + file.getPath());
    }

    public static String raw(String key, String... args) {
        if (cfg == null) return "&c[FPP] Lang not loaded: " + key;
        String value = cfg.getString(key, "&c[FPP] Missing lang key: " + key);

        String prefix = cfg.getString("prefix", "&f[FPP] ");
        value = value.replace("{prefix}", prefix);

        if (args.length > 0) {

            if (args.length % 2 == 0 && !args[0].chars().allMatch(Character::isDigit)) {
                for (int i = 0; i < args.length - 1; i += 2) {
                    value = value.replace("{" + args[i] + "}", args[i + 1]);
                }
            } else {

                for (int i = 0; i < args.length; i++) {
                    value = value.replace("{" + i + "}", args[i]);
                }
            }
        }
        return value;
    }

    public static Component get(String key, String... args) {
        return TextUtil.colorize(raw(key, args));
    }

    public static FileConfiguration config() {
        return cfg;
    }
}
