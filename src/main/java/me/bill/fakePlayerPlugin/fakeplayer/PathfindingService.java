package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;

public final class PathfindingService {

    public interface Controller {
        boolean isNavigating(@NotNull UUID botUuid);

        boolean isNavigating(@NotNull UUID botUuid, @NotNull Owner owner);

        @Nullable
        Owner getOwner(@NotNull UUID botUuid);

        void cancel(@NotNull UUID botUuid);

        void cancelAll();

        void cancelAll(@NotNull Owner owner);

        void navigate(@NotNull FakePlayer fp, @NotNull NavigationRequest request);
    }

    public enum Owner {
        MOVE,
        MINE,
        PLACE,
        USE,
        ATTACK,
        SYSTEM
    }

    public record NavigationRequest(
            @NotNull Owner owner,
            @NotNull Supplier<@Nullable Location> destinationSupplier,
            double arrivalDistance,
            double recalcDistance,
            int maxNullPathRecalculations,
            @Nullable Runnable onArrive,
            @Nullable Runnable onCancel,
            @Nullable Runnable onPathFailure,
            @Nullable Location lockOnArrival,
            @Nullable BotPathfinder.PathOptions overrideOpts) {

        public NavigationRequest(
                @NotNull Owner owner,
                @NotNull Supplier<@Nullable Location> destinationSupplier,
                double arrivalDistance,
                double recalcDistance,
                int maxNullPathRecalculations,
                @Nullable Runnable onArrive,
                @Nullable Runnable onCancel,
                @Nullable Runnable onPathFailure,
                @Nullable Location lockOnArrival) {
            this(
                    owner,
                    destinationSupplier,
                    arrivalDistance,
                    recalcDistance,
                    maxNullPathRecalculations,
                    onArrive,
                    onCancel,
                    onPathFailure,
                    lockOnArrival,
                    null);
        }

        public NavigationRequest(
                @NotNull Owner owner,
                @NotNull Supplier<@Nullable Location> destinationSupplier,
                double arrivalDistance,
                double recalcDistance,
                int maxNullPathRecalculations,
                @Nullable Runnable onArrive,
                @Nullable Runnable onCancel,
                @Nullable Runnable onPathFailure) {
            this(
                    owner,
                    destinationSupplier,
                    arrivalDistance,
                    recalcDistance,
                    maxNullPathRecalculations,
                    onArrive,
                    onCancel,
                    onPathFailure,
                    null,
                    null);
        }

        public NavigationRequest {
            if (owner == null) throw new IllegalArgumentException("owner");
            if (destinationSupplier == null) throw new IllegalArgumentException("destinationSupplier");
            if (arrivalDistance <= 0) throw new IllegalArgumentException("arrivalDistance must be > 0");
            if (recalcDistance < 0) throw new IllegalArgumentException("recalcDistance must be >= 0");
            if (maxNullPathRecalculations <= 0) maxNullPathRecalculations = Integer.MAX_VALUE;
        }
    }

    private final FakePlayerManager manager;
    private volatile Controller controller;

    public PathfindingService(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.manager = manager;
    }

    public void setController(@Nullable Controller controller) {
        Controller previous = this.controller;
        if (previous != null && previous != controller) {
            previous.cancelAll();
        }
        this.controller = controller;
    }

    public @Nullable Controller getController() {
        return controller;
    }

    public boolean isNavigating(@NotNull UUID botUuid) {
        Controller active = controller;
        return active != null && active.isNavigating(botUuid);
    }

    public boolean isNavigating(@NotNull UUID botUuid, @NotNull Owner owner) {
        Controller active = controller;
        return active != null && active.isNavigating(botUuid, owner);
    }

    @Nullable
    public Owner getOwner(@NotNull UUID botUuid) {
        Controller active = controller;
        return active != null ? active.getOwner(botUuid) : null;
    }

    public void cancel(@NotNull UUID botUuid) {
        Controller active = controller;
        if (active != null) active.cancel(botUuid);
        else stopNavigationState(botUuid);
    }

    public void cancelAll() {
        Controller active = controller;
        if (active != null) active.cancelAll();
    }

    public void cancelAll(@NotNull Owner owner) {
        Controller active = controller;
        if (active != null) active.cancelAll(owner);
    }

    public void navigate(@NotNull FakePlayer fp, @NotNull NavigationRequest request) {
        Controller active = controller;
        if (active != null) {
            active.navigate(fp, request);
            return;
        }
        stopNavigationState(fp.getUuid());
        if (request.onPathFailure() != null) request.onPathFailure().run();
        else if (request.onCancel() != null) request.onCancel().run();
    }

    public static BotPathfinder.PathOptions resolvePathOptions(@NotNull FakePlayer fp) {
        return resolvePathOptions(fp, null);
    }

    public static BotPathfinder.PathOptions resolvePathOptions(
            @NotNull FakePlayer fp, @Nullable BotPathfinder.PathOptions overrideOpts) {
        if (overrideOpts != null) return overrideOpts;
        return new BotPathfinder.PathOptions(
                fp.isNavParkour(),
                fp.isNavBreakBlocks(),
                fp.isNavPlaceBlocks(),
                fp.isNavAvoidWater() || fp.isDefaultWaterPathAvoidanceEnabled(),
                fp.isNavAvoidLava() || !fp.isSwimAiEnabled());
    }

    public static void tickSwimAi(Player bot, boolean navJump, boolean isNavigating) {
        try {
            tickSwimAiUnsafe(bot, navJump, isNavigating);
        } catch (NullPointerException e) {
            if (isFoliaWorldDataNotReady(e)) {
                NmsPlayerSpawner.setJumping(bot, bot.isInWater() || bot.isInLava());
                return;
            }
            throw e;
        }
    }

    private static void tickSwimAiUnsafe(Player bot, boolean navJump, boolean isNavigating) {
        boolean inFluid = bot.isInWater() || bot.isInLava();
        if (inFluid) {
            NmsPlayerSpawner.setJumping(bot, true);
            if (!isNavigating) {
                bot.setSprinting(false);
            }
            return;
        }

        NmsPlayerSpawner.setJumping(bot, isNavigating && navJump);
    }

    private static boolean isFoliaWorldDataNotReady(NullPointerException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("getCurrentWorldData()");
    }

    public static double xzDist(Location a, Location b) {
        return xzDistRaw(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    public static double xzDistRaw(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void stopNavigationState(UUID botUuid) {
        manager.clearNavJump(botUuid);
        manager.unlockNavigation(botUuid);
        Player bot = Bukkit.getPlayer(botUuid);
        if (bot != null && bot.isOnline()) {
            NmsPlayerSpawner.setMovementForward(bot, 0f);
            NmsPlayerSpawner.setJumping(bot, false);
            bot.setSprinting(false);
        }
    }
}
