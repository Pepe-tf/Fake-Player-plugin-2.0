package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.SkinProfile;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.TextUtil;

public class BadwordCommand implements FppCommand {

    private static final String ACCENT = "<#0079FF>";
    private static final String CLOSE = "</#0079FF>";
    private static final String GRAY = "<gray>";
    private static final String GREEN = "<green>";
    private static final String RED = "<red>";
    private static final String YELLOW = "<yellow>";

    private static final long POLL_INTERVAL_TICKS = 5L;

    private static final int POLL_TIMEOUT_TICKS = 200;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public BadwordCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "badword";
    }

    @Override
    public String getUsage() {
        return "<check|update|status>";
    }

    @Override
    public String getDescription() {
        return "Scan and fix bot names flagged by the badword filter.";
    }

    @Override
    public String getPermission() {
        return Perm.BADWORD;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.BADWORD);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (Perm.missing(sender, Perm.BADWORD)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check" -> doCheck(sender);
            case "update" -> doUpdate(sender);
            case "status" -> doStatus(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void doCheck(CommandSender sender) {
        if (!Config.isBadwordFilterEnabled()) {
            msg(
                    sender,
                    YELLOW
                            + "⚠ "
                            + GRAY
                            + "Badword filter is "
                            + RED
                            + "disabled"
                            + GRAY
                            + ". Enable it with "
                            + ACCENT
                            + "badword-filter.enabled: true"
                            + CLOSE
                            + GRAY
                            + " in config.yml, then run "
                            + ACCENT
                            + "/fpp reload"
                            + CLOSE
                            + GRAY
                            + ".");
            return;
        }
        if (BadwordFilter.getBadwordCount() == 0) {
            msg(
                    sender,
                    YELLOW
                            + "⚠ "
                            + GRAY
                            + "No badword sources are active."
                            + " Enable "
                            + ACCENT
                            + "badword-filter.use-global-list"
                            + CLOSE
                            + GRAY
                            + " or add words to "
                            + ACCENT
                            + "badword-filter.words"
                            + CLOSE
                            + GRAY
                            + " / "
                            + ACCENT
                            + "bad-words.yml"
                            + CLOSE
                            + GRAY
                            + ".");
            return;
        }

        List<FakePlayer> flagged = findFlaggedBots();
        int total = manager.getActivePlayers().size();

        if (flagged.isEmpty()) {
            msg(sender, GREEN + "✔ " + GRAY + "All " + total + " active bot(s) have clean names.");
            return;
        }

        msg(sender, ACCENT + "ꜰʟᴀɡɡᴇᴅ ʙᴏᴛ ɴᴀᴍᴇꜱ" + CLOSE + GRAY + " (" + flagged.size() + " of " + total + " bots):");
        for (FakePlayer fp : flagged) {
            String badword = BadwordFilter.findBadword(fp.getName());
            msg(
                    sender,
                    GRAY
                            + "  • "
                            + ACCENT
                            + fp.getName()
                            + CLOSE
                            + GRAY
                            + " — contains: "
                            + RED
                            + (badword != null ? badword : "???"));
        }
        msg(
                sender,
                GRAY
                        + "  Run "
                        + ACCENT
                        + "/fpp badword update"
                        + CLOSE
                        + GRAY
                        + " to replace all flagged names with random clean names.");
    }

    public void doUpdate(CommandSender sender) {
        if (!Config.isBadwordFilterEnabled()) {
            msg(
                    sender,
                    YELLOW
                            + "⚠ "
                            + GRAY
                            + "Badword filter is "
                            + RED
                            + "disabled"
                            + GRAY
                            + ". Enable it in config.yml first.");
            return;
        }
        if (BadwordFilter.getBadwordCount() == 0) {
            msg(sender, YELLOW + "⚠ " + GRAY + "No badword sources are active — nothing to check.");
            return;
        }

        List<FakePlayer> flagged = findFlaggedBots();
        if (flagged.isEmpty()) {
            msg(
                    sender,
                    GREEN
                            + "✔ "
                            + GRAY
                            + "All "
                            + manager.getActivePlayers().size()
                            + " active bot(s) have clean names — nothing to update.");
            return;
        }

        msg(sender, ACCENT + "ʙᴀᴅᴡᴏʀᴅ ᴜᴘᴅᴀᴛᴇ" + CLOSE + GRAY + " — processing " + flagged.size() + " flagged bot(s)…");

        Set<String> reserved = new HashSet<>();
        List<RenameTask> tasks = new ArrayList<>();

        for (FakePlayer fp : flagged) {
            String cleanName = findAvailableCleanName(reserved);
            if (cleanName == null) {
                msg(
                        sender,
                        RED
                                + "  ✘ "
                                + GRAY
                                + "No clean name available for "
                                + ACCENT
                                + fp.getName()
                                + CLOSE
                                + GRAY
                                + " — add more names to bot-names.yml and retry.");
                continue;
            }
            reserved.add(cleanName.toLowerCase());
            tasks.add(new RenameTask(
                    fp.getName(), cleanName, fp.getLiveLocation(), fp.getBotType(), BotSnapshot.from(fp)));
        }

        if (tasks.isEmpty()) {
            msg(
                    sender,
                    RED
                            + "✘ "
                            + GRAY
                            + "Could not find enough clean names in the pool."
                            + " Add more names to bot-names.yml and retry.");
            return;
        }

        for (RenameTask task : tasks) {
            msg(
                    sender,
                    GRAY
                            + "  Renaming "
                            + RED
                            + task.oldName()
                            + GRAY
                            + " → "
                            + ACCENT
                            + task.newName()
                            + CLOSE
                            + GRAY
                            + "…");
            manager.delete(task.oldName(), "badword_filter");
        }

        for (int i = 0; i < tasks.size(); i++) {
            final RenameTask task = tasks.get(i);
            final long delay = 12L + (long) i * 8L;

            FppScheduler.runSyncLater(
                    plugin,
                    () -> {
                        Location loc = task.location();
                        if (loc == null) {
                            msg(
                                    sender,
                                    RED
                                            + "  ✘ "
                                            + GRAY
                                            + "Cannot respawn "
                                            + ACCENT
                                            + task.newName()
                                            + CLOSE
                                            + GRAY
                                            + " — no location saved (bodyless bot had no"
                                            + " world position).");
                            return;
                        }

                        int result = manager.spawn(loc, 1, null, task.newName(), true, task.botType());
                        if (result <= 0) {
                            String reason =
                                    switch (result) {
                                        case 0 -> "name already taken";
                                        case -1 -> "global bot limit reached";
                                        case -2 -> "name failed Minecraft validation";
                                        default -> "unknown error (code " + result + ")";
                                    };
                            msg(
                                    sender,
                                    RED
                                            + "  ✘ "
                                            + GRAY
                                            + "Failed to spawn "
                                            + ACCENT
                                            + task.newName()
                                            + CLOSE
                                            + GRAY
                                            + ": "
                                            + reason
                                            + ".");
                            return;
                        }

                        scheduleSnapshotRestore(sender, task);
                    },
                    delay);
        }
    }

    private void scheduleSnapshotRestore(CommandSender sender, RenameTask task) {
        final int[] elapsed = {0};
        final int[] pollTaskId = {-1};

        Runnable poll = () -> {
            elapsed[0] += (int) POLL_INTERVAL_TICKS;

            if (elapsed[0] > POLL_TIMEOUT_TICKS) {
                if (pollTaskId[0] != -1) {
                    FppScheduler.cancelTask(pollTaskId[0]);
                    pollTaskId[0] = -1;
                }
                msg(
                        sender,
                        YELLOW
                                + "  ⚠ "
                                + GRAY
                                + "Timed out waiting for "
                                + ACCENT
                                + task.newName()
                                + CLOSE
                                + GRAY
                                + " to finish spawning. Bot is online but some data "
                                + "may not have been restored.");
                return;
            }

            FakePlayer newFp = manager.getByName(task.newName());
            if (newFp == null) return;

            if (!newFp.isBodyless() && newFp.getPlayer() == null) return;

            if (pollTaskId[0] != -1) {
                FppScheduler.cancelTask(pollTaskId[0]);
                pollTaskId[0] = -1;
            }

            task.snapshot().applyChatAndState(newFp);

            if (task.snapshot().resolvedSkin() != null
                    && task.snapshot().resolvedSkin().isValid()
                    && plugin.getSkinManager() != null
                    && newFp.getPlayer() != null) {
                plugin.getSkinManager()
                        .applySkinFromProfile(newFp, task.snapshot().resolvedSkin());
            }
            if (task.snapshot().ping() >= 0 || task.snapshot().pingUserSet()) {
                if (task.snapshot().pingUserSet()) {
                    manager.applyPing(newFp, task.snapshot().ping());
                } else if (task.snapshot().ping() >= 0) {
                    newFp.setBasePing(task.snapshot().ping());
                    newFp.setPing(task.snapshot().ping());
                }
            }

            if (task.snapshot().aiPersonality() != null) {
                DatabaseManager dbm = plugin.getDatabaseManager();
                if (dbm != null)
                    dbm.updateBotAiPersonality(
                            newFp.getUuid().toString(), task.snapshot().aiPersonality());
            }

            if (newFp.getPlayer() != null) {
                task.snapshot().applyInventoryAndXp(newFp.getPlayer());
            }

            if (task.snapshot().lpGroup() != null) {
                newFp.setLuckpermsGroup(task.snapshot().lpGroup());
            }

            msg(
                    sender,
                    GREEN
                            + "  ✔ "
                            + ACCENT
                            + task.newName()
                            + CLOSE
                            + GRAY
                            + " is online"
                            + GRAY
                            + " <dark_gray>("
                            + RED
                            + task.oldName()
                            + GRAY
                            + " renamed, data restored"
                            + "<dark_gray>)"
                            + GRAY
                            + ".");
        };

        pollTaskId[0] = FppScheduler.runSyncRepeatingWithId(plugin, poll, POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);
    }

    private void doStatus(CommandSender sender) {
        boolean enabled = Config.isBadwordFilterEnabled();
        int words = BadwordFilter.getBadwordCount();
        int wl = BadwordFilter.getWhitelistCount();
        boolean autoRen = Config.isBadwordAutoRenameEnabled();
        boolean global = Config.isBadwordGlobalListEnabled();
        String mode = Config.getBadwordAutoDetectionMode();

        msg(sender, ACCENT + "ʙᴀᴅᴡᴏʀᴅ ꜰɪʟᴛᴇʀ ꜱᴛᴀᴛᴜꜱ" + CLOSE);
        msg(sender, GRAY + "  Filter     : " + (enabled ? GREEN + "✔ enabled" : RED + "✘ disabled"));
        msg(sender, GRAY + "  Global list: " + (global ? GREEN + "✔ on" : RED + "✘ off"));
        msg(sender, GRAY + "  Words      : " + ACCENT + words + CLOSE);
        msg(sender, GRAY + "  Whitelist  : " + ACCENT + wl + CLOSE + GRAY + " entr" + (wl == 1 ? "y" : "ies"));
        msg(
                sender,
                GRAY
                        + "  Auto-rename: "
                        + (autoRen
                                ? GREEN + "✔ on " + GRAY + "(bad names get a random clean name at spawn)"
                                : RED + "✘ off " + GRAY + "(bad names are hard-blocked at spawn)"));
        msg(sender, GRAY + "  Detection  : " + ACCENT + mode + CLOSE);

        if (enabled && words > 0) {
            List<FakePlayer> flagged = findFlaggedBots();
            int total = manager.getActivePlayers().size();
            if (flagged.isEmpty()) {
                msg(sender, GREEN + "  ✔ " + GRAY + "All " + total + " active bot(s) have clean names.");
            } else {
                msg(
                        sender,
                        YELLOW
                                + "  ⚠ "
                                + ACCENT
                                + flagged.size()
                                + CLOSE
                                + GRAY
                                + " of "
                                + total
                                + " active bot(s) have flagged names. Run "
                                + ACCENT
                                + "/fpp badword update"
                                + CLOSE
                                + GRAY
                                + " to fix them.");
            }
        } else if (enabled) {
            msg(
                    sender,
                    YELLOW
                            + "  ⚠ "
                            + GRAY
                            + "No badword sources are active — filter is enabled but has nothing to"
                            + " check.");
        }
    }

    private List<FakePlayer> findFlaggedBots() {
        List<FakePlayer> result = new ArrayList<>();
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (!BadwordFilter.isAllowed(fp.getName())) result.add(fp);
        }
        return result;
    }

    private String findAvailableCleanName(Set<String> reserved) {
        List<String> pool = BotNameConfig.getNames();
        if (pool.isEmpty()) return null;

        Random rand = new Random();
        int attempts = Math.min(30, pool.size() * 3);

        for (int i = 0; i < attempts; i++) {
            String candidate = pool.get(rand.nextInt(pool.size()));
            if (candidate == null || candidate.isBlank()) continue;
            if (candidate.length() > 16) continue;
            if (!candidate.matches("[a-zA-Z0-9_]+")) continue;
            if (!BadwordFilter.isAllowed(candidate)) continue;
            if (reserved.contains(candidate.toLowerCase())) continue;
            if (manager.getByName(candidate) != null) continue;
            if (Bukkit.getPlayerExact(candidate) != null) continue;
            return candidate;
        }
        return null;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (Perm.missing(sender, Perm.BADWORD)) return List.of();
        if (args.length == 1) return filter(List.of("check", "update", "status"), args[0]);
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        msg(sender, ACCENT + "ʙᴀᴅᴡᴏʀᴅ ꜰɪʟᴛᴇʀ" + CLOSE);
        row(sender, "/fpp badword check", "List active bots with flagged names (read-only)");
        row(sender, "/fpp badword update", "Replace flagged bot names — preserves all bot data");
        row(sender, "/fpp badword status", "Show filter config, word count, and scan result");
    }

    private void row(CommandSender sender, String cmd, String desc) {
        msg(sender, GRAY + "  " + ACCENT + cmd + CLOSE + " " + GRAY + "- " + desc);
    }

    private void msg(CommandSender sender, String mm) {
        sender.sendMessage(TextUtil.colorize(mm));
    }

    private static List<String> filter(List<String> options, String partial) {
        String p = partial.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).toList();
    }

    private static final class BotSnapshot {

        private final ItemStack[] mainContents;
        private final ItemStack[] armorContents;
        private final ItemStack[] extraContents;

        private final int xpLevel;
        private final float xpProgress;
        private final int totalXp;

        private final boolean chatEnabled;
        private final String chatTier;

        private final boolean frozen;

        private final String lpGroup;

        private final String aiPersonality;

        private final SkinProfile resolvedSkin;

        private final int ping;
        private final boolean pingUserSet;

        private BotSnapshot(
                ItemStack[] main,
                ItemStack[] armor,
                ItemStack[] extra,
                int xpLevel,
                float xpProgress,
                int totalXp,
                boolean chatEnabled,
                String chatTier,
                boolean frozen,
                String lpGroup,
                String aiPersonality,
                SkinProfile resolvedSkin,
                int ping,
                boolean pingUserSet) {
            this.mainContents = main;
            this.armorContents = armor;
            this.extraContents = extra;
            this.xpLevel = xpLevel;
            this.xpProgress = xpProgress;
            this.totalXp = totalXp;
            this.chatEnabled = chatEnabled;
            this.chatTier = chatTier;
            this.frozen = frozen;
            this.lpGroup = lpGroup;
            this.aiPersonality = aiPersonality;
            this.resolvedSkin = resolvedSkin;
            this.ping = ping;
            this.pingUserSet = pingUserSet;
        }

        static BotSnapshot from(FakePlayer fp) {
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

            if ("default".equalsIgnoreCase(lpg)) lpg = null;

            return new BotSnapshot(
                    main,
                    armor,
                    extra,
                    lvl,
                    prog,
                    tot,
                    fp.isChatEnabled(),
                    fp.getChatTier(),
                    fp.isFrozen(),
                    lpg,
                    fp.getAiPersonality(),
                    fp.getResolvedSkin(),
                    fp.getPing(),
                    fp.hasCustomPing());
        }

        String lpGroup() {
            return lpGroup;
        }

        String aiPersonality() {
            return aiPersonality;
        }

        SkinProfile resolvedSkin() {
            return resolvedSkin;
        }

        int ping() {
            return ping;
        }

        boolean pingUserSet() {
            return pingUserSet;
        }

        void applyInventoryAndXp(Player entity) {
            PlayerInventory inv = entity.getInventory();
            if (mainContents.length > 0) inv.setContents(mainContents);
            if (armorContents.length > 0) inv.setArmorContents(armorContents);
            if (extraContents.length > 0) inv.setExtraContents(extraContents);

            entity.setLevel(xpLevel);
            entity.setExp(Math.max(0f, Math.min(1f, xpProgress)));
            entity.setTotalExperience(totalXp);
        }

        void applyChatAndState(FakePlayer fp) {
            fp.setChatEnabled(chatEnabled);
            if (chatTier != null) fp.setChatTier(chatTier);
            if (frozen) fp.setFrozen(true);

            if (aiPersonality != null) fp.setAiPersonality(aiPersonality);
            if (resolvedSkin != null && resolvedSkin.isValid()) {
                fp.setResolvedSkin(resolvedSkin);
            }
            if (pingUserSet) {
                fp.setUserPing(ping);
            } else if (ping >= 0) {
                fp.setBasePing(ping);
                fp.setPing(ping);
            }
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            if (items == null) return new ItemStack[0];
            ItemStack[] copy = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                copy[i] = items[i] != null ? items[i].clone() : null;
            }
            return copy;
        }
    }

    private record RenameTask(
            String oldName, String newName, Location location, BotType botType, BotSnapshot snapshot) {}
}
