package me.bill.fakePlayerPlugin.api;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FppApi {
    @NotNull
    Collection<FppBot> getBots();

    @NotNull
    Collection<FppBot> getBotsControllableBy(@NotNull Player player);

    @NotNull
    Collection<FppBot> getBotsOwnedBy(@NotNull Player player);

    @NotNull
    Optional<FppBot> getBot(@NotNull String name);

    @NotNull
    Optional<FppBot> getBot(@NotNull UUID uuid);

    boolean isBot(@NotNull Player player);

    @NotNull
    Optional<FppBot> asBot(@NotNull Player player);

    boolean canControlBot(@NotNull Player player, @NotNull FppBot bot);

    int getBotCount();

    @NotNull
    Optional<FppBot> spawnBot(@NotNull Location location, @Nullable Player spawner, @Nullable String name);

    default @NotNull Optional<FppBot> spawnBot(
            @NotNull Location location, @Nullable Player spawner, @NotNull String name, @NotNull UUID uuid) {
        return Optional.empty();
    }

    boolean despawnBot(@NotNull String name);

    boolean despawnBot(@NotNull FppBot bot);

    default boolean despawnBotForLoginHandoff(@NotNull String name) {
        return despawnBot(name);
    }

    void registerCommand(@NotNull FppAddonCommand command);

    void unregisterCommand(@NotNull FppAddonCommand command);

    void registerCommandExtension(@NotNull FppCommandExtension extension);

    void unregisterCommandExtension(@NotNull FppCommandExtension extension);

    @NotNull
    List<FppCommandInfo> getRegisteredCommands();

    @NotNull
    List<FppCommandInfo> getRegisteredCommands(@NotNull CommandSender sender);

    void registerTickHandler(@NotNull FppBotTickHandler handler);

    void unregisterTickHandler(@NotNull FppBotTickHandler handler);

    void registerSettingsTab(@NotNull FppSettingsTab tab);

    void unregisterSettingsTab(@NotNull FppSettingsTab tab);

    void registerBotSettingsTab(@NotNull FppSettingsTab tab);

    void unregisterBotSettingsTab(@NotNull FppSettingsTab tab);

    void registerAddon(@NotNull FppAddon addon);

    void unregisterAddon(@NotNull FppAddon addon);

    void sayAsBot(@NotNull FppBot bot, @NotNull String message);

    void setBotPing(@NotNull FppBot bot, int pingMs);

    void resetBotPing(@NotNull FppBot bot);

    void persistBotSettings(@NotNull FppBot bot);

    void setBotExtensionData(
            @NotNull FppBot bot, @NotNull String extensionKey, @NotNull String dataKey, @Nullable String dataValue);

    void removeBotExtensionData(@NotNull FppBot bot, @NotNull String extensionKey, @NotNull String dataKey);

    @NotNull
    Map<String, String> getBotExtensionData(@NotNull FppBot bot, @NotNull String extensionKey);

    void navigateTo(@NotNull FppBot bot, @NotNull Location destination, @Nullable Runnable onArrive);

    void navigateTo(
            @NotNull FppBot bot,
            @NotNull Location destination,
            @Nullable Runnable onArrive,
            @Nullable Runnable onFail,
            @Nullable Runnable onCancel);

    void navigateTo(
            @NotNull FppBot bot,
            @NotNull Location destination,
            @Nullable Runnable onArrive,
            @Nullable Runnable onFail,
            @Nullable Runnable onCancel,
            double arrivalDistance);

    void cancelNavigation(@NotNull FppBot bot);

    boolean isNavigating(@NotNull FppBot bot);

    boolean leftClick(@NotNull FppBot bot);

    boolean leftClick(@NotNull FppBot bot, @NotNull FppClickMode mode);

    boolean rightClick(@NotNull FppBot bot);

    boolean rightClick(@NotNull FppBot bot, @NotNull FppClickMode mode);

    void setNavigationGoal(@NotNull FppBot bot, @NotNull FppNavigationGoal goal);

    void clearNavigationGoal(@NotNull FppBot bot);

    boolean runAsBot(@NotNull FppBot bot, @NotNull String command);

    boolean isBotOnline(@NotNull UUID uuid);

    @NotNull
    String getVersion();

    @NotNull
    Plugin getPlugin();

    @Nullable
    Player getOnlinePlayer(@NotNull String name);

    int getOnlineCount();

    // ── Service registry ────────────────────────────────────────────────────────
    <T> void registerService(@NotNull Class<T> serviceClass, @NotNull T instance);

    <T> void unregisterService(@NotNull Class<T> serviceClass, @NotNull T instance);

    <T> @Nullable T getService(@NotNull Class<T> serviceClass);

    boolean hasService(@NotNull Class<?> serviceClass);

    // ── Extension config & resources ────────────────────────────────────────────
    @Nullable
    File getExtensionDataFolder(@NotNull String extensionName);

    void saveDefaultExtensionConfig(@NotNull String extensionName);

    @Nullable
    YamlConfiguration getExtensionConfig(@NotNull String extensionName);
}
