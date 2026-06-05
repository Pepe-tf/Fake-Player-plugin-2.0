package me.bill.fakePlayerPlugin.api;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface FppSettingsTab {
    @NotNull
    String getId();

    @NotNull
    String getLabel();

    @NotNull
    Material getActiveMaterial();

    @NotNull
    Material getInactiveMaterial();

    @NotNull
    Material getSeparatorGlass();

    default boolean isVisible(@NotNull Player viewer) {
        return true;
    }

    @NotNull
    List<FppSettingsItem> getItems(@NotNull Player viewer);
}
