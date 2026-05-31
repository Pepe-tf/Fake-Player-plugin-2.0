package me.bill.fakePlayerPlugin.api;

import java.io.File;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.extension.ExtensionLoader;

public interface FppExtension {
    @NotNull
    String getName();

    @NotNull
    String getVersion();

    default @NotNull String getDescription() {
        return "";
    }

    default @NotNull List<String> getAuthors() {
        return List.of();
    }

    default @NotNull List<String> getDependencies() {
        return List.of();
    }

    default @NotNull List<String> getSoftDependencies() {
        return List.of();
    }

    default int getPriority() {
        return 100;
    }

    void onEnable(@NotNull FppApi api);

    void onDisable();

    default @Nullable File getDataFolder() {
        return ExtensionLoader.getDataFolder(this);
    }

    default void saveDefaultConfig() {
        ExtensionLoader.saveDefaultConfig(this);
    }

    default void saveDefaultResources() {
        ExtensionLoader.extractResources(this);
    }

    default @Nullable File saveResource(@NotNull String jarPath) {
        return ExtensionLoader.saveResource(this, jarPath);
    }

    default @NotNull YamlConfiguration getConfig() {
        return ExtensionLoader.getConfig(this);
    }

    default void reloadConfig() {
        ExtensionLoader.reloadConfig(this);
    }
}
