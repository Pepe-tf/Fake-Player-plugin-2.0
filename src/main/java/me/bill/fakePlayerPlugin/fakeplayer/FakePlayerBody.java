package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import com.destroystokyo.paper.profile.ProfileProperty;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.AttributeCompat;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.TextUtil;

public final class FakePlayerBody {

    public static final String VISUAL_PDC_VALUE = "fpp-visual";

    public static final String NAMETAG_PDC_VALUE = "fpp-nametag";

    private FakePlayerBody() {}

    public static Player spawn(FakePlayer fp, Location loc) {
        return spawn(fp, loc, -1);
    }

    public static Player spawn(FakePlayer fp, Location loc, int initialPing) {
        if (loc == null || loc.getWorld() == null) {
            FppLogger.warn("FakePlayerBody.spawn: location or world is null for " + fp.getName());
            return null;
        }

        try {
            FppLogger.debug("FakePlayerBody.spawn: START for '" + fp.getName() + "' at " + loc + " (Folia="
                    + NmsPlayerSpawner.isFoliaServer() + ")");

            Player player = NmsPlayerSpawner.spawnFakePlayer(
                    fp.getUuid(),
                    fp.getName(),
                    fp.getResolvedSkin(),
                    loc.getWorld(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch(),
                    initialPing,
                    shouldLoadSavedPlayerData(fp));

            if (player == null) {
                FppLogger.warn("FakePlayerBody.spawn: NmsPlayerSpawner returned null for '" + fp.getName() + "'");
                return null;
            }

            FppLogger.debug("FakePlayerBody.spawn: NmsPlayerSpawner returned player, calling finalizeSpawnedBody...");
            finalizeSpawnedBody(fp, player);
            FppLogger.debug("FakePlayerBody.spawn: SUCCESS for '" + fp.getName() + "'");
            return player;

        } catch (Exception e) {
            FppLogger.error("FakePlayerBody.spawn failed for '" + fp.getName() + "': " + e.getMessage());
            FppLogger.debug("  Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    /**
     * Callback-based body spawn compatibility wrapper.
     */
    public static void spawnAsync(FakePlayer fp, Location loc, Consumer<Player> callback) {
        spawnAsync(fp, loc, -1, callback);
    }

    public static void spawnAsync(FakePlayer fp, Location loc, int initialPing, Consumer<Player> callback) {
        if (loc == null || loc.getWorld() == null) {
            callback.accept(null);
            return;
        }
        NmsPlayerSpawner.spawnFakePlayerAsync(
                fp.getUuid(),
                fp.getName(),
                fp.getResolvedSkin(),
                loc.getWorld(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                initialPing,
                shouldLoadSavedPlayerData(fp),
                player -> {
                    if (player == null) {
                        FppLogger.warn("FakePlayerBody.spawnAsync: NmsPlayerSpawner returned null for " + fp.getName());
                        callback.accept(null);
                        return;
                    }
                    try {
                        finalizeSpawnedBody(fp, player);
                    } catch (Exception e) {
                        FppLogger.error("FakePlayerBody.spawnAsync finalize failed for "
                                + fp.getName()
                                + ": "
                                + e.getMessage());
                    }
                    callback.accept(player);
                });
    }

    private static boolean shouldLoadSavedPlayerData(FakePlayer fp) {
        return fp != null && Boolean.TRUE.equals(fp.getMetadata("fpp.explicit-uuid-spawn"));
    }

    private static void finalizeSpawnedBody(FakePlayer fp, Player player) {
        try {
            if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
                player.getPersistentDataContainer().remove(new NamespacedKey(FakePlayerPlugin.getInstance(), "npc"));
                player.getPersistentDataContainer()
                        .set(
                                FakePlayerManager.FAKE_PLAYER_KEY,
                                PersistentDataType.STRING,
                                VISUAL_PDC_VALUE + ":" + fp.getName());
            }
        } catch (Exception e) {
            FppLogger.debug("FakePlayerBody.spawn: PDC tag failed for " + fp.getName() + ": " + e.getMessage());
        }

        player.setGravity(true);
        player.setInvulnerable(false);
        player.setNoDamageTicks(0);
        player.setCollidable(true);
        player.setCanPickupItems(fp.isPickUpItemsEnabled());

        String displayName = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            try {
                player.displayName(TextUtil.colorize(displayName));
            } catch (Exception e) {
                FppLogger.debug("Failed to set display name for " + fp.getName() + ": " + e.getMessage());
            }
        }

        try {
            var maxHpAttr = player.getAttribute(AttributeCompat.MAX_HEALTH);
            if (maxHpAttr != null) {
                double hp = Config.maxHealth();
                maxHpAttr.setBaseValue(hp);
                player.setHealth(hp);
            }
        } catch (Exception ignored) {
        }

        player.setAllowFlight(false);
        player.setFlying(false);

        boolean shouldNudgeDown = true;
        try {
            shouldNudgeDown = !player.isInWater() && !player.isInLava();
        } catch (Throwable ignored) {
        }
        if (shouldNudgeDown) {
            player.setVelocity(new Vector(0, -0.001, 0));
        }

        Config.debug("FakePlayerBody: spawned "
                + fp.getName()
                + " (gravity=true, damageable="
                + Config.bodyDamageable()
                + ", flying=false)");

        applyPaperSkin(player, fp.getResolvedSkin());
    }

    public static void removeAll(FakePlayer fp) {
        removeAll(fp, false, "body_remove");
    }

    public static void removeAll(FakePlayer fp, String reason) {
        removeAll(fp, false, reason);
    }

    public static void removeAllFast(FakePlayer fp) {
        removeAll(fp, true, "body_remove");
    }

    public static void removeAllFast(FakePlayer fp, String reason) {
        removeAll(fp, true, reason);
    }

    public static void removeAllWithoutSaving(FakePlayer fp) {
        removeAll(fp, true, "body_remove");
    }

    public static void removeAllWithoutSaving(FakePlayer fp, String reason) {
        removeAll(fp, true, reason);
    }

    private static void removeAll(FakePlayer fp, boolean fast, String reason) {
        if (fp == null) return;
        try {
            Player player = fp.getPlayer();
            if (player != null && player.isOnline()) {
                if (fast) {
                    Config.debugNmsBot("FakePlayerBody.removeAllFast: '" + fp.getName() + "' - reason: " + reason);
                    NmsPlayerSpawner.removeFakePlayerFast(player, reason);
                } else {
                    Config.debugNmsBot("FakePlayerBody.removeAll: '" + fp.getName() + "' - reason: " + reason);
                    NmsPlayerSpawner.removeFakePlayer(player, reason);
                }
            } else {
                Config.debugNmsBot(
                        "FakePlayerBody.removeAll skipped: player is null or offline for '" + fp.getName() + "'");
            }
        } catch (Exception e) {
            FppLogger.error("FakePlayerBody.removeAll failed for "
                    + (fp.getName() != null ? fp.getName() : "?")
                    + " (reason: " + reason + "): "
                    + e.getMessage());
        }
    }

    public static void resolveAndFinish(
            Plugin plugin, FakePlayer fp, Location loc, Runnable onReady, @Nullable Runnable onSkinApplied) {
        FakePlayerPlugin fpp = FakePlayerPlugin.getInstance();
        SkinManager skinManager = fpp != null ? fpp.getSkinManager() : null;
        if (skinManager == null) {
            FppScheduler.runAtLocation(plugin, loc, () -> {
                onReady.run();
                if (onSkinApplied != null) onSkinApplied.run();
            });
            return;
        }

        SkinProfile resolved = fp.getResolvedSkin();
        if (resolved == null || !resolved.isValid()) {
            FppScheduler.runAtLocation(plugin, loc, onReady);
            skinManager.resolveEffectiveSkin(
                    fp,
                    skin -> FppScheduler.runAtLocation(plugin, loc, () -> {
                        Player body = fp.getPlayer();
                        if (body != null && body.isOnline()) {
                            applyResolvedSkin(plugin, fp, body);
                        }
                        if (skin != null && skin.isValid() && onSkinApplied != null) {
                            onSkinApplied.run();
                        }
                    }));
            return;
        }

        skinManager.resolveEffectiveSkin(
                fp,
                skin -> FppScheduler.runAtLocation(plugin, loc, () -> {
                    onReady.run();
                    if (skin != null && skin.isValid() && onSkinApplied != null) {
                        onSkinApplied.run();
                    }
                }));
    }

    public static void resolveAndFinish(Plugin plugin, FakePlayer fp, Location loc, Runnable onReady) {
        resolveAndFinish(plugin, fp, loc, onReady, null);
    }

    public static void applyResolvedSkin(Plugin plugin, FakePlayer fp, Entity body) {
        if (!(body instanceof Player player)) return;
        FakePlayerPlugin fpp = FakePlayerPlugin.getInstance();
        applyPaperSkin(player, fp.getResolvedSkin());
    }

    private static void applyPaperSkin(Player bot, SkinProfile skin) {
        if (skin == null || !skin.isValid()) return;
        FakePlayerPlugin fpp = FakePlayerPlugin.getInstance();
        if (NmsPlayerSpawner.isFoliaServer()) {
            NmsPlayerSpawner.applySkinToGameProfile(bot, skin);
            NmsPlayerSpawner.forceAllSkinParts(bot);
            return;
        }
        if (fpp != null && fpp.getSkinManager() != null) {
            FakePlayer fp = fpp.getFakePlayerManager() != null
                    ? fpp.getFakePlayerManager().getByUuid(bot.getUniqueId())
                    : null;
            if (fp != null && fpp.getSkinManager().applySkinFromProfile(fp, skin)) {
                Config.debugSkin("FakePlayerBody: skin-manager skin applied to "
                        + bot.getName()
                        + " ("
                        + skin.getSource()
                        + ")");
                return;
            }
        }
        try {
            var profile = bot.getPlayerProfile();
            profile.removeProperty("textures");
            profile.setProperty(new ProfileProperty(
                    "textures", skin.getValue(), skin.getSignature() != null ? skin.getSignature() : ""));
            bot.setPlayerProfile(profile);
            Config.debugSkin("FakePlayerBody: paper skin applied to " + bot.getName() + " (" + skin.getSource() + ")");

            NmsPlayerSpawner.forceAllSkinParts(bot);
        } catch (Exception e) {
            FppLogger.debug("FakePlayerBody: paper skin apply failed for " + bot.getName() + ": " + e.getMessage());
        }
    }

    public static void removeNametag(FakePlayer fp) {}

    public static Entity spawnNametag(FakePlayer fp, Entity body) {

        return null;
    }

    public static void removeOrphanedNametags(String reason) {}

    public static void removeOrphanedBodies(String reason) {}
}
