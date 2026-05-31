package me.bill.fakePlayerPlugin.api;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FppNavigationGoal {
    @Nullable
    Location getNextWaypoint(@NotNull FppBot bot);

    boolean isComplete(@NotNull FppBot bot);

    default double getSpeedModifier() {
        return 1.0;
    }

    default double getArrivalDistance() {
        return 1.5;
    }

    default double getRecalcDistance() {
        return 3.5;
    }
}
