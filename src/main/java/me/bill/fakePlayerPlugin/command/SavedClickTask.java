package me.bill.fakePlayerPlugin.command;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Snapshot of an active left/right-click task for persistence across restarts: the click mode
 * (ONCE/REPEAT/HOLD), the world the bot was working in, and the exact aim point (null for
 * self-view clicks with no locked target).
 */
public record SavedClickTask(String mode, String world, @Nullable Vector aimPoint) {}
