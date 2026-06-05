package me.bill.fakePlayerPlugin.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FppSettingsItem {
    @NotNull
    String getId();

    @NotNull
    String getLabel();

    @NotNull
    String getDescription();

    @NotNull
    Material getIcon();

    @Nullable
    String getValue();

    void onClick(@NotNull Player viewer);
}
