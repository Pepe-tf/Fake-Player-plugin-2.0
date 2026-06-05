package me.bill.fakePlayerPlugin.api;

import java.util.List;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface FppBotSettingsTab extends FppSettingsTab {
    @Override
    default @NotNull List<FppSettingsItem> getItems(@NotNull Player viewer) {
        return List.of();
    }

    @NotNull
    List<FppSettingsItem> getItems(@NotNull Player viewer, @NotNull FppBot bot);
}
