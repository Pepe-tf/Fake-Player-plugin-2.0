package me.bill.fakePlayerPlugin.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.util.YamlFileSyncer;

public final class BotNameConfig {

    private static FakePlayerPlugin plugin;
    private static FileConfiguration cfg;

    private BotNameConfig() {}

    public static void init(FakePlayerPlugin instance) {
        plugin = instance;
        reload();
    }

    public static void reload() {

        YamlFileSyncer.syncMissingKeys(plugin, "bot-names.yml", "bot-names.yml");

        File file = new File(plugin.getDataFolder(), "bot-names.yml");
        if (!file.exists()) {
            plugin.saveResource("bot-names.yml", false);
        }

        FileConfiguration disk = YamlConfiguration.loadConfiguration(file);
        disk.options().copyDefaults(true);

        InputStream jarStream = plugin.getResource("bot-names.yml");
        if (jarStream != null) {
            YamlConfiguration jarDefaults =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            disk.setDefaults(jarDefaults);
        }

        cfg = disk;
        int count = cfg.getStringList("name").size();
        Config.debug("BotNameConfig loaded: " + count + " name(s) from " + file.getPath());
    }

    public static List<String> getNames() {
        if (cfg == null) return List.of();

        List<String> names = cfg.getStringList("name");
        if (names.isEmpty()) {

            names = cfg.getStringList("names");
        }
        if (names.isEmpty()) {
            return List.of();
        }
        return names;
    }
}
