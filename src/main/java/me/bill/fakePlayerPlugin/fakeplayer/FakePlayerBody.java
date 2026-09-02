package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

public final class FakePlayerBody {

    public static final String VISUAL_PDC_VALUE = "fpp-visual";

    public static final String NAMETAG_PDC_VALUE = "fpp-nametag";

    /**
     * NMS team used only to hide bots' vanilla over-head names (both rows live in the text_display).
     * Folia's Scoreboard/Team API is documented as entirely broken - global state the project hasn't
     * implemented for regionised ticking - so this team is built directly from NMS and is
     * deliberately never registered with Bukkit's/the server's real scoreboard. It exists purely as
     * a data holder for building {@code ClientboundSetPlayerTeamPacket}s, which are pushed to
     * clients by hand (see {@link #hideVanillaNameNow}/{@link #unhideVanillaNameNow}).
     */
    private static final String HIDDEN_NAME_TEAM = "fpp_hidden_names";

    private static volatile PlayerTeam hideTeam;

    /** Bot names currently hidden - the source of truth, since we don't rely on the team object's own player set. */
    private static final Set<String> hiddenNames = ConcurrentHashMap.newKeySet();

    /** Viewers (real players) who have already been sent the team-create packet for {@link #hideTeam}. */
    private static final Set<UUID> viewersKnowingTeam = ConcurrentHashMap.newKeySet();

    private FakePlayerBody() {}

    private static PlayerTeam hideTeam() {
        PlayerTeam t = hideTeam;
        if (t != null) return t;
        synchronized (FakePlayerBody.class) {
            if (hideTeam != null) return hideTeam;
            try {
                MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
                PlayerTeam team = new PlayerTeam(server.getScoreboard(), HIDDEN_NAME_TEAM);
                team.setNameTagVisibility(Team.Visibility.NEVER);
                team.setCollisionRule(Team.CollisionRule.ALWAYS);
                hideTeam = team;
            } catch (Throwable t2) {
                FppLogger.debug("FakePlayerBody: failed to build hide-nametag team: " + t2.getMessage());
            }
            return hideTeam;
        }
    }

    /**
     * Brings a newly-joined (or newly-synced) viewer up to date on the hide-team and every bot
     * currently hidden from it. Since the team is never registered with the real scoreboard, vanilla
     * login-sync never tells the client about it - this call is the only way they learn.
     */
    public static void syncHiddenNamesTo(Player viewer) {
        PlayerTeam team = hideTeam();
        if (team == null) return;
        viewersKnowingTeam.add(viewer.getUniqueId());
        PacketHelper.sendTeamCreate(viewer, team);
        for (String name : hiddenNames) {
            PacketHelper.sendTeamMembership(viewer, team, name, true);
        }
    }

    /** Forgets a disconnected viewer so a future rejoin gets a fresh full sync. */
    public static void forgetViewer(UUID viewerUuid) {
        viewersKnowingTeam.remove(viewerUuid);
    }

    private static void ensureViewerKnowsTeam(Player viewer, PlayerTeam team) {
        if (viewersKnowingTeam.add(viewer.getUniqueId())) {
            PacketHelper.sendTeamCreate(viewer, team);
        }
    }

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

        spawnNametag(fp, player);
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
        removeNametag(fp);
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
        if (skin == null || !skin.isValid()) {
            Config.debugSkinPool(
                    "applyPaperSkin(" + bot.getName() + "): no valid resolved skin to apply - stays default.");
            return;
        }
        Config.debugSkinPool("applyPaperSkin(" + bot.getName() + "): applying skin (" + skin.getSource() + ", folia="
                + NmsPlayerSpawner.isFoliaServer() + ").");
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

    public static void removeNametag(FakePlayer fp) {
        if (fp == null) return;
        unhideVanillaName(fp);
        Entity tag = fp.getNametagEntity();
        fp.setNametagEntity(null);
        if (tag == null) return;
        if (NmsPlayerSpawner.isFoliaServer()) {
            FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
            if (plugin != null) {
                FppScheduler.runAtEntity(plugin, tag, () -> {
                    try {
                        tag.remove();
                    } catch (Throwable ignored) {
                    }
                });
                return;
            }
        }
        try {
            tag.remove();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Spawns the mannequin-style name tag (a {@link TextDisplay}) that carries BOTH rows - the bot's
     * name and the configured indicator (default {@code bot by <owner>}) - and hides the bot's vanilla
     * over-head name so it does not overlay the display.
     */
    public static Entity spawnNametag(FakePlayer fp, Entity body) {
        if (fp == null || body == null || !Config.nametagSecondLineEnabled()) return null;
        Location base = body.getLocation();
        if (base.getWorld() == null) return null;
        removeNametag(fp);
        try {
            hideVanillaName(fp);
            int interp = Math.max(0, Config.nametagInterpolationTicks());
            Location loc = base.clone().add(0, Config.nametagSecondLineYOffset(), 0);
            TextDisplay display = base.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.text(buildNametagComponent(fp));
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(false);
                td.setShadowed(false);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setPersistent(false);
                td.setInvulnerable(true);
                // Interpolate to each new position so it follows the bot smoothly instead of jumping
                // (matched to the client-side entity lerp FPP uses for the bot's movement).
                td.setTeleportDuration(interp);
                if (FakePlayerManager.FAKE_PLAYER_KEY != null) {
                    td.getPersistentDataContainer()
                            .set(
                                    FakePlayerManager.FAKE_PLAYER_KEY,
                                    PersistentDataType.STRING,
                                    NAMETAG_PDC_VALUE + ":" + fp.getName());
                }
            });
            fp.setNametagEntity(display);
            return display;
        } catch (Throwable e) {
            FppLogger.debug("FakePlayerBody.spawnNametag failed for " + fp.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Rebuilds the tag text (e.g. after an owner change). The text_display is entity state, so on
     * Folia the actual mutation must run on the region thread that currently owns it - callers may
     * invoke this from anywhere (a command, an async callback, the global-region activity-refresh
     * loop), so the dispatch happens here rather than being the caller's responsibility.
     */
    public static void refreshNametag(FakePlayer fp) {
        if (fp == null) return;
        // Cheap, idempotent self-heal: re-assert the vanilla-name-hiding team membership on every
        // refresh (every ~10 ticks, driven by the activity-label loop) in case the one-time hide at
        // spawn didn't stick (e.g. a scoreboard that wasn't ready yet, or a missed broadcast).
        hideVanillaName(fp);
        if (!(fp.getNametagEntity() instanceof TextDisplay td)) return;
        if (NmsPlayerSpawner.isFoliaServer()) {
            FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
            if (plugin == null) return;
            FppScheduler.runAtEntity(plugin, td, () -> {
                if (td.isValid()) td.text(buildNametagComponent(fp));
            });
            return;
        }
        if (td.isValid()) td.text(buildNametagComponent(fp));
    }

    /**
     * Re-asserts that this bot's vanilla over-head name is hidden, independent of the text_display
     * refresh cycle. Minecraft only allows one scoreboard team per player at a time, so anything else
     * that puts the bot on a <em>different</em> team (another plugin's own prefix/nametag-color
     * system, an admin running {@code /scoreboard teams join}, …) silently evicts it from ours -
     * bringing the real name back - without ever touching the text_display, so
     * {@link #refreshNametag} alone wouldn't catch it for a bot whose activity label never changes.
     * Callers should invoke this unconditionally on a steady cadence (not gated on label change) so
     * the hide genuinely never lapses for more than one sweep interval, regardless of cause.
     */
    public static void reassertVanillaNameHidden(FakePlayer fp) {
        hideVanillaName(fp);
    }

    private static void hideVanillaName(FakePlayer fp) {
        // Building/sending packets touches no per-entity region state, but keep this on the global
        // region on Folia anyway for consistency with the rest of the visual-sync pipeline.
        if (NmsPlayerSpawner.isFoliaServer()) {
            FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
            if (plugin != null) {
                FppScheduler.runSync(plugin, () -> hideVanillaNameNow(fp));
                return;
            }
        }
        hideVanillaNameNow(fp);
    }

    private static void hideVanillaNameNow(FakePlayer fp) {
        try {
            PlayerTeam team = hideTeam();
            if (team == null) return;
            String name = fp.getName();
            hiddenNames.add(name);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                ensureViewerKnowsTeam(viewer, team);
                PacketHelper.sendTeamMembership(viewer, team, name, true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void unhideVanillaName(FakePlayer fp) {
        if (NmsPlayerSpawner.isFoliaServer()) {
            FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
            if (plugin != null) {
                FppScheduler.runSync(plugin, () -> unhideVanillaNameNow(fp));
                return;
            }
        }
        unhideVanillaNameNow(fp);
    }

    private static void unhideVanillaNameNow(FakePlayer fp) {
        try {
            String name = fp.getName();
            // removeNametag() (and therefore this) unconditionally runs before EVERY spawn, including
            // a bot's very first one - where its name was never added to any client's team roster.
            // The vanilla client's Scoreboard#removePlayerFromTeam throws (and disconnects!) if asked
            // to remove a name that isn't currently a member, so only broadcast REMOVE for a name that
            // really was hidden.
            boolean wasHidden = hiddenNames.remove(name);
            if (!wasHidden) return;
            PlayerTeam team = hideTeam;
            if (team == null) return;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PacketHelper.sendTeamMembership(viewer, team, name, false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Keeps the second-line display glued below the moving bot. Called from the tick loop. */
    public static void moveNametag(FakePlayer fp, Location botLoc) {
        if (fp == null || botLoc == null) return;
        Entity tag = fp.getNametagEntity();
        if (tag == null) return;
        if (!tag.isValid()) {
            fp.setNametagEntity(null);
            return;
        }
        Location target = botLoc.clone().add(0, Config.nametagSecondLineYOffset(), 0);
        try {
            if (NmsPlayerSpawner.isFoliaServer() || tag.getWorld() != target.getWorld()) {
                tag.teleportAsync(target);
            } else {
                tag.teleport(target);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Component buildNametagComponent(FakePlayer fp) {
        String owner = fp.getSpawnedBy() != null && !fp.getSpawnedBy().isBlank() ? fp.getSpawnedBy() : "?";
        String rawName = fp.getRawDisplayName() != null ? fp.getRawDisplayName() : fp.getDisplayName();
        Component indicatorRow =
                TextUtil.colorize(Config.nametagSecondLineFormat().replace("{owner}", owner));
        // Rows below inherit color from whatever they're appended onto, so give each an explicit
        // color instead of relying on defaults - otherwise a colorless row picks up its parent's.
        Component nameRow = TextUtil.colorize(rawName != null && !rawName.isBlank() ? rawName : fp.getName());
        if (nameRow.color() == null) nameRow = nameRow.color(TextColor.fromHexString("#EEECF7"));
        TextColor indicatorColor =
                indicatorRow.color() != null ? indicatorRow.color() : TextColor.fromHexString("#9691AB");
        Component actionRow = Component.text(BotActivity.currentLabel(fp))
                .color(indicatorColor)
                .decoration(TextDecoration.ITALIC, true);
        // Root is colorless so each row keeps its own explicit color instead of inheriting one.
        return Component.empty()
                .append(actionRow)
                .append(Component.newline())
                .append(nameRow)
                .append(Component.newline())
                .append(indicatorRow);
    }

    public static void removeOrphanedNametags(String reason) {
        FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
        if (plugin == null || FakePlayerManager.FAKE_PLAYER_KEY == null) return;
        FakePlayerManager mgr = plugin.getFakePlayerManager();
        String prefix = NAMETAG_PDC_VALUE + ":";
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay td : world.getEntitiesByClass(TextDisplay.class)) {
                String tag = td.getPersistentDataContainer()
                        .get(FakePlayerManager.FAKE_PLAYER_KEY, PersistentDataType.STRING);
                if (tag == null || !tag.startsWith(prefix)) continue;
                String botName = tag.substring(prefix.length());
                if (mgr == null || mgr.getByName(botName) == null) {
                    try {
                        td.remove();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    public static void removeOrphanedBodies(String reason) {}
}
