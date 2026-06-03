package me.bill.fakePlayerPlugin.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotRenameEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.command.StorageStore;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.SkinProfile;
import me.bill.fakePlayerPlugin.lang.Lang;

import net.kyori.adventure.text.Component;

public final class BotRenameHelper {

    private static final long POLL_TICKS = 5L;

    private static final int POLL_TIMEOUT = 200;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public BotRenameHelper(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public boolean rename(@NotNull CommandSender sender, @NotNull FakePlayer bot, @NotNull String requestedName) {
        return rename(bot, requestedName, sender::sendMessage);
    }

    public boolean rename(
            @NotNull FakePlayer bot, @NotNull String requestedName, @NotNull Consumer<Component> feedback) {
        ValidationResult validation = validate(bot, requestedName.trim());
        validation.messages().forEach(feedback);
        if (!validation.allowed()) {
            return false;
        }

        startRename(bot, validation.spawnArg(), validation.finalName(), feedback);
        return true;
    }

    private ValidationResult validate(@NotNull FakePlayer bot, @NotNull String requestedName) {
        List<Component> messages = new ArrayList<>();
        if (requestedName.isEmpty() || requestedName.length() > 16 || !requestedName.matches("[a-zA-Z0-9_]+")) {
            return ValidationResult.failure(messages, Lang.get("rename-invalid-name"));
        }

        ResolvedName resolved = resolveTarget(requestedName);
        Component availabilityProblem = validateAvailability(bot, resolved.finalName());
        if (availabilityProblem != null) {
            return ValidationResult.failure(messages, availabilityProblem);
        }

        if (Config.isBadwordFilterEnabled()) {
            if (BadwordFilter.getBadwordCount() == 0) {
                messages.add(Lang.get("badword-filter-empty-warning"));
            } else if (!BadwordFilter.isAllowed(resolved.spawnArg())) {
                String badword = BadwordFilter.findBadword(resolved.spawnArg());
                if (!Config.isBadwordAutoRenameEnabled()) {
                    return ValidationResult.failure(
                            messages,
                            Lang.get(
                                    "rename-badword-rejected",
                                    "name",
                                    requestedName,
                                    "badword",
                                    badword != null ? badword : "???"));
                }

                String sanitized = findAvailableSanitizedName(bot);
                if (sanitized == null) {
                    return ValidationResult.failure(
                            messages,
                            Lang.get(
                                    "rename-badword-rejected",
                                    "name",
                                    requestedName,
                                    "badword",
                                    badword != null ? badword : "???"));
                }

                resolved = resolveTarget(sanitized);
                availabilityProblem = validateAvailability(bot, resolved.finalName());
                if (availabilityProblem != null) {
                    return ValidationResult.failure(messages, availabilityProblem);
                }

                messages.add(Lang.get(
                        "rename-badword-auto-renamed", "original", requestedName, "name", resolved.finalName()));
            }
        }

        return ValidationResult.success(messages, resolved.spawnArg(), resolved.finalName());
    }

    private @Nullable Component validateAvailability(@NotNull FakePlayer bot, @NotNull String finalName) {
        if (finalName.equalsIgnoreCase(bot.getName())) {
            return Lang.get("rename-same-name");
        }
        FakePlayer existingBot = manager.getByName(finalName);
        if (existingBot != null && !existingBot.getUuid().equals(bot.getUuid())) {
            return Lang.get("rename-name-taken", "name", finalName);
        }
        if (Bukkit.getPlayerExact(finalName) != null) {
            return Lang.get("rename-name-taken-player", "name", finalName);
        }
        return null;
    }

    private @Nullable String findAvailableSanitizedName(@NotNull FakePlayer bot) {
        List<String> pool = new ArrayList<>(BotNameConfig.getNames());
        if (pool.isEmpty()) {
            return null;
        }
        Collections.shuffle(pool);
        for (String candidate : pool) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String trimmed = candidate.trim();
            if (trimmed.length() > 16 || !trimmed.matches("[a-zA-Z0-9_]+")) {
                continue;
            }

            ResolvedName resolved = resolveTarget(trimmed);
            if (!BadwordFilter.isAllowed(resolved.spawnArg())) {
                continue;
            }
            if (validateAvailability(bot, resolved.finalName()) != null) {
                continue;
            }
            return resolved.spawnArg();
        }
        return null;
    }

    private void startRename(
            @NotNull FakePlayer bot,
            @NotNull String spawnArg,
            @NotNull String finalName,
            @NotNull Consumer<Component> feedback) {
        String oldName = bot.getName();
        BotSnapshot snapshot = BotSnapshot.from(bot);
        Location loc = bot.getLiveLocation();
        UUID oldUuid = bot.getUuid();
        BotType botType = bot.getBotType();
        UUID expectedNewUuid = manager.refreshIdentity(finalName);

        feedback.accept(Lang.get("rename-starting", "old", oldName, "new", finalName));

        if (loc == null) {
            feedback.accept(Lang.get("rename-failed-no-location", "name", finalName));
            return;
        }

        manager.suppressNextDespawnSnapshot(oldUuid);
        manager.delete(oldName, "rename_swap");

        FppScheduler.runSyncLater(
                plugin,
                () -> {
                    int result = manager.spawn(loc, 1, null, spawnArg, true, botType);
                    if (result <= 0) {
                        manager.clearDespawnSnapshotSuppression(oldUuid);
                        feedback.accept(Lang.get(
                                "rename-failed-spawn", "name", finalName, "reason", spawnFailureReason(result)));
                        return;
                    }

                    FakePlayer newBotRef = manager.getByName(finalName);
                    UUID newUuid = newBotRef != null ? newBotRef.getUuid() : null;
                    if (newUuid != null && !newUuid.equals(expectedNewUuid)) {
                        Config.debugDatabase("Rename identity mismatch for '"
                                + finalName
                                + "': expected "
                                + expectedNewUuid
                                + ", spawned "
                                + newUuid);
                    }

                    scheduleRestore(snapshot, finalName, oldName, oldUuid, newUuid, feedback);
                },
                12L);
    }

    private void scheduleRestore(
            @NotNull BotSnapshot snap,
            @NotNull String newName,
            @NotNull String oldName,
            @NotNull UUID oldUuid,
            @Nullable UUID newUuid,
            @NotNull Consumer<Component> feedback) {
        final int[] elapsed = {0};
        final int[] pollTaskId = {-1};

        Runnable poll = () -> {
            elapsed[0] += (int) POLL_TICKS;

            if (elapsed[0] > POLL_TIMEOUT) {
                if (pollTaskId[0] != -1) {
                    FppScheduler.cancelTask(pollTaskId[0]);
                    pollTaskId[0] = -1;
                }
                manager.unmarkRenaming(oldUuid);
                if (newUuid != null) {
                    manager.unmarkRenaming(newUuid);
                }
                feedback.accept(Lang.get("rename-timeout", "name", newName));
                return;
            }

            FakePlayer newFp = manager.getByName(newName);
            if (newFp == null) {
                return;
            }
            if (!newFp.isBodyless() && newFp.getPlayer() == null) {
                return;
            }

            if (pollTaskId[0] != -1) {
                FppScheduler.cancelTask(pollTaskId[0]);
                pollTaskId[0] = -1;
            }
            snap.applyState(newFp);
            newFp.setSpawnedBy(snap.spawnerName(), snap.spawnerUuid());

            if (snap.resolvedSkin() != null
                    && snap.resolvedSkin().isValid()
                    && plugin.getSkinManager() != null
                    && newFp.getPlayer() != null) {
                plugin.getSkinManager().applySkinFromProfile(newFp, snap.resolvedSkin());
            }
            if (snap.ping() >= 0) {
                manager.applyPing(newFp, snap.ping());
            }

            if (newFp.getPlayer() != null) {
                snap.applyInventoryAndXp(newFp.getPlayer());
            }

            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager()
                        .updateBotPickupSettings(
                                newFp.getUuid().toString(), newFp.isPickUpItemsEnabled(), newFp.isPickUpXpEnabled());
                plugin.getDatabaseManager()
                        .updateBotAiPersonality(newFp.getUuid().toString(), newFp.getAiPersonality());
            }

            if (snap.lpGroup() != null) newFp.setLuckpermsGroup(snap.lpGroup());
            finishRename(oldName, newName, oldUuid, newUuid, feedback);
        };

        pollTaskId[0] = FppScheduler.runSyncRepeatingWithId(plugin, poll, POLL_TICKS, POLL_TICKS);
    }

    private void finishRename(
            @NotNull String oldName,
            @NotNull String newName,
            @NotNull UUID oldUuid,
            @Nullable UUID newUuid,
            @NotNull Consumer<Component> feedback) {
        cleanupRenameState(oldUuid, newUuid);

        StorageStore ss = plugin.getStorageStore();
        if (ss != null) {
            ss.renameBot(oldName, newName);
        }
        broadcastRename(oldName, newName);
        feedback.accept(Lang.get("rename-success", "old", oldName, "new", newName));
    }

    private void cleanupRenameState(@NotNull UUID oldUuid, @Nullable UUID newUuid) {
        manager.unmarkRenaming(oldUuid);
        if (newUuid != null) {
            manager.unmarkRenaming(newUuid);
        }
    }

    private void broadcastRename(@NotNull String oldName, @NotNull String newName) {
        // Fire API rename event.
        var fppApi = plugin.getFppApi();
        if (fppApi != null) {
            FakePlayer renamedFp = manager.getByName(newName);
            if (renamedFp != null) {
                FppBotRenameEvent renameEvt = new FppBotRenameEvent(new FppBotImpl(renamedFp), oldName, newName);
                Bukkit.getPluginManager().callEvent(renameEvt);
            }
        }
        Component msg = Lang.get("bot-rename", "old", oldName, "new", newName);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(msg));
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private @NotNull String spawnFailureReason(int code) {
        return switch (code) {
            case 0 -> "name already taken";
            case -1 -> "global bot limit reached";
            case -2 -> "name failed Minecraft validation";
            default -> "unknown error (code " + code + ")";
        };
    }

    private ResolvedName resolveTarget(@NotNull String requestedName) {
        return new ResolvedName(requestedName, requestedName);
    }

    private record ResolvedName(String spawnArg, String finalName) {}

    private record ValidationResult(
            boolean allowed, @Nullable String spawnArg, @Nullable String finalName, List<Component> messages) {
        static ValidationResult failure(List<Component> messages, Component failure) {
            messages.add(failure);
            return new ValidationResult(false, null, null, List.copyOf(messages));
        }

        static ValidationResult success(List<Component> messages, String spawnArg, String finalName) {
            return new ValidationResult(true, spawnArg, finalName, List.copyOf(messages));
        }
    }

    private static final class BotSnapshot {
        private final ItemStack[] mainContents;
        private final ItemStack[] armorContents;
        private final ItemStack[] extraContents;
        private final int xpLevel;
        private final float xpProgress;
        private final int totalXp;
        private final boolean chatEnabled;
        private final boolean respawnOnDeath;
        private final String chatTier;
        private final boolean frozen;
        private final boolean headAiEnabled;
        private final boolean pickUpItemsEnabled;
        private final boolean pickUpXpEnabled;
        private final String lpGroup;
        private final String spawnerName;
        private final UUID spawnerUuid;
        private final String aiPersonality;
        private final SkinProfile resolvedSkin;
        private final int ping;
        private final boolean pingUserSet;

        private BotSnapshot(
                ItemStack[] mainContents,
                ItemStack[] armorContents,
                ItemStack[] extraContents,
                int xpLevel,
                float xpProgress,
                int totalXp,
                boolean chatEnabled,
                boolean respawnOnDeath,
                String chatTier,
                boolean frozen,
                boolean headAiEnabled,
                boolean pickUpItemsEnabled,
                boolean pickUpXpEnabled,
                String lpGroup,
                String spawnerName,
                UUID spawnerUuid,
                String aiPersonality,
                SkinProfile resolvedSkin,
                int ping,
                boolean pingUserSet) {
            this.mainContents = mainContents;
            this.armorContents = armorContents;
            this.extraContents = extraContents;
            this.xpLevel = xpLevel;
            this.xpProgress = xpProgress;
            this.totalXp = totalXp;
            this.chatEnabled = chatEnabled;
            this.respawnOnDeath = respawnOnDeath;
            this.chatTier = chatTier;
            this.frozen = frozen;
            this.headAiEnabled = headAiEnabled;
            this.pickUpItemsEnabled = pickUpItemsEnabled;
            this.pickUpXpEnabled = pickUpXpEnabled;
            this.lpGroup = lpGroup;
            this.spawnerName = spawnerName;
            this.spawnerUuid = spawnerUuid;
            this.aiPersonality = aiPersonality;
            this.resolvedSkin = resolvedSkin;
            this.ping = ping;
            this.pingUserSet = pingUserSet;
        }

        static BotSnapshot from(@NotNull FakePlayer fp) {
            Player entity = fp.getPlayer();
            ItemStack[] main = cloneItems(entity != null ? entity.getInventory().getContents() : new ItemStack[36]);
            ItemStack[] armor =
                    cloneItems(entity != null ? entity.getInventory().getArmorContents() : new ItemStack[4]);
            ItemStack[] extra =
                    cloneItems(entity != null ? entity.getInventory().getExtraContents() : new ItemStack[1]);
            int lvl = entity != null ? entity.getLevel() : 0;
            float prog = entity != null ? entity.getExp() : 0f;
            int tot = entity != null ? entity.getTotalExperience() : 0;

            String lpg = fp.getLuckpermsGroup();
            if ("default".equalsIgnoreCase(lpg)) {
                lpg = null;
            }

            return new BotSnapshot(
                    main,
                    armor,
                    extra,
                    lvl,
                    prog,
                    tot,
                    fp.isChatEnabled(),
                    fp.isRespawnOnDeath(),
                    fp.getChatTier(),
                    fp.isFrozen(),
                    fp.isHeadAiEnabled(),
                    fp.isPickUpItemsEnabled(),
                    fp.isPickUpXpEnabled(),
                    lpg,
                    fp.getSpawnedBy(),
                    fp.getSpawnedByUuid(),
                    fp.getAiPersonality(),
                    fp.getResolvedSkin(),
                    fp.getPing(),
                    fp.hasCustomPing());
        }

        String lpGroup() {
            return lpGroup;
        }

        String spawnerName() {
            return spawnerName;
        }

        UUID spawnerUuid() {
            return spawnerUuid;
        }

        SkinProfile resolvedSkin() {
            return resolvedSkin;
        }

        int ping() {
            return ping;
        }

        void applyInventoryAndXp(@NotNull Player entity) {
            PlayerInventory inv = entity.getInventory();
            if (mainContents.length > 0) {
                inv.setContents(mainContents);
            }
            if (armorContents.length > 0) {
                inv.setArmorContents(armorContents);
            }
            if (extraContents.length > 0) {
                inv.setExtraContents(extraContents);
            }
            entity.setLevel(xpLevel);
            entity.setExp(Math.max(0f, Math.min(1f, xpProgress)));
            entity.setTotalExperience(totalXp);
        }

        void applyState(@NotNull FakePlayer fp) {
            fp.setChatEnabled(chatEnabled);
            fp.setRespawnOnDeath(respawnOnDeath);
            fp.setHeadAiEnabled(headAiEnabled);
            fp.setPickUpItemsEnabled(pickUpItemsEnabled);
            fp.setPickUpXpEnabled(pickUpXpEnabled);
            fp.setAiPersonality(aiPersonality);
            if (resolvedSkin != null && resolvedSkin.isValid()) {
                fp.setResolvedSkin(resolvedSkin);
            }
            if (pingUserSet) {
                fp.setUserPing(ping);
            } else {
                fp.setPing(ping);
            }
            if (chatTier != null) {
                fp.setChatTier(chatTier);
            }
            if (frozen) {
                fp.setFrozen(true);
            }
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            if (items == null) {
                return new ItemStack[0];
            }
            ItemStack[] copy = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                copy[i] = items[i] != null ? items[i].clone() : null;
            }
            return copy;
        }
    }
}
