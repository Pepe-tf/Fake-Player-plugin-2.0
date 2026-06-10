package me.bill.fakePlayerPlugin.command;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.BotRenameHelper;
import me.bill.fakePlayerPlugin.util.TextUtil;

public class RenameCommand implements FppCommand {

    private static final String ACCENT = "<#0079FF>";
    private static final String CLOSE = "</#0079FF>";
    private static final String GRAY = "<gray>";
    private static final String RED = "<red>";

    private final FakePlayerManager manager;
    private final BotRenameHelper renameHelper;

    public RenameCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.manager = manager;
        this.renameHelper = new BotRenameHelper(plugin, manager);
    }

    @Override
    public String getName() {
        return "rename";
    }

    @Override
    public String getUsage() {
        return "<oldname> <newname>";
    }

    @Override
    public String getDescription() {
        return "Rename an active bot (preserves all data).";
    }

    @Override
    public String getPermission() {
        return Perm.RENAME;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.hasAny(sender, Perm.RENAME, Perm.RENAME_OWN);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (args.length < 2) {
            msg(sender, GRAY + "Usage: " + ACCENT + "/fpp rename <oldname> <newname>" + CLOSE + GRAY + ".");
            return true;
        }

        String oldName = args[0];
        String newName = args[1];

        FakePlayer fp = manager.getByName(oldName);
        if (fp == null) {
            msg(sender, RED + "No active bot named " + ACCENT + oldName + CLOSE + RED + ".");
            return true;
        }

        if (!Perm.has(sender, Perm.RENAME) && sender instanceof Player player) {
            if (!BotAccess.canAdminister(player, fp)) {
                msg(
                        sender,
                        RED
                                + "You can only rename bots you own or control."
                                + " Ask an admin for "
                                + ACCENT
                                + "fpp.rename"
                                + CLOSE
                                + RED
                                + " to rename any bot.");
                return true;
            }
        }

        renameHelper.rename(sender, fp, newName);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            boolean canRenameAny = Perm.has(sender, Perm.RENAME);

            return manager.getActivePlayers().stream()
                    .filter(fp -> {
                        if (canRenameAny) return true;

                        if (!(sender instanceof Player p)) return false;
                        return BotAccess.canAdminister(p, fp);
                    })
                    .map(FakePlayer::getName)
                    .filter(n -> n.toLowerCase().startsWith(lower))
                    .toList();
        }

        return List.of();
    }

    private void msg(CommandSender sender, String mm) {
        sender.sendMessage(TextUtil.colorize(mm));
    }
}
