package me.bill.fakePlayerPlugin.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class BotDataYaml {

    public static final String FILE_NAME = "bots.yml";

    private BotDataYaml() {}

    public static File getFile(JavaPlugin plugin) {
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return new File(dataDir, FILE_NAME);
    }

    public static synchronized YamlConfiguration load(JavaPlugin plugin) {
        File file = getFile(plugin);
        if (!file.exists()) return new YamlConfiguration();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            quarantineCorruptFile(file, e);
            return new YamlConfiguration();
        }
    }

    public static synchronized void save(JavaPlugin plugin, YamlConfiguration yaml) throws IOException {
        File target = getFile(plugin);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        yaml.save(tmp);
        try {
            Files.move(
                    tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static synchronized void replaceSection(JavaPlugin plugin, String path, SectionWriter writer)
            throws IOException {
        YamlConfiguration yaml = load(plugin);
        yaml.set(path, null);
        ConfigurationSection section = yaml.createSection(path);
        writer.write(section);
        save(plugin, yaml);
    }

    @FunctionalInterface
    public interface SectionWriter {
        void write(ConfigurationSection section);
    }

    private static void quarantineCorruptFile(File file, Exception error) {
        File backup =
                new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis() + ".bak");
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            FppLogger.warn("Could not load "
                    + FILE_NAME
                    + " ("
                    + error.getMessage()
                    + "). Moved corrupt file to "
                    + backup.getName()
                    + ".");
        } catch (IOException moveError) {
            FppLogger.warn("Could not load "
                    + FILE_NAME
                    + " ("
                    + error.getMessage()
                    + ") and failed to quarantine it: "
                    + moveError.getMessage());
        }
    }
}
