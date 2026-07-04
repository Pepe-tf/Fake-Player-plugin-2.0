package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

/**
 * Core-owned skin orchestration. Skins come exclusively from the rarity pools
 * ({@link SkinPoolService}) — there is no player-name lookup, so a bot can never wear a real
 * account's identity. A bot that already carries a resolved skin (persistence restore, respawn
 * snapshot, manual apply) keeps it; the rarity roll happens once per fresh spawn.
 */
public final class SkinManager {

    private final FakePlayerPlugin plugin;

    public SkinManager(@NotNull FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        SkinPoolService pools = plugin.getSkinPoolService();
        if (pools != null) pools.reload();
    }

    public void resolveEffectiveSkin(@NotNull FakePlayer fp, @NotNull Consumer<@Nullable SkinProfile> callback) {
        // A bot that already carries a skin (persistence restore, respawn snapshot, manual apply)
        // keeps it — the rarity roll happens once per fresh spawn, never again.
        SkinProfile existing = fp.getResolvedSkin();
        if (existing != null && existing.isValid()) {
            Config.debugSkinPool("resolveEffectiveSkin('" + fp.getName() + "'): already has a resolved skin ("
                    + existing.getSource() + ") - reusing it.");
            deliver(callback, existing);
            return;
        }

        SkinPoolService pools = plugin.getSkinPoolService();
        if (pools != null && Config.skinRarePoolsEnabled()) {
            pools.rollAndResolve(fp, skin -> {
                if (skin != null && skin.isValid()) {
                    fp.setResolvedSkin(skin);
                    persistSkinToDb(fp, skin);
                    Config.debugSkinPool("resolveEffectiveSkin('" + fp.getName() + "'): pool skin resolved ("
                            + skin.getSource() + ") - handing off to callback for application.");
                    deliver(callback, skin);
                } else {
                    Config.debugSkinPool("resolveEffectiveSkin('" + fp.getName()
                            + "'): pool returned nothing - falling back to vanilla default skin.");
                    deliver(callback, BotSkin.fixed());
                }
            });
            return;
        }

        Config.debugSkinPool("resolveEffectiveSkin('" + fp.getName() + "'): skin pools "
                + (pools == null ? "unavailable" : "disabled (skin.rare-pools: false)") + " - vanilla default skin.");
        deliver(callback, BotSkin.fixed());
    }

    private void persistSkinToDb(@NotNull FakePlayer fp, @Nullable SkinProfile skin) {
        if (skin == null || !skin.isValid()) return;
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().updateBotSkin(fp.getUuid().toString(), skin.getValue(), skin.getSignature());
        }
    }

    public @Nullable SkinProfile getPreferredSkin(@NotNull FakePlayer fp) {
        SkinProfile resolved = fp.getResolvedSkin();
        if (resolved != null && resolved.isValid() && resolved.getSource().startsWith("nametag:")) {
            return resolved;
        }
        return null;
    }

    public boolean applySkinFromProfile(@NotNull FakePlayer bot, @Nullable SkinProfile skin) {
        if (skin == null || !skin.isValid()) return false;
        if (shouldPreserveNameTagSkin(bot) && !skin.getSource().startsWith("nametag:")) return false;
        boolean applied = applySkinFromTextures(bot, skin.getValue(), skin.getSignature());
        if (applied) {
            bot.setResolvedSkin(skin);
        }
        return applied;
    }

    public boolean applySkinFromTextures(@NotNull FakePlayer bot, @NotNull String texture, @Nullable String signature) {
        if (texture.isBlank()) return false;
        Player botPlayer = bot.getPlayer();
        if (botPlayer == null || !botPlayer.isOnline()) return false;

        if (NmsPlayerSpawner.isFoliaServer()) {
            SkinProfile appliedSkin = new SkinProfile(texture, signature, "direct:" + bot.getName());
            bot.setResolvedSkin(appliedSkin);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, appliedSkin);
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), texture, signature);
            }
            return true;
        }

        try {
            PlayerProfile profile = botPlayer.getPlayerProfile();
            profile.removeProperty("textures");
            profile.setProperty(new ProfileProperty("textures", texture, signature != null ? signature : ""));
            botPlayer.setPlayerProfile(profile);
            SkinProfile appliedSkin = new SkinProfile(texture, signature, "direct:" + bot.getName());
            bot.setResolvedSkin(appliedSkin);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, appliedSkin);

            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), texture, signature);
            }

            FakePlayerManager mgr = plugin.getFakePlayerManager();
            if (mgr != null) {
                mgr.refreshSkinForAll(bot);
            }
            return true;
        } catch (Exception e) {
            FppLogger.warn("SkinManager: failed to apply texture skin to " + bot.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean resetToDefaultSkin(@NotNull FakePlayer bot) {
        Player botPlayer = bot.getPlayer();
        if (botPlayer == null || !botPlayer.isOnline()) return false;

        if (NmsPlayerSpawner.isFoliaServer()) {
            bot.setResolvedSkin(null);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, null);
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), null, null);
            }
            return true;
        }

        try {
            PlayerProfile profile = botPlayer.getPlayerProfile();
            profile.removeProperty("textures");
            profile.clearProperties();
            botPlayer.setPlayerProfile(profile);
            bot.setResolvedSkin(null);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, null);

            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), null, null);
            }

            FakePlayerManager mgr = plugin.getFakePlayerManager();
            if (mgr != null) {
                mgr.refreshSkinForAll(bot);
            }
            return true;
        } catch (Exception e) {
            FppLogger.warn("SkinManager: failed to reset skin for " + bot.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private boolean shouldPreserveNameTagSkin(FakePlayer bot) {
        return getPreferredSkin(bot) != null;
    }

    private void deliver(Consumer<@Nullable SkinProfile> callback, @Nullable SkinProfile profile) {
        if (Bukkit.isPrimaryThread()) {
            callback.accept(profile);
            return;
        }
        FppScheduler.runSync(plugin, () -> callback.accept(profile));
    }
}
